package io.jenkins.tools.pluginmanager.cli.commands;

import io.jenkins.tools.pluginmanager.config.Config;
import io.jenkins.tools.pluginmanager.impl.PluginManager;
import picocli.CommandLine;

import java.util.concurrent.Callable;

/**
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
}
