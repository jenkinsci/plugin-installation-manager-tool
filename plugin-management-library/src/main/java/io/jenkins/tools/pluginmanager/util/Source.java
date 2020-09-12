package io.jenkins.tools.pluginmanager.util;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Source {
    private final String version;
    private final String url;

    @JsonCreator
    public Source(@JsonProperty("version") String version, @JsonProperty("url") String url) {
        this.url = url;
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    public String getUrl() {
        return url;
    }
}
