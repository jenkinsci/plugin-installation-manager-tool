package io.jenkins.tools.pluginmanager.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

public class Plugin {
    private String name;
    private String version;
    private String url;
    private JarFile jarFile;
    private boolean isPluginOptional;
    private List<Plugin> dependencies;
    private List<Plugin> dependents;


    public Plugin(String name, String version, String url) {
        this.name = name;
        this.version = version;
        this.url = url;
        this.dependencies = new ArrayList<>();
        this.dependents = new ArrayList<>();
    }

    public Plugin(String name, String version, boolean isPluginOptional) {
        this.name = name;
        this.version = version;
        this.isPluginOptional = isPluginOptional;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setVersion(String version) {
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

    public String getVersion() {
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

    public void setDependent(Plugin dependent) {
        dependents.add(dependent);
    }

    public List<Plugin> getDependents() {
        return dependents;
    }

}
