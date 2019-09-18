package io.jenkins.tools.pluginmanager.impl;

public class PluginDependencyStrategyException extends RuntimeException {

    public PluginDependencyStrategyException(String message) {
        super(message);
    }

    public PluginDependencyStrategyException(String message, Throwable cause) {
        super(message, cause);
    }
}
