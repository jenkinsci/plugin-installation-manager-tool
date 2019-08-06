package io.jenkins.tools.pluginmanager.impl;

import hudson.util.VersionNumber;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

public class Plugin implements Comparable<Plugin> {
    private String name;
    private String originalName;
    private VersionNumber version;
    private String groupId;
    private String url;
    private File file;
    private boolean isPluginOptional;
    private List<Plugin> dependencies;
    private Plugin parent;
    private List<SecurityWarning> securityWarnings;
    private boolean latest;
    private boolean experimental;

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

    public Plugin(String name, String version, boolean isPluginOptional) {
        this.name = name;
        this.originalName = name;
        if (StringUtils.isEmpty(version)) {
            version = "latest";
        }
        this.version = new VersionNumber(version);

        this.isPluginOptional = isPluginOptional;
        this.dependencies = new ArrayList<>();
        this.parent = this;
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

    public void setPluginOptional(boolean isPluginOptional) {
        this.isPluginOptional = isPluginOptional;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
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

    public String getGroupId() {
        return groupId;
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
    public int compareTo(Plugin p) {
        if (this.equals(p)) {
            return 0;
        } else if (this.getName().equals(p.getName())) {
            return this.getVersion().compareTo(p.getVersion());
        } else {
            return this.getName().compareTo(p.getName());
        }
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
