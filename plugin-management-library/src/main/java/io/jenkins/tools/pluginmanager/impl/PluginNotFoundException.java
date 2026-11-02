package io.jenkins.tools.pluginmanager.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PluginNotFoundException extends PluginException {

    public PluginNotFoundException(Plugin dependendantPlugin, String message) {
        super(dependendantPlugin, message);
    }

    public PluginNotFoundException(Plugin dependendantPlugin, String message, Throwable cause) {
        super(dependendantPlugin, message, cause);
    }

    @Override
    public String getMessage() {
        Plugin plugin = getPlugin();
        if (plugin != null) {
            List<Plugin> dependencyChain = new ArrayList<>();
            Plugin currentPlugin = plugin;
            dependencyChain.add(currentPlugin);
            int depth = 0;
            while (currentPlugin.getParent() != null && currentPlugin.getParent() != currentPlugin) {
                currentPlugin = currentPlugin.getParent();
                dependencyChain.add(0, currentPlugin);
                if (depth++ > 20) {
                    break;
                }
            }
            if (dependencyChain.size() == 2) {
                Plugin originator = dependencyChain.get(0);
                Plugin missingPlugin = dependencyChain.get(1);
                return String.format("Plugin %s:%s depends on %s:%s, which %s",
                        originator.getName(), originator.getVersion(),
                        missingPlugin.getName(), missingPlugin.getVersion(),
                        getMessageSuffix());
            } else if (dependencyChain.size() > 2) {
                Plugin originator = dependencyChain.get(0);
                Plugin missingPlugin = dependencyChain.get(dependencyChain.size() - 1);
                String intermediates = dependencyChain.stream()
                        .skip(1)
                        .limit(dependencyChain.size() - 2)
                        .map(p -> p.getName() + ":" + p.getVersion())
                        .collect(Collectors.joining("->"));
                return String.format("Plugin %s:%s (via %s) depends on %s:%s, which %s",
                        originator.getName(), originator.getVersion(),
                        intermediates,
                        missingPlugin.getName(), missingPlugin.getVersion(),
                        getMessageSuffix());
            }
        }
        return super.getMessage();
    }
}
