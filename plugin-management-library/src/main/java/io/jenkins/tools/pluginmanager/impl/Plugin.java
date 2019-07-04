package io.jenkins.tools.pluginmanager.impl;

import hudson.util.VersionNumber;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

public class Plugin {
    private String name;
    private VersionNumber version;
    private String url;
    private JarFile jarFile;
    private boolean isPluginOptional;
    private List<Plugin> dependencies;
    private Plugin parent;


    public Plugin(String name, String version, String url) {
        this.name = name;
        this.version = new VersionNumber(version);
        this.url = url;
        this.dependencies = new ArrayList<>();
        this.parent = this;
    }

    public Plugin(String name, String version, boolean isPluginOptional) {
        this.name = name;
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

    public void setJarFile(JarFile jarFile) {
        this.jarFile = jarFile;
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

    public JarFile getJarFile() {
        return jarFile;
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
        return getName() + " : " + getVersion();
    }

}
