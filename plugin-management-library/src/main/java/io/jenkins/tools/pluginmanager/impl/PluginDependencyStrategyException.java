package io.jenkins.tools.pluginmanager.impl;

public class PluginDependencyStrategyException extends RuntimeException {

    public PluginDependencyStrategyException(String message) {
        super(message);
    }

    public PluginDependencyStrategyException(Plugin plugin, String message, Throwable cause) {
        super(message, cause);
    }
}
