package io.jenkins.tools.pluginmanager.cli.util;

public class VersionNotFoundException extends RuntimeException {

    public VersionNotFoundException(String message) {
        super(message);
    }

    public VersionNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
