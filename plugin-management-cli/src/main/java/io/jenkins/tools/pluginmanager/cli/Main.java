package io.jenkins.tools.pluginmanager.cli;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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
    @SuppressFBWarnings("DM_EXIT")
/*
    The @SuppressFBWarnings("DM_EXIT") annotation is added here to suppress FindBugs warnings concerning the direct use of System.exit().The value
    "DM_EXIT" corresponds to the specific FindBugs detector for direct exit methods, indicating that this suppression is targeted at that particular
    category of warnings. The justification for this suppression is that the usage of System.exit() in this code is intentional and serves a specific
    purpose. Placing the justification and value directly in the annotation adheres to the common approach with SuppressFBWarnings, providing explicit
    documentation about the deliberate decision to use System.exit() in the code.
 */

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
