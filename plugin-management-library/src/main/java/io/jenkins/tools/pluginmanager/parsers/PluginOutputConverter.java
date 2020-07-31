package io.jenkins.tools.pluginmanager.parsers;

import io.jenkins.tools.pluginmanager.impl.Plugin;
import java.util.List;

public interface PluginOutputConverter {

    String convert(List<Plugin> plugins);
}
