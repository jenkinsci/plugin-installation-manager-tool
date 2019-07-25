package io.jenkins.tools.pluginmanager.cli;

public class PluginInputFormatException extends RuntimeException {

    public PluginInputFormatException(String message) {
        super(message);
    }

    public PluginInputFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
