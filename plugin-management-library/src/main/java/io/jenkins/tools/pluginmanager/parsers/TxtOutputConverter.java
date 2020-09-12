package io.jenkins.tools.pluginmanager.parsers;

import io.jenkins.tools.pluginmanager.impl.Plugin;
import java.util.List;
import java.util.stream.Collectors;

public class TxtOutputConverter implements PluginOutputConverter {

    private static final String SEPARATOR = ":";

    @Override
    public String convert(List<Plugin> plugins) {
        return plugins.stream()
                .map(this::mapPluginToTxtFormat)
                .collect(Collectors.joining(System.lineSeparator()));

    }

    private String mapPluginToTxtFormat(Plugin plugin) {
        StringBuilder builder = new StringBuilder();

        builder.append(plugin.getName());

        if (plugin.getVersion() != null && plugin.getGroupId() == null) {
            builder.append(SEPARATOR)
                    .append(plugin.getVersion());
        }

        if (plugin.getUrl() != null) {
            builder.append(SEPARATOR)
                    .append(plugin.getUrl());
        }

        if (plugin.getGroupId() != null) {
            builder.append(SEPARATOR)
                    .append("incrementals;")
                    .append(plugin.getGroupId())
                    .append(";")
                    .append(plugin.getVersion());
        }
        return builder.toString();
    }
}
