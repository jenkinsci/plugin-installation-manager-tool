package io.jenkins.tools.pluginmanager.cli.util;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.jenkins.tools.pluginmanager.cli.Main;
import picocli.CommandLine;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class PIMVersionProvider implements CommandLine.IVersionProvider {

    @Override
    public String[] getVersion() throws Exception {
        return new String[] { readVersionProperty() };
    }

    @SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE")
    public static String readVersionProperty() throws VersionNotFoundException {
        try (InputStream propertyInputStream = getPropertiesInputStream("/.properties")) {
            if (propertyInputStream == null) {
                throw new VersionNotFoundException("No version information available");
            }
            Properties properties = new Properties();
            properties.load(propertyInputStream);

            return properties.getProperty("project.version");
        } catch (IOException e) {
            throw new VersionNotFoundException("No version information available", e);
        }
    }

    /*VisibleForTesting*/
    public static InputStream getPropertiesInputStream(String path) {
        return Main.class.getResourceAsStream(path);
    }
}