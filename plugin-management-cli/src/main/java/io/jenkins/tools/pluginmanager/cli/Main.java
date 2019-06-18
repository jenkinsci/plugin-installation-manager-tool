package io.jenkins.tools.pluginmanager.cli;

import io.jenkins.tools.pluginmanager.config.Config;
import io.jenkins.tools.pluginmanager.impl.Plugin;
import io.jenkins.tools.pluginmanager.impl.PluginManager;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;


public class Main {
    public static void main(String[] args) throws IOException {
        List<Plugin> plugins = new ArrayList<>();

        CliOptions options = new CliOptions();
        CmdLineParser parser = new CmdLineParser(options);

        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            parser.printUsage(System.out);
            throw new IOException("Failed to read command-line arguments", e);
        }

        Config cfg = new Config();

        cfg.setPluginDir(options.getPluginDir());
        cfg.setShowWarnings(options.hasShowWarnings());
        cfg.setShowAllWarnings(options.hasShowAllWarnings());
        cfg.setJenkinsWar(options.getJenkinsWar());

        String[] pluginsFromCLI = options.getPlugins();
        if (pluginsFromCLI != null) {
            for (String pluginLine : pluginsFromCLI) {
                plugins.add(parsePluginLine(pluginLine));
            }
        }

        System.out.println("show warnings" + options.hasShowAllWarnings());

        try {
            Scanner scanner = new Scanner(options.getPluginTxt());
            System.out.println("Reading in plugins from " + options.getPluginTxt().toString());
            while (scanner.hasNextLine()) {
                Plugin plugin = parsePluginLine(scanner.nextLine());
                plugins.add(plugin);
            }
        } catch (
                FileNotFoundException e) {
            e.printStackTrace();
        }

        cfg.setPlugins(plugins);
        PluginManager pm = new PluginManager(cfg);
        pm.start();
    }

    public static Plugin parsePluginLine(String pluginLine) {
        String[] pluginInfo = pluginLine.split(":");
        String pluginName = pluginInfo[0];
        String pluginVersion = null;
        String pluginUrl = null;
        if (pluginInfo.length >= 2) {
            pluginVersion = pluginInfo[1];
        }
        if (pluginInfo.length == 3) {
            pluginUrl = pluginInfo[2];
        }

        return new Plugin(pluginName, pluginVersion, pluginUrl);

    }

}
