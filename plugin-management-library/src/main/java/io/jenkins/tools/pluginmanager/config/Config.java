package io.jenkins.tools.pluginmanager.config;

import java.io.File;

public class Config {
    private File pluginTxt;
    private File pluginDir;
    private boolean showWarnings;

    public void setPluginTxt(File pluginTxt) {
        this.pluginTxt = pluginTxt;
    }

    public File getPluginTxt() {
        return pluginTxt;
    }

    public void setPluginDir(File pluginDir) {
        this.pluginDir = pluginDir;
    }

    public File getPluginDir() {
        return pluginDir;
    }

    public void setShowWarnings(boolean showWarnings) {
        this.showWarnings = showWarnings;
    }

    public boolean hasShowWarnings() {
        return showWarnings;
    }

    public void setShowAllWarnings(boolean showWarnings) {
        this.showWarnings = showWarnings;
    }

    public boolean hasShowAllWarnings() {
        return showWarnings;
    }
}
