package io.jenkins.tools.pluginmanager.config;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.util.VersionNumber;
import io.jenkins.tools.pluginmanager.impl.Plugin;
import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configuration for the plugin installation manager tool.
 * Construct it with
 * {@code
 * Config.builder()
 * ...
 * build()
 * }
 * Defaults for update centers will be set for you
 */
public class Config {
    private final File pluginDir;
    private final boolean cleanPluginDir;
    private final boolean showWarnings;
    private final boolean hideWarnings;
    private final boolean showAllWarnings;
    private final boolean showAvailableUpdates;
    private final boolean showPluginsToBeDownloaded;

    /**
     * Explicitly passed Jenkins version.
     */
    @CheckForNull
    private final VersionNumber jenkinsVersion;
    /**
     * Path to the Jenkins WAR file.
     */
    @CheckForNull
    private final String jenkinsWar;
    private final List<Plugin> plugins;
    private final boolean verbose;
    private final HashFunction hashFunction;
    private final URL jenkinsUc;
    private final URL jenkinsUcExperimental;
    private final URL jenkinsIncrementalsRepoMirror;
    private final URL jenkinsPluginInfo;
    private final boolean doDownload;
    private final boolean useLatestSpecified;
    private final boolean useLatestAll;
    private final boolean skipFailedPlugins;
    @NonNull
    private final OutputFormat outputFormat;
    private final List<Credentials> credentials;
    private final Path cachePath;
    private final LogOutput logOutput;

    private Config(
            File pluginDir,
            boolean cleanPluginDir,
            boolean showWarnings,
            boolean showAllWarnings,
            boolean showAvailableUpdates,
            boolean showPluginsToBeDownloaded,
            boolean verbose,
            VersionNumber jenkinsVersion,
            String jenkinsWar,
            List<Plugin> plugins,
            URL jenkinsUc,
            URL jenkinsUcExperimental,
            URL jenkinsIncrementalsRepoMirror,
            URL jenkinsPluginInfo,
            boolean doDownload,
            boolean useLatestSpecified,
            boolean useLatestAll,
            boolean skipFailedPlugins,
            OutputFormat outputFormat,
            HashFunction hashFunction,
            List<Credentials> credentials,
            Path cachePath,
            boolean hideWarnings) {
        this.pluginDir = pluginDir;
        this.cleanPluginDir = cleanPluginDir;
        this.showWarnings = showWarnings;
        this.showAllWarnings = showAllWarnings;
        this.showAvailableUpdates = showAvailableUpdates;
        this.showPluginsToBeDownloaded = showPluginsToBeDownloaded;
        this.verbose = verbose;
        this.jenkinsVersion = jenkinsVersion;
        this.jenkinsWar = jenkinsWar;
        this.plugins = plugins;
        this.jenkinsUc = jenkinsUc;
        this.jenkinsUcExperimental = jenkinsUcExperimental;
        this.jenkinsIncrementalsRepoMirror = jenkinsIncrementalsRepoMirror;
        this.jenkinsPluginInfo = jenkinsPluginInfo;
        this.doDownload = doDownload;
        this.useLatestSpecified = useLatestSpecified;
        this.useLatestAll = useLatestAll;
        this.skipFailedPlugins = skipFailedPlugins;
        this.outputFormat = outputFormat;
        this.credentials = credentials;
        this.hashFunction = hashFunction;
        this.cachePath = cachePath;
        this.logOutput = new LogOutput(verbose);
        this.hideWarnings = hideWarnings;
    }

    public File getPluginDir() {
        return pluginDir;
    }

    public boolean isCleanPluginDir() {
        return cleanPluginDir;
    }

    public boolean isShowWarnings() {
        return showWarnings;
    }

    public boolean isHideWarnings() {
        return hideWarnings;
    }

    public boolean isShowAllWarnings() {
        return showAllWarnings;
    }

    public boolean isShowAvailableUpdates() {
        return showAvailableUpdates;
    }

    public boolean isShowPluginsToBeDownloaded() {
        return showPluginsToBeDownloaded;
    }

    public boolean isVerbose() {
        return verbose;
    }

    @CheckForNull
    public String getJenkinsWar() {
        return jenkinsWar;
    }

    public List<Plugin> getPlugins() {
        return plugins;
    }

    public URL getJenkinsUc() {
        return jenkinsUc;
    }

    public URL getJenkinsUcExperimental() {
        return jenkinsUcExperimental;
    }

    public URL getJenkinsIncrementalsRepoMirror() {
        return jenkinsIncrementalsRepoMirror;
    }

    public URL getJenkinsPluginInfo() {
        return jenkinsPluginInfo;
    }

    /**
     * Get Jenkins version to be used by the plugin manager.
     * @return Jenkins version.
     *         When {@code null}, instructs the plugin manager to use alternative version retrieval mechanisms.
     */
    @CheckForNull
    public VersionNumber getJenkinsVersion() {
        return jenkinsVersion;
    }

    public boolean doDownload() {
        return doDownload;
    }

    public boolean isUseLatestSpecified() {
        return useLatestSpecified;
    }

    public boolean isUseLatestAll() { return useLatestAll; }

    public boolean isSkipFailedPlugins() {
        return skipFailedPlugins;
    }

    public List<Credentials> getCredentials() {
        return credentials;
    }

    public static Builder builder() {
        return new Builder();
    }

    public HashFunction getHashFunction() {
        return hashFunction;
    }

    @NonNull
    public OutputFormat getOutputFormat() {
        return outputFormat;
    }

    public Path getCachePath() {
        return cachePath;
    }

    public LogOutput getLogOutput() {
        return logOutput;
    }

