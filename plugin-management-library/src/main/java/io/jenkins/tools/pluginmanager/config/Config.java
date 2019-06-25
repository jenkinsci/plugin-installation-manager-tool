package io.jenkins.tools.pluginmanager.config;

import io.jenkins.tools.pluginmanager.impl.Plugin;
import java.io.File;
import java.util.ArrayList;
import java.util.List;


public class Config {
    private File pluginDir;
    private File pluginTxt;
    private boolean showWarnings;
    private String jenkinsWar;
    private List<Plugin> plugins;
    private boolean isOutputVerbose;

    public void setPluginDir(File pluginDir) {
        this.pluginDir = pluginDir;
    }

    public void setPluginTxt(File pluginTxt) {
        this.pluginTxt = pluginTxt;
    }

    public File getPluginDir() {
        return pluginDir;
    }

    public void setShowWarnings(boolean showWarnings) {
        this.showWarnings = showWarnings;
    }

    public boolean isShowWarnings() {
        return showWarnings;
    }

    public void setShowAllWarnings(boolean showWarnings) {
        this.showWarnings = showWarnings;
    }

    public boolean isShowAllWarnings() {
        return showWarnings;
    }

    public File getPluginTxt() {
        return pluginTxt;
    }

    public void setJenkinsWar(String jenkinsWar) {
        this.jenkinsWar = jenkinsWar;
    }

    public String getJenkinsWar() {
        return jenkinsWar;
    }

    public void setPlugins(List<Plugin> plugins) {
        this.plugins = plugins;
    }

    public List<Plugin> getPlugins() {
        return plugins;
    }

    public boolean isOutputVerbose() {
        return isOutputVerbose;
    }

    public void setOutputVerbose(boolean outputVerbose) {
        isOutputVerbose = outputVerbose;
    }

    public Config() {
        plugins = new ArrayList<>();
    }

}
