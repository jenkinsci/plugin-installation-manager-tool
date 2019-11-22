package io.jenkins.tools.pluginmanager.cli;

import io.jenkins.tools.pluginmanager.config.Config;
import io.jenkins.tools.pluginmanager.config.Settings;
import io.jenkins.tools.pluginmanager.impl.Plugin;
import io.jenkins.tools.pluginmanager.impl.PluginDependencyStrategyException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.BooleanOptionHandler;
import org.kohsuke.args4j.spi.FileOptionHandler;
import org.kohsuke.args4j.spi.StringArrayOptionHandler;
import org.kohsuke.args4j.spi.URLOptionHandler;

class CliOptions {
    //path must include plugins.txt
    @Option(name = "--plugin-file", aliases = {"-f"}, usage = "Path to plugins.txt or plugins.yaml file",
            handler = FileOptionHandler.class)
    private File pluginFile;

    @Option(name = "--plugin-download-directory", aliases = {"-d"},
            usage = "Path to directory in which to install plugins; will override PLUGIN_DIR environment variable.",
            handler = FileOptionHandler.class)
    private File pluginDir;

    @Option(name = "--plugins", aliases = {"-p"}, usage = "List of plugins to install, separated by a space",
            handler = StringArrayOptionHandler.class)
    private String[] plugins = new String[0];

    @Option(name = "--war", aliases = {"-w"}, usage = "Path to Jenkins war file")
    private String jenkinsWarFile;

    @Option(name = "--list", aliases = {"-l"}, usage = "Lists all plugins currently installed and if given a list of " +
            "plugins to install either via file or CLI option, all plugins that will be installed by the tool",
            handler = BooleanOptionHandler.class)
    private boolean showPluginsToBeDownloaded;

    @Option(name = "--verbose", usage = "Verbose logging",
            handler = BooleanOptionHandler.class)
    private boolean verbose;

    @Option(name = "--available-updates", usage = "Show available plugin updates for the requested plugins",
            handler = BooleanOptionHandler.class)
    private boolean showAvailableUpdates;

    @Option(name = "--view-security-warnings",
            usage = "Show if any security warnings exist for the requested plugins",
            handler = BooleanOptionHandler.class)
    private boolean showWarnings;

    @Option(name = "--view-all-security-warnings",
            usage = "Set to true to show all plugins that have security warnings",
            handler = BooleanOptionHandler.class)
    private boolean showAllWarnings;

    @Option(name = "--jenkins-update-center",
            usage = "Sets main update center; will override JENKINS_UC environment variable. If not set via CLI " +
                    "option or environment variable, will default to " + Settings.DEFAULT_UPDATE_CENTER_LOCATION,
            handler = URLOptionHandler.class)
    private URL jenkinsUc;

    @Option(name = "--jenkins-experimental-update-center",
            usage = "Sets experimental update center; will override JENKINS_UC_EXPERIMENTAL environment variable. If " +
                    "not set via CLI option or environment variable, will default to " +
                    Settings.DEFAULT_EXPERIMENTAL_UPDATE_CENTER_LOCATION,
            handler = URLOptionHandler.class)
    private URL jenkinsUcExperimental;

    @Option(name = "--jenkins-incrementals-repo-mirror",
            usage = "Set Maven mirror to be used to download plugins from the Incrementals repository, will override " +
                    "the JENKINS_INCREMENTALS_REPO_MIRROR environment variable. If not set via CLI option or " +
                    "environment variable, will default to " + Settings.DEFAULT_INCREMENTALS_REPO_MIRROR_LOCATION,
            handler = URLOptionHandler.class)
    private URL jenkinsIncrementalsRepoMirror;

    @Option(name = "--version", aliases = {"-v"}, usage = "View version and exit", handler = BooleanOptionHandler.class)
    private boolean showVersion;

