/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */
package org.jenkinsci.plugins.microsoft.appservice.commands;

import java.io.*;

import com.microsoft.azure.management.appservice.PublishingProfile;
import hudson.FilePath;
import hudson.Util;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.FileSet;

public class FTPDeployCommand implements ICommand<FTPDeployCommand.IFTPDeployCommandData> {

    private static final String SITE_ROOT = "/site/wwwroot/";

    // Java specific
    private static final String TOMCAT_ROOT_WAR = SITE_ROOT + "webapps/ROOT.war";
    private static final String TOMCAT_ROOT_DIR = SITE_ROOT + "webapps/ROOT";

    public void execute(IFTPDeployCommandData context) {
        final FilePath workspace = context.getBuild().getWorkspace();
        final PublishingProfile pubProfile = context.getPublishingProfile();
        FTPClient ftpClient = new FTPClient();
        try {
            String ftpUrl = pubProfile.ftpUrl();
            String userName = pubProfile.ftpUsername();
            String password = pubProfile.ftpPassword();

            if (ftpUrl.startsWith("ftp://")) {
                ftpUrl = ftpUrl.substring("ftp://".length());
            }

            if (ftpUrl.indexOf("/") > 0) {
                int splitIndex = ftpUrl.indexOf("/");
                ftpUrl = ftpUrl.substring(0, splitIndex);
            }

            context.logStatus(String.format("Starting to deploy to FTP: %s", ftpUrl));

            ftpClient.connect(ftpUrl);
            ftpClient.login(userName, password);

            final String targetDirectory = SITE_ROOT + Util.fixNull(context.getTargetDirectory());
            ftpClient.makeDirectory(targetDirectory);
            ftpClient.changeWorkingDirectory(targetDirectory);
            context.logStatus(String.format("Working directory: %s", ftpClient.printWorkingDirectory()));

            final File sourceDir = new File(workspace.getRemote(), Util.fixNull(context.getSourceDirectory()));
            FileSet fs = Util.createFileSet(sourceDir, context.getFilePath());
            DirectoryScanner ds = fs.getDirectoryScanner();
            String[] files = ds.getIncludedFiles();
            for (String file: files) {
                uploadFile(context, ftpClient, new File(sourceDir, file), file);
            }
        } catch (IOException e) {
            e.printStackTrace();
            context.logError("Fail to deploy to FTP: "  + e.getMessage());
            context.setDeploymentState(DeploymentState.HasError);
        } finally {
            if (ftpClient.isConnected()) {
                try {
                    ftpClient.disconnect();
                } catch (IOException e) {
                    e.printStackTrace();
                    context.logStatus("Fail to disconnect from FTP: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Remove FTP directory recursively
     * @param context Command context
     * @param ftpClient FTP client
     * @param dir Directory to remove
     * @throws IOException
     */
    private void removeFtpDirectory(IFTPDeployCommandData context, FTPClient ftpClient, String dir) throws IOException {
        context.logStatus("Removing remote directory: " + dir);

        FTPFile[] subFiles = ftpClient.listFiles(dir);
        if (subFiles.length > 0) {
            for (FTPFile ftpFile : subFiles) {
                String fileName = ftpFile.getName();
                if (fileName.equals(".") || fileName.equals("..")) {
                    // Skip
                    continue;
                }

                String fullFileName = dir + "/" + fileName;
                if (ftpFile.isDirectory()) {
                    // Remove sub directory recursively
                    removeFtpDirectory(context, ftpClient, fullFileName);
                } else {
                    // Delete regular file
                    context.logStatus("Removing remote file: " + fullFileName);

                    ftpClient.deleteFile(fullFileName);
                }
            }
        }

        ftpClient.removeDirectory(dir);
    }

    private void uploadFile(IFTPDeployCommandData context, FTPClient ftpClient, File file, String remoteName) throws IOException {
        context.logStatus(String.format("Uploading %s", remoteName));

        // Need some preparation in some cases
        prepareDirectory(context, ftpClient, remoteName);

        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
        try (InputStream stream = new FileInputStream(file)) {
            ftpClient.storeFile(remoteName, stream);
        }
    }

    private void prepareDirectory(IFTPDeployCommandData context, FTPClient ftpClient, String fileName) throws IOException {
        // Deployment to tomcat root requires removing root directory first
        final String targetFilePath = FilenameUtils.concat(ftpClient.printWorkingDirectory(), fileName);
        if (targetFilePath.equalsIgnoreCase(FilenameUtils.separatorsToSystem(TOMCAT_ROOT_WAR))) {
            removeFtpDirectory(context, ftpClient, TOMCAT_ROOT_DIR);
        }
    }

    public interface IFTPDeployCommandData extends IBaseCommandData {

        public PublishingProfile getPublishingProfile();

        public String getFilePath();

        public String getSourceDirectory();

        public String getTargetDirectory();
    }
}
