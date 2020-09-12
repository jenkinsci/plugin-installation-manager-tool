package io.jenkins.tools.pluginmanager.util;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class Plugins {
    private final List<PluginInfo> plugins;

    @JsonCreator
    public Plugins(@JsonProperty("plugins") List<PluginInfo> plugins) {
        this.plugins = plugins;
    }

    public List<PluginInfo> getPlugins() {
        return plugins;
    }
}
