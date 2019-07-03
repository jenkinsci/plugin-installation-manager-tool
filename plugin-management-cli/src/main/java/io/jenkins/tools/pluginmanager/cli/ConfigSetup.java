package io.jenkins.tools.pluginmanager.cli;

import io.jenkins.tools.pluginmanager.config.Config;
import io.jenkins.tools.pluginmanager.config.Settings;
import io.jenkins.tools.pluginmanager.impl.Plugin;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import org.apache.commons.lang3.StringUtils;


public class ConfigSetup {
    private List<Plugin> plugins;
    private CliOptions options;

    public ConfigSetup(CliOptions options) {
        plugins = new ArrayList<>();
        this.options = options;
    }

    /**
     * Creates a configuration class with configurations specified from the CLI and/or environment variables.
     *
     * @return a configuration class that can be passed to the PluginManager class
     */
    public Config setup() {
        Config cfg = new Config();
        getPlugins(cfg);
        getPluginDir(cfg);
        getWar(cfg);
        getUpdateCenters(cfg);
        getWarnings(cfg);
        return cfg;
    }

    /**
     * Creates a list plugins specified in the CLI option and/or the plugins.txt file and adds them to the configuration
     * class
     *
     * @param cfg the configuration class
     */
    public void getPlugins(Config cfg) {
        List<String> pluginsFromCLI = options.getPlugins();
        for (String pluginLine : pluginsFromCLI) {
            plugins.add(parsePluginLine(pluginLine));
        }

        if (options.getPluginTxt() == null) {
            System.out.println("No file containing list of plugins to be downloaded entered. " +
                    "Will use default of " + Settings.DEFAULT_PLUGIN_TXT);
            cfg.setPluginTxt(Settings.DEFAULT_PLUGIN_TXT);
        } else {
            System.out.println("File containing list of plugins to be downloaded: " + options.getPluginTxt());
            cfg.setPluginTxt(options.getPluginTxt());
        }

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
        } else {
            System.out.println(cfg.getPluginTxt() + " file does not exist");
        }

