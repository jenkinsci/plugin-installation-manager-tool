package io.jenkins.tools.pluginmanager.config;

import io.jenkins.tools.pluginmanager.impl.Plugin;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for the plugin installation manager tool.
 * <br/>
 * Construct it with
 * {@code
 * Config.builder()
 * ...
 * build()
 * }
 * <br/>
 * Defaults for update centers will be set for you
 */
public class Config {
    private File pluginDir;
    private boolean showWarnings;
    private boolean showAllWarnings;
    private String jenkinsWar;
    private List<Plugin> plugins;
    private URL jenkinsUc;
    private URL jenkinsUcExperimental;
    private URL jenkinsIncrementalsRepoMirror;

    private Config(
            File pluginDir,
            boolean showWarnings,
            boolean showAllWarnings,
            String jenkinsWar,
            List<Plugin> plugins,
            URL jenkinsUc,
            URL jenkinsUcExperimental,
            URL jenkinsIncrementalsRepoMirror
    ) {
        this.pluginDir = pluginDir;
        this.showWarnings = showWarnings;
        this.showAllWarnings = showAllWarnings;
        this.jenkinsWar = jenkinsWar;
        this.plugins = plugins;
        this.jenkinsUc = jenkinsUc;
        this.jenkinsUcExperimental = jenkinsUcExperimental;
        this.jenkinsIncrementalsRepoMirror = jenkinsIncrementalsRepoMirror;
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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private File pluginDir;
        private boolean showWarnings;
        private boolean showAllWarnings;
        private String jenkinsWar;
        private List<Plugin> plugins = new ArrayList<>();
        ;
        private URL jenkinsUc = Settings.DEFAULT_UPDATE_CENTER;
        private URL jenkinsUcExperimental = Settings.DEFAULT_EXPERIMENTAL_UPDATE_CENTER;
        private URL jenkinsIncrementalsRepoMirror = Settings.DEFAULT_INCREMENTALS_REPO_MIRROR;

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


        public Builder withJenkinsWar(String jenkinsWar) {
            this.jenkinsWar = jenkinsWar;
            return this;
        }

        public Builder withPlugins(List<Plugin> plugins) {
            this.plugins = plugins;
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

        public Config build() {
            return new Config(
                    pluginDir,
                    showWarnings,
                    showAllWarnings,
                    jenkinsWar,
                    plugins,
                    jenkinsUc,
                    jenkinsUcExperimental,
                    jenkinsIncrementalsRepoMirror
            );
        }


    }
}
