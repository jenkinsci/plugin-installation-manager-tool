package io.jenkins.tools.pluginmanager.config;

import java.net.MalformedURLException;
import java.net.URL;

public class Settings {
    public static final String DEFAULT_PLUGIN_DIR_LOCATION;
    public static final String DEFAULT_WAR;
    public static final URL DEFAULT_UPDATE_CENTER;
    public static final String DEFAULT_UPDATE_CENTER_FILENAME = "/update-center.json";
    public static final String DEFAULT_UPDATE_CENTER_LOCATION = "https://updates.jenkins.io" + DEFAULT_UPDATE_CENTER_FILENAME;
    public static final URL DEFAULT_EXPERIMENTAL_UPDATE_CENTER;
    public static final String DEFAULT_EXPERIMENTAL_UPDATE_CENTER_LOCATION = "https://updates.jenkins.io/experimental" + DEFAULT_UPDATE_CENTER_FILENAME;
    public static final URL DEFAULT_INCREMENTALS_REPO_MIRROR;
    public static final String DEFAULT_INCREMENTALS_REPO_MIRROR_LOCATION = "https://repo.jenkins-ci.org/incrementals";
    public static final String DEFAULT_PLUGIN_INFO_LOCATION = "https://updates.jenkins.io/current/plugin-versions.json";

    static {
        try {
            DEFAULT_UPDATE_CENTER = new URL(DEFAULT_UPDATE_CENTER_LOCATION);
            DEFAULT_EXPERIMENTAL_UPDATE_CENTER = new URL(DEFAULT_EXPERIMENTAL_UPDATE_CENTER_LOCATION);
            DEFAULT_INCREMENTALS_REPO_MIRROR = new URL(DEFAULT_INCREMENTALS_REPO_MIRROR_LOCATION);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            DEFAULT_WAR = "C:\\ProgramData\\Jenkins\\jenkins.war";
            DEFAULT_PLUGIN_DIR_LOCATION = "C:\\ProgramData\\Jenkins\\Reference\\Plugins";
        } else {
            DEFAULT_WAR = "/usr/share/jenkins/jenkins.war";
            DEFAULT_PLUGIN_DIR_LOCATION = "/usr/share/jenkins/ref/plugins";
        }
    }
}
