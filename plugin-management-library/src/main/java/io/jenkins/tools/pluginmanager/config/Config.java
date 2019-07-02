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
    private String jenkinsUc;
    private String jenkinsUcExperimental;
    private String jenkinsIncrementalsRepoMirror;


    public void setPluginDir(File pluginDir) {
        this.pluginDir = pluginDir;
    }

    public void setPluginTxt(File pluginTxt) {
        this.pluginTxt =pluginTxt;
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

    public void setJenkinsUc(String jenkinsUc) {
        this.jenkinsUc = jenkinsUc;
    }

    public void setJenkinsUcExperimental(String jenkinsUcExperimental) {
        this.jenkinsUcExperimental = jenkinsUcExperimental;
    }

    public void setJenkinsIncrementalsRepoMirror(String jenkinsIncrementalsRepoMirror) {
        this.jenkinsIncrementalsRepoMirror = jenkinsIncrementalsRepoMirror;
    }

    public String getJenkinsUc() {
        return jenkinsUc;
    }

    public String getJenkinsUcExperimental() {
        return jenkinsUcExperimental;
    }

    public String getJenkinsIncrementalsRepoMirror() {
        return jenkinsIncrementalsRepoMirror;
    }

    public Config() {
        plugins = new ArrayList<>();
    }
}
