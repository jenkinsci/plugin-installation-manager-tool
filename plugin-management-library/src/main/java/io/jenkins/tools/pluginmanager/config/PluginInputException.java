package io.jenkins.tools.pluginmanager.config;

public class PluginInputException extends RuntimeException {

    public PluginInputException(String message) {
        super(message);
    }

    public PluginInputException(String message, Throwable cause) {
        super(message, cause);
    }
}
