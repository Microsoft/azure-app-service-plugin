/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */
package com.microsoft.jenkins.appservice.integration;

import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.appservice.*;
import com.microsoft.azure.management.resources.ResourceGroup;
import com.microsoft.jenkins.appservice.util.AzureUtils;
import com.microsoft.jenkins.azurecommons.JobContext;
import com.microsoft.jenkins.azurecommons.core.AzureClientFactory;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.StreamBuildListener;
import com.microsoft.jenkins.appservice.commands.FTPDeployCommand;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.logging.Logger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ITFTPDeployCommand extends IntegrationTest {

    private static final Logger LOGGER = Logger.getLogger(ITFTPDeployCommand.class.getName());
    private FTPDeployCommand command = null;
    private FTPDeployCommand.IFTPDeployCommandData commandDataMock = null;
    private WebApp webApp = null;
    private FilePath workspace = null;

    @Override
    @Before
    public void setUp() {
        super.setUp();
        command = new FTPDeployCommand();
        commandDataMock = mock(FTPDeployCommand.IFTPDeployCommandData.class);
        JobContext jobContextMock = mock(JobContext.class);
        when(commandDataMock.getJobContext()).thenReturn(jobContextMock);
        StreamBuildListener listener = new StreamBuildListener(System.out, Charset.defaultCharset());
        when(commandDataMock.getJobContext().getTaskListener()).thenReturn(listener);
        setUpBaseCommandMockErrorHandling(commandDataMock);

        Azure azureClient = AzureClientFactory.getClient(
                servicePrincipal.getClientId(),
                servicePrincipal.getClientSecret(),
                servicePrincipal.getTenant(),
                servicePrincipal.getSubscriptionId(),
                servicePrincipal.getAzureEnvironment());

        // Setup web app
        final ResourceGroup resourceGroup = azureClient.resourceGroups()
                .define(testEnv.azureResourceGroup)
                .withRegion(testEnv.azureLocation)
                .create();
        Assert.assertNotNull(resourceGroup);

        final AppServicePlan asp = azureClient.appServices().appServicePlans()
                .define(testEnv.appServicePlanName)
                .withRegion(testEnv.azureLocation)
                .withNewResourceGroup(testEnv.azureResourceGroup)
                .withPricingTier(testEnv.appServicePricingTier)
                .withOperatingSystem(OperatingSystem.WINDOWS)
                .create();
        Assert.assertNotNull(asp);

        webApp = azureClient.appServices().webApps()
                .define(testEnv.appServiceName)
                .withExistingWindowsPlan(asp)
                .withExistingResourceGroup(testEnv.azureResourceGroup)
                .withJavaVersion(JavaVersion.JAVA_8_NEWEST)
                .withWebContainer(WebContainer.TOMCAT_8_0_NEWEST)
                .create();
        Assert.assertNotNull(webApp);
        when(commandDataMock.getWebAppBase()).thenReturn(webApp);

        final PublishingProfile pubProfile = webApp.getPublishingProfile();
        when(commandDataMock.getPublishingProfile()).thenReturn(pubProfile);

        File workspaceDir = com.google.common.io.Files.createTempDir();
        workspaceDir.deleteOnExit();
        workspace = new FilePath(workspaceDir);

        final Run run = mock(Run.class);
        when(commandDataMock.getJobContext().getRun()).thenReturn(run);
        when(commandDataMock.getJobContext().getWorkspace()).thenReturn(workspace);
    }

    /**
     * This test uploads a war file to a non-root directory and verifies web page content
     * @throws IOException
     * @throws InterruptedException
     */
    @Test
    public void uploadNonRoot() throws IOException, InterruptedException {
        Utils.extractResourceFile(getClass(), "sample-java-app/app.war", workspace.child("webapps/sample.war").getRemote());
        when(commandDataMock.getFilePath()).thenReturn("webapps/sample.war");

        command.execute(commandDataMock);

        Utils.waitForAppReady(new URL("https://" + webApp.defaultHostName() + "/sample/"),"Sample \"Hello, World\" Application", 300);
    }

    /**
     * This test uploads a war file to a root directory and verifies web page content
     * @throws IOException
     * @throws InterruptedException
     */
    @Test
    public void uploadRoot() throws IOException, InterruptedException {
        Utils.extractResourceFile(getClass(), "sample-java-app/app.war", workspace.child("webapps/ROOT.war").getRemote());
        when(commandDataMock.getFilePath()).thenReturn("webapps/ROOT.war");

        command.execute(commandDataMock);

        Utils.waitForAppReady(new URL("https://" + webApp.defaultHostName()), "Sample \"Hello, World\" Application", 300);
    }

    @Test
    public void zipDeploy() throws IOException, InterruptedException {
        Utils.extractResourceFile(getClass(), "sample-java-app-zip/gs-spring-boot-0.1.0.zip", workspace.child("gs-spring-boot-0.1.0.zip").getRemote());
        when(commandDataMock.getFilePath()).thenReturn("*.zip");

        command.execute(commandDataMock);

        Utils.waitForAppReady(new URL("https://" + webApp.defaultHostName()), "Greetings from Spring Boot!", 300);
    }

    /**
     * This test uploads a standalone application which does not use the default tomcat
     * @throws IOException
     * @throws InterruptedException
     */
    @Test
    public void uploadStandalone() throws IOException, InterruptedException {
        Utils.extractResourceFile(getClass(), "sample-java-app-spring-boot/gs-spring-boot-0.1.0.jar",
            workspace.child("gs-spring-boot-0.1.0.jar").getRemote());
        Utils.extractResourceFile(getClass(), "sample-java-app-spring-boot/web.config",
            workspace.child("web.config").getRemote());
        when(commandDataMock.getFilePath()).thenReturn("*.jar,web.config");

        command.execute(commandDataMock);

        Utils.waitForAppReady(new URL("https://" + webApp.defaultHostName()), "Greetings from Spring Boot!", 300);
    }

    /**
     * This test uploads a war file to a root directory and verifies web page content, with source and target directories specified
     * @throws IOException
     * @throws InterruptedException
     */
    @Test
    public void uploadWithSourceTargetDirectory() throws IOException, InterruptedException {
        Utils.extractResourceFile(getClass(), "sample-java-app/app.war", workspace.child("target/ROOT.war").getRemote());
        when(commandDataMock.getSourceDirectory()).thenReturn("target");
        when(commandDataMock.getTargetDirectory()).thenReturn("webapps");
        when(commandDataMock.getFilePath()).thenReturn("ROOT.war");

        command.execute(commandDataMock);

        Utils.waitForAppReady(new URL("https://" + webApp.defaultHostName()), "Sample \"Hello, World\" Application", 300);
    }

}