        cfg.setPlugins(plugins);
    }

    /**
     * For each plugin specified in the CLI using the --plugins option or line in the plugins.txt file, creates a Plugin
     * object containing the  plugin name (required), version (optional), and url (optional)
     *
     * @param pluginLine plugin information to parse
     * @return plugin object containing name, version, and/or url
     */
    public Plugin parsePluginLine(String pluginLine) {
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

    /**
     * Gets the user specified plugin download directory from the CLI option and sets this in the configuration class
     *
     * @param cfg the configuration class, which
     */
    public void getPluginDir(Config cfg) {
        if (options.getPluginDir() == null) {
            System.out.println("No directory to download plugins entered. " +
                    "Will use default of " + Settings.DEFAULT_PLUGIN_DIR);
            cfg.setPluginDir(Settings.DEFAULT_PLUGIN_DIR);
        } else {
            System.out.println("Plugin download location: " + options.getPluginDir());
            cfg.setPluginDir(options.getPluginDir());
        }
    }

    /**
     * Gets the user specified Jenkins war from the CLI option and sets this in the configuration class
     *
     * @param cfg the configuration class
     */
    public void getWar(Config cfg) {
        if (options.getJenkinsWar() == null) {
            System.out.println("No war entered. Will use default of " + Settings.DEFAULT_JENKINS_WAR);
            cfg.setJenkinsWar(Settings.DEFAULT_JENKINS_WAR);
        } else {
            System.out.println("Will use war file: " + options.getJenkinsWar());
            cfg.setJenkinsWar(options.getJenkinsWar());
        }
    }

    /**
     * Gets the update center, experimental update center, and incrementals repository mirror specified in the CLI
     * options or environment variable and sets this in the configuration class
     *
     * @param cfg the configuration class
     */
    public void getUpdateCenters(Config cfg) {
        String jenkinsUc = getUpdateCenter();
        cfg.setJenkinsUc(jenkinsUc);

        String jenkinsUcExperimental = getExperimentalUpdateCenter();
        cfg.setJenkinsUcExperimental(jenkinsUcExperimental);

        String jenkinsIncrementalsRepo = getIncrementalsMirror();
        cfg.setJenkinsIncrementalsRepoMirror(jenkinsIncrementalsRepo);
    }

    /**
     * Determines the update center url string. If a value is set via CLI option, it will override a value set via
     * environment variable. If neither are set, the default in the Settings class will be used.
     *
     * @return the update center url string
     */
    public String getUpdateCenter() {
        String jenkinsUc;
        if (!StringUtils.isEmpty(options.getJenkinsUc())) {
            jenkinsUc = options.getJenkinsUc();
            System.out.println("Using update center " + jenkinsUc + " specified with CLI option");
        } else if (!StringUtils.isEmpty(System.getenv("JENKINS_UC"))) {
            jenkinsUc = System.getenv("JENKINS_UC");
            System.out.println("Using update center " + jenkinsUc + " from JENKINS_UC environment variable");
        } else {
            jenkinsUc = Settings.DEFAULT_JENKINS_UC;
            System.out.println("No CLI option or environment variable set for update center, using default of " +
                    jenkinsUc);
        }
        return jenkinsUc;
    }

    /**
     * Determines the experimental update center url string. If a value is set via CLI option, it will override a value
     * set via environment variable. If neither are set, the default in the Settings class will be used.
     *
     * @return the experimental update center url string
     */
    public String getExperimentalUpdateCenter() {
        String jenkinsUcExperimental;
        if (!StringUtils.isEmpty(options.getJenkinsUcExperimental())) {
            jenkinsUcExperimental = options.getJenkinsUcExperimental();
            System.out.println(
                    "Using experimental update center " + jenkinsUcExperimental + " specified with CLI option");
        } else if (!StringUtils.isEmpty(System.getenv("JENKINS_UC_EXPERIMENTAL"))) {
            jenkinsUcExperimental = System.getenv("JENKINS_UC_EXPERIMENTAL");
            System.out.println("Using experimental update center " + jenkinsUcExperimental +
                    " from JENKINS_UC_EXPERIMENTAL environemnt variable");
        } else {
            jenkinsUcExperimental = Settings.DEFAULT_JENKINS_UC_EXPERIMENTAL;
            System.out.println(
                    "No CLI option or environment variable set for experimental update center, using default of " +
                            jenkinsUcExperimental);
        }
        return jenkinsUcExperimental;
    }

    /**
     * Determines the incrementals repository mirror url string. If a value is set via CLI option, it will override a
     * value set via environment variable. If neither are set, the default in the Settings class will be used.
     *
     * @return the incrementals repository mirror url string
     */
    public String getIncrementalsMirror() {
        String jenkinsIncrementalsRepo;
        if (!StringUtils.isEmpty(options.getJenkinsIncrementalsRepoMirror())) {
            jenkinsIncrementalsRepo = options.getJenkinsIncrementalsRepoMirror();
            System.out.println("Using incrementals mirror " + jenkinsIncrementalsRepo + " specified with CLI option");
        } else if (!StringUtils.isEmpty(System.getenv("JENKINS_INCREMENTALS_REPO_MIRROR"))) {
            jenkinsIncrementalsRepo = System.getenv("JENKINS_INCREMENTALS_REPO_MIRROR");
            System.out.println("Using incrementals mirror " + jenkinsIncrementalsRepo +
                    " from JENKINS_INCREMENTALS_REPO_MIRROR environment variable");
        } else {
            jenkinsIncrementalsRepo = Settings.DEFAULT_JENKINS_INCREMENTALS_REPO_MIRROR;
            System.out.println("No CLI option or environment variable set for incrementals mirror, using default of " +
                    jenkinsIncrementalsRepo);
        }
        return jenkinsIncrementalsRepo;
    }

    /**
     * Gets the value to show all security warnings from the CLI options and sets this value in the configuration class
     *
     * @param cfg the configuration class
     */
    public void getWarnings(Config cfg) {
        cfg.setShowWarnings(options.isShowWarnings());
        cfg.setShowAllWarnings(options.isShowAllWarnings());
        System.out.println("Show all security warnings: " + options.isShowAllWarnings());
    }
}
