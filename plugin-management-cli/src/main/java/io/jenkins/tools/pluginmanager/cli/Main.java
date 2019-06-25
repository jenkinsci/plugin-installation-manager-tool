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
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;


public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

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

        cfg.setOutputVerbose(options.isOutputVerbose());
        cfg.setShowWarnings(options.isShowWarnings());
        cfg.setShowAllWarnings(options.isShowAllWarnings());

        String[] pluginsFromCLI = options.getPlugins();
        if (pluginsFromCLI != null) {
            for (String pluginLine : pluginsFromCLI) {
                plugins.add(parsePluginLine(pluginLine));
            }
        }

        StringBuilder optionInfo = new StringBuilder();

        if (options.getPluginTxt() == null) {
            optionInfo.append("No file containing list of plugins to be downloaded entered. " +
                    "Will use default of " + Settings.DEFAULT_PLUGIN_TXT + "\n");
            cfg.setPluginTxt(Settings.DEFAULT_PLUGIN_TXT);
        }
        else {
            optionInfo.append("File containing list of plugins to be downloaded: " + options.getPluginTxt() + "\n");
            cfg.setPluginTxt(options.getPluginTxt());
        }


        if (options.getPluginDir() == null) {
            optionInfo.append("No directory to download plugins to entered. " +
                    "Will use default of " + Settings.DEFAULT_PLUGIN_DIR + "\n");
            cfg.setPluginDir(Settings.DEFAULT_PLUGIN_DIR);
        }
        else {
            optionInfo.append("Plugin download location: " + options.getPluginDir() + "\n");
            cfg.setPluginDir(options.getPluginDir());
        }


        if (options.getJenkinsWar() == null) {
            optionInfo.append("No war entered. Will use default of " + Settings.DEFAULT_JENKINS_WAR + "\n");
            cfg.setJenkinsWar(Settings.DEFAULT_JENKINS_WAR);
        }
        else {
            optionInfo.append("Will use war file: " + options.getJenkinsWar() + "\n");
            cfg.setJenkinsWar(options.getJenkinsWar());
        }

        optionInfo.append("Show all security warnings: " + options.isShowAllWarnings());

        LOGGER.log(Level.INFO, optionInfo.toString());

        if (Files.exists(cfg.getPluginTxt().toPath())) {
            try {
                Scanner scanner = new Scanner(cfg.getPluginTxt(), StandardCharsets.UTF_8.name());
                LOGGER.log(Level.INFO, "Reading in plugins from {0}", cfg.getPluginTxt().toString());
                while (scanner.hasNextLine()) {
                    Plugin plugin = parsePluginLine(scanner.nextLine());
                    plugins.add(plugin);
                }
            } catch (FileNotFoundException e) {
                LOGGER.log(Level.WARNING, "Unable to open {0}", cfg.getPluginTxt());
            }
        }
        else {
            LOGGER.log(Level.WARNING, "{0} file does not exist", cfg.getPluginTxt());
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
