package io.jenkins.tools.pluginmanager.config;

import io.jenkins.tools.pluginmanager.impl.Plugin;
import java.io.File;
import java.util.ArrayList;
import java.util.List;


public class Config {
    private File pluginDir;
    private boolean showWarnings;
    private String jenkinsWar;
    private List<Plugin> plugins;

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

    public Config() {
        plugins = new ArrayList<>();
    }
}
