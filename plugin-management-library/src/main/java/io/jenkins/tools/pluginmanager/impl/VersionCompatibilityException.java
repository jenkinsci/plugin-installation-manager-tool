package io.jenkins.tools.pluginmanager.impl;

public class VersionCompatibilityException extends RuntimeException {

    public VersionCompatibilityException(String message) {
        super(message);
    }

    public VersionCompatibilityException(String message, Throwable cause) {
        super(message, cause);
    }
}
