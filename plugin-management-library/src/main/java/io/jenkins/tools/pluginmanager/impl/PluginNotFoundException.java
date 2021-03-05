package io.jenkins.tools.pluginmanager.impl;

public class PluginNotFoundException extends PluginException {

    public PluginNotFoundException(Plugin dependendantPlugin, String message) {
        super(dependendantPlugin, message);
    }

    public PluginNotFoundException(Plugin dependendantPlugin, String message, Throwable cause) {
        super(dependendantPlugin, message, cause);
    }
}
