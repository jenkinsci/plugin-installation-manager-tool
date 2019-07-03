package io.jenkins.tools.pluginmanager.cli;

import io.jenkins.tools.pluginmanager.config.Settings;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.BooleanOptionHandler;
import org.kohsuke.args4j.spi.FileOptionHandler;
import org.kohsuke.args4j.spi.StringArrayOptionHandler;
import org.kohsuke.args4j.spi.URLOptionHandler;


public class CliOptions {
    //path must include plugins.txt
    @Option(name = "--pluginTxtPath", usage = "Path to plugins.txt", handler = FileOptionHandler.class)
    public File pluginTxt;

    @Option(name = "--pluginDirPath", usage = "Directory in which to install plugins",
            handler = FileOptionHandler.class)
    public File pluginDir;

    @Option(name = "--plugins", usage = "List of plugins to install, separated by a space",
            handler = StringArrayOptionHandler.class)
    public String[] plugins = new String[0];

    @Option(name = "--war", usage = "Path to Jenkins war file")
    public String jenkinsWarFile;

    @Option(name = "--viewSecurityWarnings",
            usage = "Set to true to show specified plugins that have security warnings",
            handler = BooleanOptionHandler.class)
    public boolean showWarnings;

    @Option(name = "--viewAllSecurityWarnings", usage = "Set to true to show all plugins that have security warnings",
            handler = BooleanOptionHandler.class)
    public boolean showAllWarnings;

    @Option(name = "--jenkins-update-center",
            usage = "Sets main update center; will override JENKINS_UC environment variable. If not set via CLI " +
                    "option or environment variable, will default to " + Settings.DEFAULT_JENKINS_UC,
            handler = URLOptionHandler.class)
    public URL jenkinsUc;

    @Option(name = "--jenkins-experimental-update-center",
            usage = "Sets experimental update center; will override JENKINS_UC_EXPERIMENTAL environment variable. If " +
                    "not set via CLI option or environment variable, will default to " +
                    Settings.DEFAULT_JENKINS_UC_EXPERIMENTAL,
            handler = URLOptionHandler.class)
    public URL jenkinsUcExperimental;

    @Option(name = "--jenkins-incrementals-repo-mirror",
            usage = "Set Maven mirror to be used to download plugins from the Incrementals repository, will override " +
                    "the JENKINS_INCREMENTALS_REPO_MIRROR environment variable. If not set via CLI option or " +
                    "environment variable, will default to " + Settings.DEFAULT_JENKINS_INCREMENTALS_REPO_MIRROR,
            handler = URLOptionHandler.class)
    public URL jenkinsIncrementalsRepoMirror;


    public File getPluginTxt() {
        return pluginTxt;
    }

    public File getPluginDir() {
        return pluginDir;
    }

    public String getJenkinsWar() {
        return jenkinsWarFile;
    }

    public List<String> getPlugins() {
        if (plugins != null) {
            return new ArrayList<>(Arrays.asList(plugins));
        }
        return new ArrayList<>();
    }

    public boolean isShowWarnings() {
        return showWarnings;
    }

    public boolean isShowAllWarnings() {
        return showAllWarnings;
    }

    public String getJenkinsUc() {
        return jenkinsUc == null ? "" : jenkinsUc.toString();
    }

    public String getJenkinsUcExperimental() {
        return jenkinsUcExperimental == null ? "" : jenkinsUcExperimental.toString();
    }

    public String getJenkinsIncrementalsRepoMirror() {
        return jenkinsIncrementalsRepoMirror == null ? "" : jenkinsIncrementalsRepoMirror.toString();
    }

}
