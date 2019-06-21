package io.jenkins.tools.pluginmanager.cli;

import io.jenkins.tools.pluginmanager.config.Config;
import io.jenkins.tools.pluginmanager.config.Settings;
import io.jenkins.tools.pluginmanager.impl.Plugin;
import io.jenkins.tools.pluginmanager.impl.PluginManager;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

        cfg.setShowWarnings(options.isShowWarnings());
        cfg.setShowAllWarnings(options.isShowAllWarnings());

        String[] pluginsFromCLI = options.getPlugins();
        if (pluginsFromCLI != null) {
            for (String pluginLine : pluginsFromCLI) {
                plugins.add(parsePluginLine(pluginLine));
            }
        }

        if (options.getPluginTxt() == null) {
            System.out.println("No file containing list of plugins to be downloaded entered. " +
                    "Will use default of " + Settings.DEFAULT_PLUGIN_TXT);
            cfg.setPluginTxt(Settings.DEFAULT_PLUGIN_TXT);
        }
        else {
            System.out.println("File containing list of plugins to be downloaded: " + options.getPluginTxt());
            cfg.setPluginTxt(options.getPluginTxt());
        }


        if (options.getPluginDir() == null) {
            System.out.println("No directory to download plugins to entered. " +
                    "Will use default of " + Settings.DEFAULT_PLUGIN_DIR);
            cfg.setPluginDir(Settings.DEFAULT_PLUGIN_DIR);
        }
        else {
            System.out.println("Plugin download location: " + options.getPluginDir());
            cfg.setPluginDir(options.getPluginDir());
        }


        if (options.getJenkinsWar() == null) {
            System.out.println("No war entered. Will use default of " + Settings.DEFAULT_JENKINS_WAR);
            cfg.setJenkinsWar(Settings.DEFAULT_JENKINS_WAR);
        }
        else {
            System.out.println("Will use war file: " + options.getJenkinsWar());
            cfg.setJenkinsWar(options.getJenkinsWar());
        }

        System.out.println("Show all security warnings: " + options.isShowAllWarnings());

        if (Files.exists(cfg.getPluginTxt().toPath())) {
            try {
                Scanner scanner = new Scanner(cfg.getPluginTxt(), StandardCharsets.UTF_8.name());
                System.out.println("Reading in plugins from " + cfg.getPluginTxt().toString() + "\n");
                while (scanner.hasNextLine()) {
                    Plugin plugin = parsePluginLine(scanner.nextLine());
                    plugins.add(plugin);
                }
            } catch (FileNotFoundException e) {
                System.out.println("Unable to open " + cfg.getPluginTxt());
            }
        }
        else {
            System.out.println(cfg.getPluginTxt() + " file does not exist");
        }

        cfg.setPlugins(plugins);
        PluginManager pm = new PluginManager(cfg);
        pm.start();
    }

    public static Plugin parsePluginLine(String pluginLine) {
        String[] pluginInfo = pluginLine.split(":");
        String pluginName = pluginInfo[0];
        String pluginVersion = "latest";
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
