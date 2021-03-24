package io.jenkins.tools.pluginmanager.impl;

public class PluginChecksumMismatchException extends PluginException {
    private final String expectedChecksum;
    private final String actualChecksum;

    public PluginChecksumMismatchException(Plugin plugin, String expectedChecksum, String actualChecksum) {
        super(plugin, String.format("invalid checksum, expected: %s, actual: %s", expectedChecksum, actualChecksum));
        this.expectedChecksum = expectedChecksum;
        this.actualChecksum = actualChecksum;
    }

    public String getExpectedChecksum() {
        return expectedChecksum;
    }

    public String getActualChecksum() {
        return actualChecksum;
    }
}