    @Option(name = "--no-download", usage = "Set true to avoid downloading plugins; can be used in combination with " +
            "other options to see information about plugins and their dependencies",
            handler = BooleanOptionHandler.class)
    private boolean isNoDownload;

    @Option(name = "--latest-specified", usage = "Set to true to download latest transitive dependencies of any " +
            "plugin that is requested to have the latest version. By default, plugin dependency versions will be " +
            "determined by the update center metadata or plugin MANIFEST.MF",
            handler = BooleanOptionHandler.class)
    private boolean useLatestSpecified;

    @Option(name = "--latest", usage = "Set to true to download the latest version of all dependencies, even " +
            "if the version(s) of the requested plugin(s) are not the latest. By default, plugin dependency versions " +
            "will be determined by the update center metadata or plugin MANIFEST.MF",
            handler = BooleanOptionHandler.class)
    private boolean useLatestAll;

    @Option(name = "--help", aliases = {"-h"}, help = true)
    private boolean showHelp;

    /**
     * Creates a configuration class with configurations specified from the CLI and/or environment variables.
     *
     * @return a configuration class that can be passed to the PluginManager class
     */
    Config setup() {
        return Config.builder()
                .withPlugins(getPlugins())
                .withPluginDir(getPluginDir())
                .withJenkinsUc(getUpdateCenter())
                .withJenkinsUcExperimental(getExperimentalUpdateCenter())
                .withJenkinsIncrementalsRepoMirror(getIncrementalsMirror())
                .withJenkinsWar(getJenkinsWar())
                .withShowWarnings(isShowWarnings())
                .withShowAllWarnings(isShowAllWarnings())
                .withShowPluginsToBeDownloaded(isShowPluginsToBeDownloaded())
                .withShowAvailableUpdates(isShowAvailableUpdates())
                .withIsVerbose(isVerbose())
                .withDoDownload(!isNoDownload())
                .withUseLatestSpecified(isUseLatestSpecified())
                .withUseLatestAll(isUseLatestAll())
                .build();
    }

    /**
     * Outputs information about plugin txt or yaml file selected from CLI Option. Throws a PluginInputException if the
     * file does not exist.
     *
     * @return plugin txt or yaml file passed in through CLI, or null if user did not pass in a txt file
     */
    private File getPluginFile() {
        if (pluginFile == null) {
            System.out.println("No .txt or .yaml file containing list of plugins to be downloaded entered.");
        } else {
            if (Files.exists(pluginFile.toPath())) {
                System.out.println("File containing list of plugins to be downloaded: " + pluginFile);
            } else {
                throw new PluginInputException("File containing list of plugins does not exist " + pluginFile.toPath());
            }
        }
        return pluginFile;
    }

    /**
     * Gets the user specified plugin download directory from the CLI option and sets this in the configuration class
     */
    private File getPluginDir() {
        if (pluginDir != null) {
            System.out.println("Plugin download location: " + pluginDir);
            return pluginDir;
        } else if (!StringUtils.isEmpty(System.getenv("PLUGIN_DIR"))) {
            System.out.println("No directory to download plugins entered. " +
                    "Will use location specified in PLUGIN_DIR environment variable: " + System.getenv("PLUGIN_DIR"));
            return new File(System.getenv("PLUGIN_DIR"));
        }
        System.out.println("No directory to download plugins entered. " +
                "Will use default of " + Settings.DEFAULT_PLUGIN_DIR_LOCATION);
        return new File(Settings.DEFAULT_PLUGIN_DIR_LOCATION);
    }

    /**
     * Gets the user specified Jenkins war from the CLI option and sets this in the configuration class
     */
    private String getJenkinsWar() {
        if (jenkinsWarFile == null) {
            System.out.println("No war entered. Will use default of " + Settings.DEFAULT_WAR);
            return Settings.DEFAULT_WAR;
        } else {
            System.out.println("Will use war file: " + jenkinsWarFile);
            return jenkinsWarFile;
        }
    }

