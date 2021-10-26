package io.jenkins.tools.pluginmanager.cli;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.jenkins.tools.pluginmanager.cli.commands.AbstractPluginManagerCommand;
import io.jenkins.tools.pluginmanager.cli.commands.ShowUpdatesCommand;
import io.jenkins.tools.pluginmanager.cli.commands.InstallPluginsCommand;
import io.jenkins.tools.pluginmanager.cli.commands.PreviewUpdatesCommand;
import io.jenkins.tools.pluginmanager.cli.commands.VersionCommand;
import io.jenkins.tools.pluginmanager.cli.util.PIMVersionProvider;

import io.jenkins.tools.pluginmanager.config.Config;
import io.jenkins.tools.pluginmanager.impl.PluginManager;
import picocli.AutoComplete;
import picocli.CommandLine;

@SuppressFBWarnings("DM_EXIT")
@CommandLine.Command(
        versionProvider = PIMVersionProvider.class, sortOptions = false, mixinStandardHelpOptions = true,
        subcommands = {InstallPluginsCommand.class, PreviewUpdatesCommand.class, ShowUpdatesCommand.class,
                AutoComplete.GenerateCompletion.class, CommandLine.HelpCommand.class, VersionCommand.class})
public class Main extends AbstractPluginManagerCommand {

    //TODO: Add "--no-download" option to retain the compatibility?

    public static void main(String[] args) throws Throwable {

        // break for attaching profiler
        if (Boolean.getBoolean("start.pause")) {
            System.console().readLine();
        }

        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call(PluginManager pm, Config config) throws Exception {
        // Same as Install Plugins Command
        pm.start();
        return 0;
    }

    @Override
    public Config.Configurator getConfigurator() {
        // Same as Install Plugins Command
        return (builder) -> {builder.withDoDownload(true);};
    }

}
