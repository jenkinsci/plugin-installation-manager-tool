package io.jenkins.tools.pluginmanager.cli.commands;

import io.jenkins.tools.pluginmanager.cli.util.PIMVersionProvider;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;

@Command(name = "version", description = "Shows Plugin Installation Manager version", mixinStandardHelpOptions = true)
public class VersionCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        System.out.println(PIMVersionProvider.readVersionProperty());
        return 0;
    }
}
