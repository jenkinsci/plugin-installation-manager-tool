package io.jenkins.tools.pluginmanager.cli;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.jenkins.tools.pluginmanager.cli.commands.CheckUpdatesCommand;
import io.jenkins.tools.pluginmanager.cli.commands.InstallPluginsCommand;
import io.jenkins.tools.pluginmanager.cli.commands.PreviewUpdateCommand;
import io.jenkins.tools.pluginmanager.cli.commands.VersionCommand;
import io.jenkins.tools.pluginmanager.cli.util.PIMVersionProvider;

import picocli.AutoComplete;
import picocli.CommandLine;

@SuppressFBWarnings("DM_EXIT")
@CommandLine.Command(name = "jenkinsfile-runner",
        versionProvider = PIMVersionProvider.class, sortOptions = false, mixinStandardHelpOptions = true,
        subcommands = {InstallPluginsCommand.class, PreviewUpdateCommand.class, CheckUpdatesCommand.class,
                AutoComplete.GenerateCompletion.class, CommandLine.HelpCommand.class, VersionCommand.class})
public class Main extends InstallPluginsCommand {

    //TODO: Add "--no-download" option to retain the compatibility?

    public static void main(String[] args) throws Throwable {
        // break for attaching profiler
        if (Boolean.getBoolean("start.pause")) {
            System.console().readLine();
        }

        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

}
