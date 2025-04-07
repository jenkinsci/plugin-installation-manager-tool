package io.jenkins.tools.pluginmanager.cli;

import io.jenkins.tools.pluginmanager.config.Config;
import io.jenkins.tools.pluginmanager.impl.Plugin;
import io.jenkins.tools.pluginmanager.impl.PluginManager;
import io.jenkins.tools.pluginmanager.parsers.AvailableUpdatesStdOutPluginOutputConverter;
import java.io.IOException;
import java.util.List;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.ParserProperties;

public class Main {
    public static void main(String[] args) throws IOException {
        CliOptions options = new CliOptions();
        ParserProperties parserProperties = ParserProperties.defaults().withUsageWidth(150);
        CmdLineParser parser = new CmdLineParser(options, parserProperties);

        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            parser.printUsage(System.err);
            System.err.println(e.getMessage());
            throw new IOException("Failed to read command-line arguments", e);
        }

        if (args.length == 0) {
            parser.printUsage(System.out);
            return;
        }

        try {
            if (options.isShowVersion()) {
                options.showVersion();
                return;
            }

            if (options.isShowHelp()) {
                parser.printUsage(System.out);
                return;
            }

            Config cfg = options.setup();
            try (PluginManager pm = new PluginManager(cfg)) {
                if (options.isShowAvailableUpdates()) {
                    pm.getUCJson(pm.getJenkinsVersion());
                    List<Plugin> latestVersionsOfPlugins = pm.getLatestVersionsOfPlugins(cfg.getPlugins());
                    pm.outputPluginList(latestVersionsOfPlugins, () -> new AvailableUpdatesStdOutPluginOutputConverter(cfg.getPlugins()));
                } else {
                    pm.start();
                }
            }
        } catch (Exception e) {
            if (options.isVerbose()) {
                e.printStackTrace();
            }
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
