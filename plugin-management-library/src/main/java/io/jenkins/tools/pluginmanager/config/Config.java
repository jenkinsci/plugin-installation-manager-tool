package io.jenkins.tools.pluginmanager.config;

import io.jenkins.tools.pluginmanager.impl.Plugin;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
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
    private File pluginDir;
    private boolean showWarnings;
    private boolean showAllWarnings;
    private boolean showAvailableUpdates;
    private boolean showPluginsToBeDownloaded;
    private String jenkinsWar;
    private List<Plugin> plugins;
    private boolean verbose;
    private URL jenkinsUc;
    private URL jenkinsUcExperimental;
    private URL jenkinsIncrementalsRepoMirror;
    private boolean doDownload;
    private boolean useLatestSpecified;
    private boolean useLatestAll;
    private boolean skipFailedPlugins;

    private Config(
            File pluginDir,
            boolean showWarnings,
            boolean showAllWarnings,
            boolean showAvailableUpdates,
            boolean showPluginsToBeDownloaded,
            boolean verbose,
            String jenkinsWar,
            List<Plugin> plugins,
            URL jenkinsUc,
            URL jenkinsUcExperimental,
            URL jenkinsIncrementalsRepoMirror,
            boolean doDownload,
            boolean useLatestSpecified,
            boolean useLatestAll,
            boolean skipFailedPlugins
    ) {
        this.pluginDir = pluginDir;
        this.showWarnings = showWarnings;
        this.showAllWarnings = showAllWarnings;
        this.showAvailableUpdates = showAvailableUpdates;
        this.showPluginsToBeDownloaded = showPluginsToBeDownloaded;
        this.verbose = verbose;
        this.jenkinsWar = jenkinsWar;
        this.plugins = plugins;
        this.jenkinsUc = jenkinsUc;
        this.jenkinsUcExperimental = jenkinsUcExperimental;
        this.jenkinsIncrementalsRepoMirror = jenkinsIncrementalsRepoMirror;
        this.doDownload = doDownload;
        this.useLatestSpecified = useLatestSpecified;
        this.useLatestAll = useLatestAll;
        this.skipFailedPlugins = skipFailedPlugins;

    }

    public File getPluginDir() {
        return pluginDir;
    }

    public boolean isShowWarnings() {
        return showWarnings;
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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private File pluginDir;
        private boolean showWarnings;
        private boolean showAllWarnings;
        private boolean showAvailableUpdates;
        private boolean showPluginsToBeDownloaded;
        private boolean verbose;
        private String jenkinsWar;
        private List<Plugin> plugins = new ArrayList<>();
        private URL jenkinsUc = Settings.DEFAULT_UPDATE_CENTER;
        private URL jenkinsUcExperimental = Settings.DEFAULT_EXPERIMENTAL_UPDATE_CENTER;
        private URL jenkinsIncrementalsRepoMirror = Settings.DEFAULT_INCREMENTALS_REPO_MIRROR;
        private boolean doDownload;
        private boolean useLatestSpecified;
        private boolean useLatestAll;
        private boolean skipFailedPlugins;

        private Builder() {
        }

        public Builder withPluginDir(File pluginDir) {
            this.pluginDir = pluginDir;
            return this;
        }

        public Builder withShowWarnings(boolean showWarnings) {
            this.showWarnings = showWarnings;
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

        public Builder withJenkinsWar(String jenkinsWar) {
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
        }

        public Config build() {
            return new Config(
                    pluginDir,
                    showWarnings,
                    showAllWarnings,
                    showAvailableUpdates,
                    showPluginsToBeDownloaded,
                    verbose,
                    jenkinsWar,
                    plugins,
                    jenkinsUc,
                    jenkinsUcExperimental,
                    jenkinsIncrementalsRepoMirror,
                    doDownload,
                    useLatestSpecified,
                    useLatestAll,
                    skipFailedPlugins
            );
        }


    }
}
