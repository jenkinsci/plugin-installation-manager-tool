package io.jenkins.tools.pluginmanager.parsers;

import hudson.util.VersionNumber;
import io.jenkins.tools.pluginmanager.impl.Plugin;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AvailableUpdatesStdOutPluginOutputConverter implements PluginOutputConverter {

    private final List<Plugin> originalPlugins;

    public AvailableUpdatesStdOutPluginOutputConverter(List<Plugin> originalPlugins) {
        this.originalPlugins = originalPlugins;
    }

    @Override
    public String convert(List<Plugin> plugins) {

        Map<String, Plugin> pluginsAsMap = plugins.stream()
                .collect(Collectors.toMap(Plugin::getName, plugin -> plugin));

        StringBuilder builder = new StringBuilder("Available updates:\n");
        for (Plugin plugin : originalPlugins) {
            VersionNumber originalVersion = plugin.getVersion();
            VersionNumber newVersion = pluginsAsMap.get(plugin.getName()).getVersion();
            if (originalVersion.isOlderThan(newVersion)) {
                builder.append(String.format("%s (%s) has an available update: %s%n", plugin.getName(),
                        plugin.getVersion(), newVersion));
            }
        }

        String result = builder.toString();
        if (!result.contains("has an")) {
            return "No available updates\n";
        }
        return result;
    }
}
