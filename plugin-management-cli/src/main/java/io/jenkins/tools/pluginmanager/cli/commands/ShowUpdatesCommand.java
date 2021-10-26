package io.jenkins.tools.pluginmanager.cli.commands;

import io.jenkins.tools.pluginmanager.config.Config;
import io.jenkins.tools.pluginmanager.config.OutputFormat;
import io.jenkins.tools.pluginmanager.impl.Plugin;
import io.jenkins.tools.pluginmanager.impl.PluginManager;
import io.jenkins.tools.pluginmanager.parsers.StdOutPluginOutputConverter;
import io.jenkins.tools.pluginmanager.parsers.TxtOutputConverter;
import io.jenkins.tools.pluginmanager.parsers.YamlPluginOutputConverter;
import picocli.CommandLine;

import java.util.List;

/**
 * @since 3.0
 */
@CommandLine.Command(
        name = "show-updates",
        description = "Install plugins",
        mixinStandardHelpOptions = true)
public class ShowUpdatesCommand extends AbstractPluginManagerCommand {

    @Override
    public Integer call(PluginManager pm, Config config) throws Exception {
        pm.getUCJson(pm.getJenkinsVersion());
        List<Plugin> latestVersionsOfPlugins = pm.getLatestVersionsOfPlugins(config.getPlugins());
        OutputFormat outputFormat = options.getOutputFormat() == null ? OutputFormat.STDOUT : options.getOutputFormat();
        String output;
        switch (outputFormat) {
            case YAML:
                output = new YamlPluginOutputConverter().convert(latestVersionsOfPlugins);
                break;
            case TXT:
                output = new TxtOutputConverter().convert(latestVersionsOfPlugins);
                break;
            default:
                output = new StdOutPluginOutputConverter(config.getPlugins()).convert(latestVersionsOfPlugins);
        }
        System.out.println(output);
        return 0;
    }

}
