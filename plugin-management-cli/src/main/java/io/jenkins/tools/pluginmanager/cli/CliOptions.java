package io.jenkins.tools.pluginmanager.cli;

import io.jenkins.tools.pluginmanager.config.Settings;
import java.io.File;
import javax.annotation.Nonnull;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.BooleanOptionHandler;
import org.kohsuke.args4j.spi.StringArrayOptionHandler;



public class CliOptions {
    //path must include plugins.txt
    @Option(name = "-pluginTxtPath", usage = "Path to plugins.txt")
    public File pluginTxt;

    @Option(name = "-pluginDirPath", usage = "Directory in which to install plugins")
    public File pluginDir;

    @Option(name = "-plugins", usage = "List of plugins to install, separated by a space", handler = StringArrayOptionHandler.class)
    public String[] plugins;

    @Option(name = "-war", usage = "Path to Jenkins war file")
    public String jenkinsWarFile;

    @Option(name = "-viewSecurityWarnings", usage = "Set to true to show specified plugins that have security warnings", handler = BooleanOptionHandler.class)
    public boolean showWarnings;

    @Option(name = "-viewAllSecurityWarnings", usage = "Set to true to show all plugins that have security warnings", handler = BooleanOptionHandler.class)
    public boolean showAllWarnings;

    @Nonnull
    public File getPluginTxt() {
        return pluginTxt != null ? pluginTxt : Settings.DEFAULT_PLUGIN_TXT;
    }

    @Nonnull
    public File getPluginDir() {
        return pluginDir != null ? pluginDir : Settings.DEFAULT_PLUGIN_DIR;
    }

    @Nonnull
    public String getJenkinsWar() {
        return jenkinsWarFile != null ? jenkinsWarFile : Settings.DEFAULT_JENKINS_WAR;
    }


    public String[] getPlugins() {
        return plugins;
    }

    public boolean hasShowWarnings() {
        return showWarnings;
    }

    public boolean hasShowAllWarnings() {
        return showAllWarnings;
    }
}
