package io.jenkins.tools.pluginmanager.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

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

    public void addSecurityVersion(String firstVersion, String lastVersion, String pattern) {
        securityVersionList.add(new SecurityVersion(firstVersion, lastVersion, pattern));
    }

    public List<SecurityVersion> getSecurityVersions() {
        return securityVersionList;
    }

    public SecurityWarning(String id, String message, String name, String url) {
        this.id = id;
        this.message = message;
        this.name = name;
        this.url = url;
        this.securityVersionList = new ArrayList<>();
    }

    static class SecurityVersion {
        String firstVersion;
        String lastVersion;
        Pattern pattern;

        public SecurityVersion(String firstVersion, String lastVersion, String patternString) {
            this.firstVersion = firstVersion;
            this.lastVersion = lastVersion;
            pattern = Pattern.compile(patternString);
        }

        public String getLastVersion() {
            return lastVersion;
        }

        public String getFirstVersion() {
            return firstVersion;
        }

        public Pattern getPattern() {
            return pattern;
        }

        public void setLastVersion(String lastVersion) {
            this.lastVersion = lastVersion;
        }

        public void setPattern(Pattern pattern) {
            this.pattern = pattern;
        }

        public void setFirstVersion(String firstVersion) {
            this.firstVersion = firstVersion;
        }
    }
}
