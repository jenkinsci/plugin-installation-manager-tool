package io.jenkins.tools.pluginmanager.cli.options;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.util.VersionNumber;
import io.jenkins.tools.pluginmanager.cli.util.VersionNotFoundException;
import io.jenkins.tools.pluginmanager.config.Config;
import io.jenkins.tools.pluginmanager.config.Credentials;
import io.jenkins.tools.pluginmanager.config.HashFunction;
import io.jenkins.tools.pluginmanager.config.OutputFormat;
import io.jenkins.tools.pluginmanager.config.PluginInputException;
import io.jenkins.tools.pluginmanager.config.Settings;
import io.jenkins.tools.pluginmanager.impl.Plugin;
import io.jenkins.tools.pluginmanager.util.PluginListParser;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.CheckForNull;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine;
import picocli.CommandLine.Option;

public class BaseCliOptions {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec; // injected by picocli

    //path must include plugins.txt
    @Option(names = {"--plugin-file", "-f"},
            description = "Path to plugins.txt or plugins.yaml file")
    private File pluginFile;

    @Option(names = {"--plugin-download-directory", "-d"},
            description = "Path to directory in which to install plugins; will override PLUGIN_DIR environment variable.")
    private File pluginDir;

    @Option(names = "--clean-download-directory",
            description = "If sets, cleans the plugin download directory before plugin installation. " +
                    "Otherwise the tool performs plugin download and reports compatibility issues, if any.")
    private boolean cleanPluginDir;

    @Option(names = {"--plugins", "-p"},
            description = "List of plugins to install, separated by a space")
    private String[] plugins = new String[0];

    @CheckForNull
    private VersionNumber jenkinsVersion;

    @Option(names = "--jenkins-version",
            description = "Jenkins version to be used. " +
                    "If undefined, Plugin Manager will use alternative ways to retrieve the version, e.g. from WAR")
    public void setJenkinsVersion(String version) {
        try {
            jenkinsVersion = new VersionNumber(version);
        } catch (Exception ex) {
            throw new CommandLine.ParameterException(spec.commandLine(), "Failed to parse Jenkins version number (--jenkins-version): " + version, ex);
        }
    }

    @Option(names = {"--war", "-w"}, description = "Path to Jenkins war file")
    @CheckForNull
    private String jenkinsWarFile;

    @Option(names = {"--list", "-l"},
            description = "Lists all plugins currently installed and if given a list of " +
            "plugins to install either via file or CLI option, all plugins that will be installed by the tool")
    private boolean showPluginsToBeDownloaded;

    @Option(names = "--verbose",
            description = "Verbose logging")
    private boolean verbose;

    @Option(names = "--available-updates",
            description = "Show available plugin updates for the requested plugins")
    private boolean showAvailableUpdates;

    @Option(names = {"--output", "-o"},
            description = "Output format for available updates")
    private OutputFormat outputFormat;

    @Option(names = "--view-security-warnings",
            description = "Show if any security warnings exist for the requested plugins")
    private boolean showWarnings;

    @Option(names = "--view-all-security-warnings",
            description = "Set to true to show all plugins that have security warnings")
    private boolean showAllWarnings;

    @Option(names = "--jenkins-update-center",
            description = "Sets main update center; will override JENKINS_UC environment variable. If not set via CLI " +
                    "option or environment variable, will default to " + Settings.DEFAULT_UPDATE_CENTER_LOCATION)
    private URL jenkinsUc;

    @CommandLine.Option(names = "--jenkins-experimental-update-center",
            description = "Sets experimental update center; will override JENKINS_UC_EXPERIMENTAL environment variable. If " +
                    "not set via CLI option or environment variable, will default to " +
                    Settings.DEFAULT_EXPERIMENTAL_UPDATE_CENTER_LOCATION)
    private URL jenkinsUcExperimental;

    @Option(names = "--jenkins-incrementals-repo-mirror",
            description = "Set Maven mirror to be used to download plugins from the Incrementals repository, will override " +
                    "the JENKINS_INCREMENTALS_REPO_MIRROR environment variable. If not set via CLI option or " +
                    "environment variable, will default to " + Settings.DEFAULT_INCREMENTALS_REPO_MIRROR_LOCATION)
    private URL jenkinsIncrementalsRepoMirror;

