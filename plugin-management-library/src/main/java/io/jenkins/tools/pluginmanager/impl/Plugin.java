package io.jenkins.tools.pluginmanager.impl;

import hudson.util.VersionNumber;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Plugin {
    private String name;
    private String originalName;
    private VersionNumber version;
    private String url;
    private File file;
    private boolean isPluginOptional;
    private List<Plugin> dependencies;
    private Plugin parent;


    public Plugin(String name, String version, String url) {
        this.name = name;
        this.originalName = name;
        this.version = new VersionNumber(version);
        this.url = url;
        this.dependencies = new ArrayList<>();
        this.parent = this;
    }

    public Plugin(String name, String version, boolean isPluginOptional) {
        this.name = name;
        this.originalName = name;
        this.version = new VersionNumber(version);
        this.isPluginOptional = isPluginOptional;
        this.dependencies = new ArrayList<>();
        this.parent = this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setVersion(VersionNumber version) {
        this.version = version;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public void setPluginOptional(boolean isPluginOptional) {
        this.isPluginOptional = isPluginOptional;
    }

    public String getName() {
        return name;
    }

    public VersionNumber getVersion() {
        return version;
    }

    public String getUrl() {
        return url;
    }

    public String getArchiveFileName() {
        return name + ".jpi";
    }

    public boolean getPluginOptional() {
        return isPluginOptional;
    }

    public File getFile() {
        return file;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setDependencies(List<Plugin> dependencies) {
        this.dependencies = dependencies;
    }

    public List<Plugin> getDependencies() {
        return dependencies;
    }

    public void setParent(Plugin parent) {
        this.parent = parent;
    }

    public Plugin getParent() {
        return parent;
    }

    @Override
    public String toString() {
        if (url == null) {
            return name + " " + version;
        }
        return name + " " + version + " " + url;
    }

}
