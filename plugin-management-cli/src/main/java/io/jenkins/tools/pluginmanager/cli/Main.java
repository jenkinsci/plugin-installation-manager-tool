package io.jenkins.tools.pluginmanager.cli;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.jenkins.tools.pluginmanager.config.Config;
import io.jenkins.tools.pluginmanager.impl.PluginManager;
import java.io.IOException;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

public class Main {
    @SuppressFBWarnings("DM_EXIT")
    public static void main(String[] args) throws IOException {
        CliOptions options = new CliOptions();
        CmdLineParser parser = new CmdLineParser(options);

        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            parser.printUsage(System.err);
            System.err.println(e.getMessage());
            throw new IOException("Failed to read command-line arguments", e);
        }

        try {
            if (options.isShowVersion()) {
                options.showVersion();
                return;
            }

            Config cfg = options.setup();
            PluginManager pm = new PluginManager(cfg);
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
