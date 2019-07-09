package io.jenkins.tools.pluginmanager.config;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

public class Settings {

    public static final File DEFAULT_PLUGIN_TXT = new File(System.getProperty("user.dir") + File.separator + "plugins.txt");
    public static final File DEFAULT_PLUGIN_DIR = new File("/usr/share/jenkins/ref/plugins");
    public static final String DEFAULT_WAR;
    public static final URL DEFAULT_UPDATE_CENTER;
    public static final String DEFAULT_UPDATE_CENTER_LOCATION = "https://updates.jenkins.io";
    public static final URL DEFAULT_EXPERIMENTAL_UPDATE_CENTER;
    public static final String DEFAULT_EXPERIMENTAL_UPDATE_CENTER_LOCATION = "https://updates.jenkins.io/experimental";
    public static final URL DEFAULT_INCREMENTALS_REPO_MIRROR;
    public static final String DEFAULT_INCREMENTALS_REPO_MIRROR_LOCATION = "https://repo.jenkins-ci.org/incrementals";

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
        }
        else {
            DEFAULT_WAR = "/usr/share/jenkins/jenkins.war";
        }

    }
}
