package io.jenkins.tools.pluginmanager.impl;

public class WarBundledPluginException extends RuntimeException {

    public WarBundledPluginException(String message) {
        super(message);
    }

    public WarBundledPluginException(String message, Throwable cause) {
        super(message, cause);
    }
}
