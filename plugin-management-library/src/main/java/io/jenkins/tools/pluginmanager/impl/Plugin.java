package io.jenkins.tools.pluginmanager.impl;

import hudson.util.VersionNumber;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

public class Plugin {
    private String name;
    private String originalName;
    private VersionNumber version;
    private String groupId;
    private String url;
    private File file;
    private List<Plugin> dependencies;
    //TODO(oleg_nenashev): better to use nullable API
    private boolean dependenciesSpecified;
    private Plugin parent;
    private List<SecurityWarning> securityWarnings;
    private boolean latest;
    private boolean experimental;
    private VersionNumber jenkinsVersion;

    public Plugin(String name, String version, String url, String groupId) {
        this.originalName = name;
        this.name = name;
        if (StringUtils.isEmpty(version)) {
            version = "latest";
        }
        this.version = new VersionNumber(version);
        this.url = url;
        this.dependencies = new ArrayList<>();
        this.parent = this;
        this.groupId = groupId;
        this.securityWarnings = new ArrayList<>();
        if (version.equals("latest")) {
            latest = true;
        }
        if (version.equals("experimental")) {
            experimental = true;
        }
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

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public void setJenkinsVersion(String jenkinsVersion) {
        this.jenkinsVersion = new VersionNumber(jenkinsVersion);
    }

    public void setLatest(boolean latest) {
        this.latest = latest;
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

    public File getFile() {
        return file;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setDependencies(List<Plugin> dependencies) {
        this.dependencies = dependencies;
        this.dependenciesSpecified = true;
    }

    public Plugin withDependencies(List<Plugin> dependencies) {
        this.setDependencies(dependencies);
        return this;
    }

    public Plugin withoutDependencies() {
        this.setDependencies(Collections.emptyList());
        return this;
    }

    public List<Plugin> getDependencies() {
        return dependencies;
    }

    public boolean isDependenciesSpecified() {
        return dependenciesSpecified;
    }

    public void setParent(Plugin parent) {
        this.parent = parent;
    }

    public Plugin getParent() {
        return parent;
    }

    public VersionNumber getJenkinsVersion() {
        return jenkinsVersion;
    }

    public void setSecurityWarnings(List<SecurityWarning> securityWarnings) {
        this.securityWarnings = securityWarnings;
    }

    public List<SecurityWarning> getSecurityWarnings() {
        return securityWarnings;
    }

    public boolean isLatest() {
        return latest;
    }

    public boolean isExperimental() {
        return experimental;
    }

    @Override
    public String toString() {
        if (url == null) {
            return name + " " + version;
        }
        return name + " " + version + " " + url;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Plugin plugin = (Plugin) o;
        return Objects.equals(name, plugin.name) &&
                Objects.equals(version, plugin.version) &&
                Objects.equals(groupId, plugin.groupId) &&
                Objects.equals(url, plugin.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, version, groupId, url);
    }
}