    @Option(names = "--jenkins-plugin-info",
            description = "Sets the location of plugin information; will override JENKINS_PLUGIN_INFO environment variable. " +
                    "If not set via CLI option or environment variable, will default to " +
                    Settings.DEFAULT_PLUGIN_INFO_LOCATION)
    private URL jenkinsPluginInfo;

    @Option(names = "--latest-specified", description = "Set to true to download latest transitive dependencies of any " +
            "plugin that is requested to have the latest version. By default, plugin dependency versions will be " +
            "determined by the update center metadata or plugin MANIFEST.MF")
    private boolean useLatestSpecified;

    @SuppressWarnings("FieldMayBeFinal")
    @Option(names = "--latest", defaultValue = "true", description = "Set to true to download the latest version of all dependencies, even " +
            "if the version(s) of the requested plugin(s) are not the latest. By default, plugin dependency versions " +
            "will be determined by the update center metadata or plugin MANIFEST.MF")
    private boolean useLatestAll;

    @Option(names = "--skip-failed-plugins", description = "Set to true to skip installing plugins that have failed to download. " +
            "By default, if a single plugin is unavailable then all plugins fail to download and install.")
    private boolean skipFailedPlugins;

    @Option(names = "--credentials", description = "Comma-separated list of credentials in format '<host>[:port]:<username>:<password>'. The password must not contain space or ','",
            converter = CredentialsTypeConverter.class)
    private List<Credentials> credentials;

    /**
     * Creates a configuration class with configurations specified from the CLI and/or environment variables.
     *
     * @return a configuration class that can be passed to the PluginManager class
     */
    public Config setup() {
        return setupWithConfig(null);
    }

    /**
     * Creates a configuration class with configurations specified from the CLI and/or environment variables.
     *
     * @param configurator Configurator that sets additional options
     * @return a configuration class that can be passed to the PluginManager class
     * @since 3.0
     */
    public Config setupWithConfig(@CheckForNull Config.Configurator configurator) {
        return Config.builder()
                .withPlugins(getPlugins())
                .withPluginDir(getPluginDir())
                .withCleanPluginsDir(isCleanPluginDir())
                .withJenkinsUc(getUpdateCenter())
                .withJenkinsUcExperimental(getExperimentalUpdateCenter())
                .withJenkinsIncrementalsRepoMirror(getIncrementalsMirror())
                .withJenkinsPluginInfo(getPluginInfo())
                .withJenkinsVersion(getJenkinsVersion())
                .withJenkinsWar(getJenkinsWar())
                .withShowWarnings(isShowWarnings())
                .withShowAllWarnings(isShowAllWarnings())
                .withShowPluginsToBeDownloaded(isShowPluginsToBeDownloaded())
                .withShowAvailableUpdates(isShowAvailableUpdates())
                .withOutputFormat(getOutputFormat())
                .withIsVerbose(isVerbose())
                .withUseLatestSpecified(isUseLatestSpecified())
                .withUseLatestAll(isUseLatestAll())
                .withSkipFailedPlugins(isSkipFailedPlugins())
                .withCredentials(credentials)
                .withHashFunction(getHashFunction())
                .configure(configurator)
                .build();
    }

    public OutputFormat getOutputFormat() {
        return outputFormat;
    }

    /**
     * Outputs information about plugin txt or yaml file selected from CLI Option. Throws a PluginInputException if the
     * file does not exist.
     *
     * @return plugin txt or yaml file passed in through CLI, or null if user did not pass in a txt file
     */
    private File getPluginFile() {
        if (pluginFile == null) {
            if (verbose) {
                System.out.println("No .txt or .yaml file containing list of plugins to be downloaded entered.");
            }
        } else {
            if (Files.exists(pluginFile.toPath())) {
                if (verbose) {
                    System.out.println("File containing list of plugins to be downloaded: " + pluginFile);
                }
            } else {
                throw new PluginInputException("File containing list of plugins does not exist " + pluginFile.toPath());
            }
        }
        return pluginFile;
    }