    public static class Builder {
        private File pluginDir;
        private boolean cleanPluginDir;
        private boolean showWarnings;
        private boolean hideWarnings;
        private boolean showAllWarnings;
        private boolean showAvailableUpdates;
        private boolean showPluginsToBeDownloaded;
        private boolean verbose;
        private VersionNumber jenkinsVersion;
        private String jenkinsWar;
        private List<Plugin> plugins = new ArrayList<>();
        private URL jenkinsUc = Settings.DEFAULT_UPDATE_CENTER;
        private URL jenkinsUcExperimental = Settings.DEFAULT_EXPERIMENTAL_UPDATE_CENTER;
        private URL jenkinsIncrementalsRepoMirror = Settings.DEFAULT_INCREMENTALS_REPO_MIRROR;
        private URL jenkinsPluginInfo = Settings.DEFAULT_PLUGIN_INFO;
        private boolean doDownload;
        private boolean useLatestSpecified;
        private boolean useLatestAll;
        private boolean skipFailedPlugins;
        private OutputFormat outputFormat = OutputFormat.STDOUT;
        private List<Credentials> credentials = Collections.emptyList();
        private HashFunction hashFunction = Settings.DEFAULT_HASH_FUNCTION;
        private Path cachePath = Settings.DEFAULT_CACHE_PATH;

        private Builder() {
        }

        public Builder withPluginDir(File pluginDir) {
            this.pluginDir = pluginDir;
            return this;
        }

        public Builder withCleanPluginsDir(boolean cleanPluginDir) {
            this.cleanPluginDir = cleanPluginDir;
            return this;
        }

        public Builder withShowWarnings(boolean showWarnings) {
            this.showWarnings = showWarnings;
            return this;
        }

        public Builder withHideWarnings(boolean hideWarnings) {
            this.hideWarnings = hideWarnings;
            return this;
        }

        public Builder withShowAllWarnings(boolean showAllWarnings) {
            this.showAllWarnings = showAllWarnings;
            return this;
        }

        public Builder withShowAvailableUpdates(boolean showAvailableUpdates) {
            this.showAvailableUpdates = showAvailableUpdates;
            return this;
        }

        public Builder withShowPluginsToBeDownloaded(boolean showPluginsToBeDownloaded) {
            this.showPluginsToBeDownloaded = showPluginsToBeDownloaded;
            return this;
        }

        /**
         * Sets Jenkins version to be used for retrieving compatible plugins.
         * @param jenkinsVersion Jenkins version.
         *        {@code null} to make undefined and to force alternative version retrieval logic.
         * @return builder for jenkinsVersion
         */
        public Builder withJenkinsVersion(@CheckForNull VersionNumber jenkinsVersion) {
            this.jenkinsVersion = jenkinsVersion;
            return this;
        }

        public Builder withJenkinsWar(@CheckForNull String jenkinsWar) {
            this.jenkinsWar = jenkinsWar;
            return this;
        }

        public Builder withPlugins(List<Plugin> plugins) {
            this.plugins = plugins;
            return this;
        }

        public Builder withIsVerbose(boolean verbose) {
            this.verbose = verbose;
            return this;
        }

        public Builder withJenkinsUc(URL jenkinsUc) {
            this.jenkinsUc = jenkinsUc;
            return this;
        }

        public Builder withJenkinsUcExperimental(URL jenkinsUcExperimental) {
            this.jenkinsUcExperimental = jenkinsUcExperimental;
            return this;
        }

        public Builder withJenkinsIncrementalsRepoMirror(URL jenkinsIncrementalsRepoMirror) {
            this.jenkinsIncrementalsRepoMirror = jenkinsIncrementalsRepoMirror;
            return this;
        }

        public Builder withJenkinsPluginInfo(URL jenkinsPluginInfo) {
            this.jenkinsPluginInfo = jenkinsPluginInfo;
            return this;
        }

        public Builder withDoDownload(boolean doDownload) {
            this.doDownload = doDownload;
            return this;
        }

        public Builder withUseLatestSpecified(boolean useLatestSpecifed) {
            this.useLatestSpecified = useLatestSpecifed;
            return this;
        }

        public Builder withUseLatestAll(boolean useLatestAll) {
            this.useLatestAll = useLatestAll;
            return this;
        }

        public Builder withSkipFailedPlugins(boolean skipFailedPlugins) {
            this.skipFailedPlugins = skipFailedPlugins;
            return this;
        }

        public Builder withOutputFormat(OutputFormat outputFormat) {
            this.outputFormat = outputFormat;
            return this;
        }


        public Builder withCredentials(List<Credentials> credentials) {
            if (credentials == null) {
                this.credentials = Collections.emptyList();
                return this;
            }

            this.credentials  = credentials;
            return this;
        }

        public Builder withHashFunction(HashFunction hashFunction) {
            this.hashFunction = hashFunction;
            return this;
        }

        public Builder withCachePath(@NonNull Path cachePath) {
            this.cachePath = cachePath;
            return this;
        }

        public Config build() {
            return new Config(
                    pluginDir,
                    cleanPluginDir,
                    showWarnings,
                    showAllWarnings,
                    showAvailableUpdates,
                    showPluginsToBeDownloaded,
                    verbose,
                    jenkinsVersion,
                    jenkinsWar,
                    plugins,
                    jenkinsUc,
                    jenkinsUcExperimental,
                    jenkinsIncrementalsRepoMirror,
                    jenkinsPluginInfo,
                    doDownload,
                    useLatestSpecified,
                    useLatestAll,
                    skipFailedPlugins,
                    outputFormat,
                    hashFunction,
                    credentials,
                    cachePath,
                    hideWarnings
            );
        }

    }
}
