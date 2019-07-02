package io.jenkins.tools.pluginmanager.config;

import java.io.File;

public class Settings {

    public static final File DEFAULT_PLUGIN_TXT = new File(System.getProperty("user.dir") + File.separator + "plugins.txt");
    public static final File DEFAULT_PLUGIN_DIR = new File(System.getProperty("user.dir") + File.separator + "plugins");
    public static final String DEFAULT_JENKINS_WAR = "/usr/share/jenkins/jenkins.war";
    public static final String DEFAULT_JENKINS_UC = "https://updates.jenkins.io";
    public static final String DEFAULT_JENKINS_UC_EXPERIMENTAL = "https://updates.jenkins.io/experimental";
    public static final String DEFAULT_JENKINS_INCREMENTALS_REPO_MIRROR ="https://repo.jenkins-ci.org/incrementals";

}
