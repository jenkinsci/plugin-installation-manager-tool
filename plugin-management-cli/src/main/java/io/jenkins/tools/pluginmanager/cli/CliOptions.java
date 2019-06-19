package io.jenkins.tools.pluginmanager.cli;

import java.io.File;
import java.util.Arrays;
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

    public File getPluginTxt() {
        return pluginTxt;
    }

    public File getPluginDir() {
        return pluginDir;
    }

    public String getJenkinsWar() {
        return jenkinsWarFile;
    }


    public String[] getPlugins() {
        return Arrays.copyOf(plugins, plugins.length);
    }

    public boolean isShowWarnings() {
        return showWarnings;
    }

    public boolean isShowAllWarnings() {
        return showAllWarnings;
    }
}
