package io.jenkins.tools.pluginmanager.impl;

public class PluginDependencyException extends PluginException {

    public PluginDependencyException(Plugin plugin, String message) {
        super(plugin, message);
    }

    public PluginDependencyException(Plugin plugin, String message, Throwable cause) {
        super(plugin, message, cause);
    }
}
