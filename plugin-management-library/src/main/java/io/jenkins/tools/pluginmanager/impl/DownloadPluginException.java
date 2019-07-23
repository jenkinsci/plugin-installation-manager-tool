package io.jenkins.tools.pluginmanager.impl;

public class DownloadPluginException extends RuntimeException {

    public DownloadPluginException(String message) {
        super(message);
    }

    public DownloadPluginException(String message, Throwable cause) {
        super(message, cause);
    }
}
