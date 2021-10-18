package io.jenkins.tools.pluginmanager.cli.commands;

import io.jenkins.tools.pluginmanager.config.Config;
import io.jenkins.tools.pluginmanager.impl.PluginManager;
import picocli.CommandLine;

/**
 * Installs plugins.
 * This is the original default of Plugin Installation Manager before version 3.0.
 * @since 3.0
 */
@CommandLine.Command(
        name = "install-plugins",
        description = "Install plugins",
        mixinStandardHelpOptions = true)
public class InstallPluginsCommand extends AbstractPluginManagerCommand {

    @Override
    public Integer call(PluginManager pm, Config config) throws Exception {
        pm.start();
        return 0;
    }

    @Override
    public Config.Configurator getConfigurator() {
        return (builder) -> {builder.withDoDownload(true);};
    }
}
