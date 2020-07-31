package io.jenkins.tools.pluginmanager.util;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class PluginInfo {
    private final String artifactId;
    private final String groupId;
    private final Source source;

    @JsonCreator
    public PluginInfo(
            @JsonProperty("artifactId") String artifactId,
            @JsonProperty("groupId") String groupId,
            @JsonProperty("source") Source source
    ) {
        this.artifactId = artifactId;
        this.groupId = groupId;
        this.source = source;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getGroupId() {
        return groupId;
    }

    public Source getSource() {
        return source;
    }

}
