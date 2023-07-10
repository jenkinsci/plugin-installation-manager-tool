package io.jenkins.tools.pluginmanager.parsers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.jenkins.tools.pluginmanager.impl.Plugin;
import io.jenkins.tools.pluginmanager.util.PluginInfo;
import io.jenkins.tools.pluginmanager.util.Plugins;
import io.jenkins.tools.pluginmanager.util.Source;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;

public class YamlPluginOutputConverter implements PluginOutputConverter {
    @Override
    public String convert(List<Plugin> plugins) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER))
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);

        try {
            return mapper.writeValueAsString(mapToOutputFormat(plugins));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return "Error converting to yaml";
        }
    }

    private Plugins mapToOutputFormat(List<Plugin> plugins) {
        List<PluginInfo> convertedPlugins = plugins.stream()
                .sorted(comparing(Plugin::getName).thenComparing(Plugin::getVersion))
                .map(this::toPluginHolder)
                .collect(Collectors.toList());

        return new Plugins(convertedPlugins);
    }

    private PluginInfo toPluginHolder(Plugin plugin) {
        return new PluginInfo(plugin.getName(), plugin.getGroupId(), new Source(plugin.getVersion().toString(), plugin.getUrl()));
    }
}
