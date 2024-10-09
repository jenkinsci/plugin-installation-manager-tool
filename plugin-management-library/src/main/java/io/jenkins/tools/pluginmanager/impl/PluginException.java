package io.jenkins.tools.pluginmanager.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PluginException extends RuntimeException {

    private final Plugin plugin;

    public PluginException(Plugin plugin, String messageSuffix, Throwable cause) {
        super(messageSuffix, cause);
        this.plugin = plugin;
    }

    public PluginException(Plugin plugin, String messageSuffix) {
        this(plugin, messageSuffix, null);
    }

    public String getOriginatorPluginAndDependencyChain() {
        List<Plugin> dependencyChain = new ArrayList<>();
        Plugin currentPlugin = plugin;
        dependencyChain.add(currentPlugin);
        int depth = 0;
        while (currentPlugin.getParent() != null && currentPlugin.getParent() != currentPlugin) {
            currentPlugin = currentPlugin.getParent();
            // insert in reverse order
            dependencyChain.add(0, currentPlugin);
            if (depth++ > 20) {
                System.err.println("Probably found a dependency cycle in " + plugin);
                break;
            }
        }
        String chainInfo = dependencyChain.stream().skip(1).map(p -> p.getName() + ":" + p.getVersion()).collect(Collectors.joining("->"));
        if (!chainInfo.isEmpty()) {
            chainInfo = " (via " + chainInfo + ")";
        }
        Plugin originator = dependencyChain.get(0);
        return String.format("Plugin %s:%s%s ", originator.getName(), originator.getVersion(), chainInfo);
    }

    @Override
    public String getMessage() {
        return getOriginatorPluginAndDependencyChain() + super.getMessage();
    }
}