    /**
     * Gets the user specified plugin download directory from the CLI option and sets this in the configuration class
     */
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "we want the user to be able to specify a path")
    private File getPluginDir() {
        if (pluginDir != null) {
            if (verbose) {
                System.out.println("Plugin download location: " + pluginDir);
            }
            return pluginDir;
        } else if (!StringUtils.isEmpty(System.getenv("PLUGIN_DIR"))) {
            if (verbose) {
                System.out.println("No directory to download plugins entered. " +
                        "Will use location specified in PLUGIN_DIR environment variable: " + System.getenv("PLUGIN_DIR"));
            }
            return new File(System.getenv("PLUGIN_DIR"));
        }
        if (verbose) {
            System.out.println("No directory to download plugins entered. " +
                    "Will use default of " + Settings.DEFAULT_PLUGIN_DIR_LOCATION);
        }
        return new File(Settings.DEFAULT_PLUGIN_DIR_LOCATION);
    }

    public boolean isCleanPluginDir() {
        return cleanPluginDir;
    }

    @CheckForNull
    private VersionNumber getJenkinsVersion() {
        if (jenkinsVersion != null) {
            return jenkinsVersion;
        }

        String fromEnv = System.getenv("JENKINS_VERSION");
        if (StringUtils.isNotBlank(fromEnv)) {
            try {
                return new VersionNumber(fromEnv);
            } catch (Exception ex) {
                throw new VersionNotFoundException("Failed to parse the version from JENKINS_VERSION=" + fromEnv, ex);
            }
        }

        return null;
    }

    /**
     * Gets the user specified Jenkins war from the CLI option and sets this in the configuration class
     */
    private String getJenkinsWar() {
        if (jenkinsWarFile == null) {
            if (verbose) {
                System.out.println("No war entered. Will use default of " + Settings.DEFAULT_WAR);
            }
            return Settings.DEFAULT_WAR;
        } else {
            if (verbose) {
                System.out.println("Will use war file: " + jenkinsWarFile);
            }
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
        PluginListParser pluginParser = new PluginListParser(verbose);
        List<Plugin> requestedPlugins = new ArrayList<>(pluginParser.parsePluginsFromCliOption(plugins));

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

    public boolean isShowAvailableUpdates() {
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
        try {
            if (jenkinsUc != null) {
                jenkinsUpdateCenter = new URL(appendFilePathIfNotPresent(jenkinsUc.toString()));
                if (verbose) {
                    System.out.println("Using update center " + jenkinsUpdateCenter + " specified with CLI option");
                }
            } else {
                String jenkinsUcFromEnv = System.getenv("JENKINS_UC");
                if (!StringUtils.isEmpty(jenkinsUcFromEnv)) {
                    jenkinsUpdateCenter = new URL(appendFilePathIfNotPresent(jenkinsUcFromEnv));
                    if (verbose) {
                        System.out.println("Using update center " + jenkinsUpdateCenter + " from JENKINS_UC environment variable");
                    }
                } else {
                    jenkinsUpdateCenter = Settings.DEFAULT_UPDATE_CENTER;
                    if (verbose) {
                        System.out.println("No CLI option or environment variable set for update center, using default of " +
                                jenkinsUpdateCenter);
                    }
                }
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        return jenkinsUpdateCenter;
    }

    public String appendFilePathIfNotPresent(@NonNull String updateCenter) {
        if (updateCenter.endsWith("json")) {
            return updateCenter;
        }
        return updateCenter + Settings.DEFAULT_UPDATE_CENTER_FILENAME;
    }

    /**
     * Determines the experimental update center url string. If a value is set via CLI option, it will override a value
     * set via environment variable. If neither are set, the default in the Settings class will be used.
     *
     * @return the experimental update center url
     */
    private URL getExperimentalUpdateCenter() {
        URL experimentalUpdateCenter;
        try {
            if (jenkinsUcExperimental != null) {
                experimentalUpdateCenter = new URL(appendFilePathIfNotPresent(jenkinsUcExperimental.toString()));
                if (verbose) {
                    System.out.println(
                            "Using experimental update center " + experimentalUpdateCenter + " specified with CLI option");
                }
            } else if (!StringUtils.isEmpty(System.getenv("JENKINS_UC_EXPERIMENTAL"))) {
                experimentalUpdateCenter = new URL(appendFilePathIfNotPresent(System.getenv("JENKINS_UC_EXPERIMENTAL")));
                if (verbose) {
                    System.out.println("Using experimental update center " + experimentalUpdateCenter +
                            " from JENKINS_UC_EXPERIMENTAL environment variable");
                }
            } else {
                experimentalUpdateCenter = Settings.DEFAULT_EXPERIMENTAL_UPDATE_CENTER;
                if (verbose) {
                    System.out.println(
                            "No CLI option or environment variable set for experimental update center, using default of " +
                                    experimentalUpdateCenter);
                }
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
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
            if (verbose) {
                System.out.println("Using incrementals mirror " + jenkinsIncrementalsRepo + " specified with CLI option");
            }
        } else if (!StringUtils.isEmpty(System.getenv("JENKINS_INCREMENTALS_REPO_MIRROR"))) {
            try {
                jenkinsIncrementalsRepo = new URL(System.getenv("JENKINS_INCREMENTALS_REPO_MIRROR"));
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
            if (verbose) {
                System.out.println("Using incrementals mirror " + jenkinsIncrementalsRepo +
                        " from JENKINS_INCREMENTALS_REPO_MIRROR environment variable");
            }
        } else {
            jenkinsIncrementalsRepo = Settings.DEFAULT_INCREMENTALS_REPO_MIRROR;
            if (verbose) {
                System.out.println("No CLI option or environment variable set for incrementals mirror, using default of " +
                        jenkinsIncrementalsRepo);
            }
        }
        return jenkinsIncrementalsRepo;
    }

    /**
     * Determines the plugin information url string. If a value is set via CLI option, it will override a value
     * set via environment variable. If neither are set, the default in the Settings class will be used.
     *
     * @return the plugin information url
     */
    private URL getPluginInfo() {
        URL pluginInfo;
        if (jenkinsPluginInfo != null) {
            pluginInfo = jenkinsPluginInfo;
            if (verbose) {
                System.out.println("Using plugin info " + jenkinsPluginInfo + " specified with CLI option");
            }
        } else if (!StringUtils.isEmpty(System.getenv("JENKINS_PLUGIN_INFO"))) {
            try {
                pluginInfo = new URL(System.getenv("JENKINS_PLUGIN_INFO"));
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
            if (verbose) {
                System.out.println("Using plugin info " + pluginInfo +
                        " from JENKINS_PLUGIN_INFO environment variable");
            }
        } else {
            pluginInfo = Settings.DEFAULT_PLUGIN_INFO;
            if (verbose) {
                System.out.println("No CLI option or environment variable set for plugin info, using default of " +
                        pluginInfo);
            }
        }
        return pluginInfo;
    }

    /**
     * Returns the boolean corresponding to if user wants dependencies of plugins with latest version specified to also
     * be the latest version
     *
     * @return true if user wants transitive dependencies of latest version plugins to also have the latest version
     */
    public boolean isUseLatestSpecified() {
        return useLatestSpecified;
    }

    /**
     * Returns the boolean corresponding to if the user wants to skip plugins that fail to download, including their
     * dependencies
     *
     * @return true if the user wants to skip plugins that have failed to download from the specified UpdateCenter
     */
    public boolean isSkipFailedPlugins() {
        return skipFailedPlugins;
    }

    /**
     * Returns the boolean corresponding to if the user wants all dependencies to be the latest version, even the
     * dependencies of a plugin that had a requested version that was not the latest
     *
     * @return true if the user wants all transitive dependencies to be the latest version
     */
    public boolean isUseLatestAll() {
        if (useLatestSpecified) {
            return false;
        }
        return useLatestAll;
    }

    /**
     * Determines the hash function used with the Update Center
     * set via environment variable only
     *
     * @return the string value for the hash function. Currently allows sha1, sha256(default), sha512
     */
    private HashFunction getHashFunction() {

        String fromEnv = System.getenv("JENKINS_UC_HASH_FUNCTION");
        if (StringUtils.isNotBlank(fromEnv)) {
            return HashFunction.valueOf(fromEnv.toUpperCase());
        } else {
            return Settings.DEFAULT_HASH_FUNCTION;
        }
    }

}
