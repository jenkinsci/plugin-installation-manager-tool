package io.jenkins.tools.pluginmanager.impl;

public class PluginNotFoundException extends RuntimeException {

    public PluginNotFoundException(String message) {
        super(message);
    }

    public PluginNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