    /**
     * Parses user specified plugins from CLI, .txt file, and/or .yaml file; creates and returns a list of corresponding
     * plugin objects
     *
     * @return list of plugins representing user-specified input
     */
    private List<Plugin> getPlugins() {
        List<Plugin> requestedPlugins = new ArrayList<>();
        PluginParser pluginParser = new PluginParser();
        requestedPlugins.addAll(pluginParser.parsePluginsFromCliOption(plugins));

        File pluginFile = getPluginFile();
        if (pluginFile != null) {
            if (isFileExtension(pluginFile, "yaml", "yml")) {
                requestedPlugins.addAll(pluginParser.parsePluginYamlFile(pluginFile));
            } else if (isFileExtension(pluginFile, "txt")) {
                requestedPlugins.addAll(pluginParser.parsePluginTxtFile(pluginFile));
            } else {
                throw new PluginInputException("Unknown file type, file must have .yaml/.yml or .txt extension");
            }
        }
        return requestedPlugins;
    }

    /**
     * Given a file containing a list of plugins and one or more file extensions, returns true if the file extension
     * of the file matches any of the file extension strings gvien
     *
     * @param pluginFile plugin file of which to check extension
     * @param extensions array of strings of extensions to check
     * @return true if file extension matches any of the list of extensions
     */
    private boolean isFileExtension(File pluginFile, String... extensions) {
        String fileExtension = FilenameUtils.getExtension(pluginFile.toString());
        for (String ext : extensions) {
            if (fileExtension.equals(ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the value corresponding to if user selected to show warnings for specified plugins
     *
     * @return true if user selected CLI Option to see warnings for specified plugins
     */
    private boolean isShowWarnings() {
        return showWarnings;
    }

    /**
     * Gets the value corresponding to if the user selected to show security warnings for all plugins
     *
     * @return true if user selected CLI Option to see warnings for all plugins
     */
    private boolean isShowAllWarnings() {
        return showAllWarnings;
    }

    private boolean isShowPluginsToBeDownloaded() {
        return showPluginsToBeDownloaded;
    }

    private boolean isShowAvailableUpdates() {
        return showAvailableUpdates;
    }

    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Determines the update center url string. If a value is set via CLI option, it will override a value set via
     * environment variable. If neither are set, the default in the Settings class will be used.
     *
     * @return the update center url
     */
    private URL getUpdateCenter() {
        URL jenkinsUpdateCenter;

        if (jenkinsUc != null) {
            jenkinsUpdateCenter = jenkinsUc;
            System.out.println("Using update center " + jenkinsUpdateCenter + " specified with CLI option");
        } else if (!StringUtils.isEmpty(System.getenv("JENKINS_UC"))) {
            try {
                jenkinsUpdateCenter = new URL(System.getenv("JENKINS_UC"));
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
            System.out.println("Using update center " + jenkinsUpdateCenter + " from JENKINS_UC environment variable");
        } else {
            jenkinsUpdateCenter = Settings.DEFAULT_UPDATE_CENTER;
            System.out.println("No CLI option or environment variable set for update center, using default of " +
                    jenkinsUpdateCenter);
        }
        return jenkinsUpdateCenter;
    }

    /**
     * Determines the experimental update center url string. If a value is set via CLI option, it will override a value
     * set via environment variable. If neither are set, the default in the Settings class will be used.
     *
     * @return the experimental update center url
     */
    private URL getExperimentalUpdateCenter() {
        URL experimentalUpdateCenter;
        if (jenkinsUcExperimental != null) {
            experimentalUpdateCenter = jenkinsUcExperimental;
            System.out.println(
                    "Using experimental update center " + experimentalUpdateCenter + " specified with CLI option");
        } else if (!StringUtils.isEmpty(System.getenv("JENKINS_UC_EXPERIMENTAL"))) {
            try {
                experimentalUpdateCenter = new URL(System.getenv("JENKINS_UC_EXPERIMENTAL"));
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
            System.out.println("Using experimental update center " + experimentalUpdateCenter +
                    " from JENKINS_UC_EXPERIMENTAL environment variable");
        } else {
            experimentalUpdateCenter = Settings.DEFAULT_EXPERIMENTAL_UPDATE_CENTER;
            System.out.println(
                    "No CLI option or environment variable set for experimental update center, using default of " +
                            experimentalUpdateCenter);
        }
        return experimentalUpdateCenter;
    }

    /**
     * Determines the incrementals repository mirror url string. If a value is set via CLI option, it will override a
     * value set via environment variable. If neither are set, the default in the Settings class will be used.
     *
     * @return the incrementals repository mirror url
     */
    private URL getIncrementalsMirror() {
        URL jenkinsIncrementalsRepo;
        if (jenkinsIncrementalsRepoMirror != null) {
            jenkinsIncrementalsRepo = jenkinsIncrementalsRepoMirror;
            System.out.println("Using incrementals mirror " + jenkinsIncrementalsRepo + " specified with CLI option");
        } else if (!StringUtils.isEmpty(System.getenv("JENKINS_INCREMENTALS_REPO_MIRROR"))) {
            try {
                jenkinsIncrementalsRepo = new URL(System.getenv("JENKINS_INCREMENTALS_REPO_MIRROR"));
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
            System.out.println("Using incrementals mirror " + jenkinsIncrementalsRepo +
                    " from JENKINS_INCREMENTALS_REPO_MIRROR environment variable");
        } else {
            jenkinsIncrementalsRepo = Settings.DEFAULT_INCREMENTALS_REPO_MIRROR;
            System.out.println("No CLI option or environment variable set for incrementals mirror, using default of " +
                    jenkinsIncrementalsRepo);
        }
        return jenkinsIncrementalsRepo;
    }

    /**
     * Returns true if user selected option to not download plugins. By default, isNoDownload is set to false and
     * plugins will be downloaded.
     */
    private boolean isNoDownload() {
        return isNoDownload;
    }

    /**
     * Prints out the Plugin Management Tool version
     */
    public void showVersion() {
        try (InputStream propertyInputStream = getPropertiesInputStream("/.properties")) {
            if (propertyInputStream == null) {
                throw new VersionNotFoundException("No version information available");
            }
            Properties properties = new Properties();
            properties.load(propertyInputStream);

            System.out.println(properties.getProperty("project.version"));
        } catch (IOException e) {
            throw new VersionNotFoundException("No version information available", e);
        }
    }

    /**
     * Returns boolean corresponding to if user wanted to view the help option output
     *
     * @return true if user wanted to show help
     */
    public boolean isShowHelp() {
        return showHelp;
    }

    /**
     * Returns the boolean corresponding to if user wants dependencies of plugins with latest version specified to also
     * be the latest version
     *
     * @return true if user wants transitive dependencies of latest version plugins to also have the latest version
     */
    public boolean isUseLatestSpecified() {
        if (useLatestSpecified && useLatestAll) {
            throw new PluginDependencyStrategyException("Only one plugin dependency version strategy can be selected " +
                    "at a time");
        }
        return useLatestSpecified;
    }

    /**
     * Returns the boolean corresponding to if the user wants all dependencies to be the latest version, even the
     * dependencies of a plugin that had a requested version that was not the latest
     * @return true if the user wants all transitive dependencies to be the latest version
     */
    public boolean isUseLatestAll() {
        if (useLatestSpecified && useLatestAll) {
            throw new PluginDependencyStrategyException("Only one plugin dependency version strategy can be selected " +
                    "at a time");
        }
        return useLatestAll;
    }

    // visible for testing
    public InputStream getPropertiesInputStream(String path) {
        return this.getClass().getResourceAsStream(path);
    }

    /**
     * Returns if user requested to see the tool version from the CLI options
     *
     * @return true if user passed in option to see version, false otherwise
     */
    public boolean isShowVersion() {
        return showVersion;
    }
}
