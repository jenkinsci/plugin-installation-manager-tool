package io.jenkins.tools.pluginmanager.cli;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

    @Option(name = "-viewSecurityWarnings", usage = "Set to show specified plugins that have security warnings", handler = BooleanOptionHandler.class)
    public boolean showWarnings;

    @Option(name = "-viewAllSecurityWarnings", usage = "Set to show all plugins that have security warnings", handler = BooleanOptionHandler.class)
    public boolean showAllWarnings;

    @Option(name="-verbose", usage = "Set to show detailed information about plugin downloads", handler = BooleanOptionHandler.class)
    public boolean isOutputVerbose;

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

    public boolean isOutputVerbose() {
        return isOutputVerbose;
    }
}
