package io.jenkins.tools.pluginmanager.impl;

import java.util.ArrayList;
import java.util.List;

public class SecurityWarning {

    private String id;
    private String message;
    private String name;
    private String type;
    private String url;
    private List<SecurityVersion> securityVersionList;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void addSecurityVersion(String lastVersion, String pattern) {
        securityVersionList.add(new SecurityVersion(message, url));
    }

    public SecurityWarning(String id, String message, String name, String url) {
        this.id = id;
        this.message = message;
        this.name = name;
        this.url = url;
        this.securityVersionList = new ArrayList<>();
    }

    class SecurityVersion {
        String lastVersion;
        String pattern;

        public SecurityVersion(String lastVersion, String pattern) {
            this.lastVersion = lastVersion;
            this.pattern = pattern;
        }

        public String getLastVersion() {
            return lastVersion;
        }

        public String getPattern() {
            return pattern;
        }

        public void setLastVersion(String lastVersion) {
            this.lastVersion = lastVersion;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }
    }
}
