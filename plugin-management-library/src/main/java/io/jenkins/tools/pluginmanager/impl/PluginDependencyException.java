package io.jenkins.tools.pluginmanager.impl;

public class PluginDependencyException extends PluginException {

    public PluginDependencyException(Plugin plugin, String messageSuffix) {
        super(plugin, messageSuffix);
    }

    public PluginDependencyException(Plugin plugin, String messageSuffix, Throwable cause) {
        super(plugin, messageSuffix, cause);
    }
}
