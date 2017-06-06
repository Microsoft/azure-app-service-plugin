/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */
package org.jenkinsci.plugins.microsoft.appservice.commands;

import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.appservice.PublishingProfile;
import com.microsoft.azure.management.appservice.WebApp;
import com.microsoft.azure.util.AzureCredentials;
import org.jenkinsci.plugins.microsoft.appservice.util.TokenCache;

public class GetPublishSettingsCommand implements ICommand<GetPublishSettingsCommand.IGetPublishSettingsCommandData> {

    @Override
    public void execute(GetPublishSettingsCommand.IGetPublishSettingsCommandData context) {
        try {
            context.logStatus("Retrieving publish settings");
            String resourceGroupName = context.getResourceGroupName();
            String name = context.getAppServiceName();

            final Azure azureClient = TokenCache.getInstance(context.getAzureServicePrincipal()).getAzureClient();
            final WebApp app = azureClient.webApps().getByGroup(resourceGroupName, name);
            if (app == null) {
                context.logError(String.format("App %s in resource group %s not found", name, resourceGroupName));
                context.setDeploymentState(DeploymentState.HasError);
                return;
            }

            final PublishingProfile pubProfile = app.getPublishingProfile();
            context.setPublishingProfile(pubProfile);

            context.setDeploymentState(DeploymentState.Success);
            context.logStatus("Successfully retrieved publish settings");

        } catch (Exception e) {
            e.printStackTrace();
            context.setDeploymentState(DeploymentState.HasError);
            context.logError("Error retrieving publish settings: " + e.getMessage());
        }
    }

    public interface IGetPublishSettingsCommandData extends IBaseCommandData {

        public String getResourceGroupName();

        public String getAppServiceName();

        public void setPublishingProfile(PublishingProfile profile);

        public AzureCredentials.ServicePrincipal getAzureServicePrincipal();
    }
}
