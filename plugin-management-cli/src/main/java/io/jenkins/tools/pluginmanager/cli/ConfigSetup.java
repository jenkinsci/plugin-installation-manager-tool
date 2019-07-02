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
    List<Plugin> plugins;
    Config cfg;
    CliOptions options;

    public ConfigSetup(Config cfg, CliOptions options) {
        plugins = new ArrayList<>();
        this.cfg = cfg;
        this.options = options;
    }

    public void setup() {
        getPlugins();
        getPluginDir();
        getWar();
        getUpdateCenters();
        getWarnings();
    }


    public void getPlugins() {
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

    public void getPluginDir() {
        if (options.getPluginDir() == null) {
            System.out.println("No directory to download plugins entered. " +
                    "Will use default of " + Settings.DEFAULT_PLUGIN_DIR);
            cfg.setPluginDir(Settings.DEFAULT_PLUGIN_DIR);
        } else {
            System.out.println("Plugin download location: " + options.getPluginDir());
            cfg.setPluginDir(options.getPluginDir());
        }
    }


    public void getWar() {
        if (options.getJenkinsWar() == null) {
            System.out.println("No war entered. Will use default of " + Settings.DEFAULT_JENKINS_WAR);
            cfg.setJenkinsWar(Settings.DEFAULT_JENKINS_WAR);
        } else {
            System.out.println("Will use war file: " + options.getJenkinsWar());
            cfg.setJenkinsWar(options.getJenkinsWar());
        }
    }


    public void getUpdateCenters() {
        String jenkinsUc = "";
        if (!StringUtils.isEmpty(options.getJenkinsUc())) {
            jenkinsUc = options.getJenkinsUc();
            System.out.println("Using update center " + jenkinsUc + " specified with CLI option");
        } else if (!StringUtils.isEmpty(System.getenv("JENKINS_UC"))) {
            jenkinsUc = System.getenv("JENKINS_UC");
            System.out.println("Using update center " + jenkinsUc + " from JENKINS_UC environment variable");
        } else {
            jenkinsUc = "https://updates.jenkins.io";
            System.out.println(
                    "No CLI option or environment variable set for update center, using default of " + jenkinsUc);
        }

        cfg.setJenkinsUc(jenkinsUc);

        String jenkinsUcExperimental = "";
        if (!StringUtils.isEmpty(options.getJenkinsUcExperimental())) {
            jenkinsUcExperimental = options.getJenkinsUcExperimental();
            System.out.println(
                    "Using experimental update center " + jenkinsUcExperimental + " specified with CLI option");
        } else if (!StringUtils.isEmpty(System.getenv("JENKINS_UC_EXPERIMENTAL"))) {
            jenkinsUcExperimental = System.getenv("JENKINS_UC_EXPERIMENTAL");
            System.out.println("Using experimental update center " + jenkinsUcExperimental +
                    " from JENKINS_UC_EXPERIMENTAL environemnt variable");
        } else {
            jenkinsUcExperimental = "https://updates.jenkins.io/experimental";
            System.out.println(
                    "No CLI option or environment variable set for experimental update center, using default of " +
                            jenkinsUcExperimental);
        }

        cfg.setJenkinsUcExperimental(jenkinsUcExperimental);

        String jenkinsIncrementalsRepo = "";
        if (!StringUtils.isEmpty(options.getJenkinsIncrementalsRepoMirror())) {
            jenkinsIncrementalsRepo = options.getJenkinsIncrementalsRepoMirror();
            System.out.println("Using incrementals mirror " + jenkinsIncrementalsRepo + " specified with CLI option");
        } else if (!StringUtils.isEmpty(System.getenv("JENKINS_INCREMENTALS_REPO_MIRROR"))) {
            jenkinsIncrementalsRepo = System.getenv("JENKINS_INCREMENTALS_REPO_MIRROR");
            System.out.println("Using incrementals mirror " + jenkinsIncrementalsRepo +
                    " from JENKINS_INCREMENTALS_REPO_MIRROR environment variable");
        } else {
            jenkinsIncrementalsRepo = "https://repo.jenkins-ci.org/incrementals";
            System.out.println("No CLI option or environment variable set for incrementals mirror, using default of " +
                    jenkinsIncrementalsRepo);
        }
        cfg.setJenkinsIncrementalsRepoMirror(jenkinsIncrementalsRepo);
    }

    public void getWarnings() {
        cfg.setShowWarnings(options.isShowWarnings());
        cfg.setShowAllWarnings(options.isShowAllWarnings());
        System.out.println("Show all security warnings: " + options.isShowAllWarnings());
    }

}
