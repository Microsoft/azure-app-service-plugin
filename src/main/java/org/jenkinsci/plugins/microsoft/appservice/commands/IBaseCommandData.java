/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */
package org.jenkinsci.plugins.microsoft.appservice.commands;

import hudson.model.AbstractBuild;
import hudson.model.BuildListener;

public interface IBaseCommandData {

    public AbstractBuild<?, ?> getBuild();

    public BuildListener getListener();

    public void logError(String message);

    public void logStatus(String status);

    public void logError(Exception ex);

    public void logError(String prefix, Exception ex);

    public void setDeploymentState(DeploymentState deployState);

    public DeploymentState getDeploymentState();
}
