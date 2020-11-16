package io.jenkins.tools.pluginmanager.cli;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.jenkins.tools.pluginmanager.config.Config;
import io.jenkins.tools.pluginmanager.config.OutputFormat;
import io.jenkins.tools.pluginmanager.impl.Plugin;
import io.jenkins.tools.pluginmanager.impl.PluginManager;
import io.jenkins.tools.pluginmanager.parsers.StdOutPluginOutputConverter;
import io.jenkins.tools.pluginmanager.parsers.TxtOutputConverter;
import io.jenkins.tools.pluginmanager.parsers.YamlPluginOutputConverter;
import java.io.IOException;
import java.util.List;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.ParserProperties;

public class Main {
    @SuppressFBWarnings("DM_EXIT")
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
            PluginManager pm = new PluginManager(cfg);

            if (options.isShowAvailableUpdates()) {
                pm.getUCJson(pm.getJenkinsVersionFromWar());
                List<Plugin> latestVersionsOfPlugins = pm.getLatestVersionsOfPlugins(cfg.getPlugins());
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
                        output = new StdOutPluginOutputConverter(cfg.getPlugins()).convert(latestVersionsOfPlugins);
                }
                System.out.println(output);
                return;
            }

            if (cfg.isShowPluginsToBeDownloaded()) {
                System.out.println("The --list flag is currently unsafe and is temporarily disabled, " +
                    "see https://github.com/jenkinsci/plugin-installation-manager-tool/issues/173");
                return;
            }

            pm.start();
        } catch (Exception e) {
            if (options.isVerbose()) {
                e.printStackTrace();
            }
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
