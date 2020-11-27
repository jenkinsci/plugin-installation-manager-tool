package io.jenkins.tools.pluginmanager.impl;

public class PluginChecksumMismatchException extends RuntimeException {
    private final String pluginName;
    private final String expectedChecksum;
    private final String actualChecksum;

    public PluginChecksumMismatchException(Plugin plugin, String expectedChecksum, String actualChecksum) {
        this.pluginName = plugin.getName();
        this.expectedChecksum = expectedChecksum;
        this.actualChecksum = actualChecksum;
    }

    @Override
    public String getMessage() {
        return String.format("Invalid checksum for %s plugin expected: %s, actual: %s", pluginName, expectedChecksum, actualChecksum);
    }

    public String getPluginName() {
        return pluginName;
    }

    public String getExpectedChecksum() {
        return expectedChecksum;
    }

    public String getActualChecksum() {
        return actualChecksum;
    }
}
