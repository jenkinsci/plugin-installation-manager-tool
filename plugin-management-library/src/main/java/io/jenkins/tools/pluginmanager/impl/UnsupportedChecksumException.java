package io.jenkins.tools.pluginmanager.impl;

public class UnsupportedChecksumException extends RuntimeException {

    public UnsupportedChecksumException(String message) {
        super(message);
    }

    public UnsupportedChecksumException(String message, Throwable cause) {
        super(message, cause);
    }
}
