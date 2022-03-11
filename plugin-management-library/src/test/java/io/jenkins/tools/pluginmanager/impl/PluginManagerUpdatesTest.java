package io.jenkins.tools.pluginmanager.impl;

import io.jenkins.tools.pluginmanager.config.Config;
import io.jenkins.tools.pluginmanager.config.Settings;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class PluginManagerUpdatesTest {

    private PluginManager pm;

    @BeforeEach
    public void before() throws IOException {
        Config cfg = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .build();

        pm = new PluginManager(cfg);

        JSONObject pluginInfoJson = loadPluginVersionsFromClassPath();
        pm.setLatestUcPlugins(pluginInfoJson.getJSONObject("plugins"));

        JSONObject experimentalPlugins = loadExperimentalPluginVersionsFromClassPath();
        pm.setExperimentalPlugins(experimentalPlugins.getJSONObject("plugins"));
    }

    @Test
    public void simpleUpdate() {
        List<Plugin> latestVersionsOfPlugins = pm.getLatestVersionsOfPlugins(singletonList(plugin("mailer", "1.31")));

        assertThat(latestVersionsOfPlugins)
                .containsExactly(plugin("mailer", "1.32.1"));
    }

    @Test
    public void latestIsNotUpdated() {
        List<Plugin> latestVersionsOfPlugins = pm.getLatestVersionsOfPlugins(singletonList(plugin("mailer", null)));

        assertThat(latestVersionsOfPlugins)
            .containsExactly(plugin("mailer", "latest"));
    }

    private JSONObject loadPluginVersionsFromClassPath() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("available-updates/update-center.actual.json")) {
            return new JSONObject(IOUtils.toString(stream, StandardCharsets.UTF_8));
        }
    }

    private JSONObject loadExperimentalPluginVersionsFromClassPath() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("available-updates/update-center.experimental.json")) {
            return new JSONObject(IOUtils.toString(stream, StandardCharsets.UTF_8));
        }
    }

    @Test
    @Disabled("Need another source of data for incrementals, add later")
    public void updateIncremental() {
        List<Plugin> latestVersionsOfPlugins = pm.getLatestVersionsOfPlugins(singletonList(
                pluginIncremental("mailer", "org.jenkins-ci.plugins", "1.31-rc315.eb08e134da74")
        ));

        assertThat(latestVersionsOfPlugins)
                .containsExactly(pluginIncremental("mailer", "org.jenkins-ci.plugins", "1.31-rc316.fb08e134da74"));
    }

    @Test
    public void incrementalIsnotUpgradedToGA() {
        List<Plugin> latestVersionsOfPlugins = pm.getLatestVersionsOfPlugins(singletonList(
                pluginIncremental("mailer", "org.jenkins-ci.plugins", "1.31-rc316.fb08e134da74")
        ));

        assertThat(latestVersionsOfPlugins)
                .containsExactly(pluginIncremental("mailer", "org.jenkins-ci.plugins", "1.31-rc316.fb08e134da74"));
    }

    @Test
    public void nonExistentPlugin() {
        List<Plugin> latestVersionsOfPlugins = pm.getLatestVersionsOfPlugins(singletonList(plugin("non-existing-plugin", "1.31")));

        assertThat(latestVersionsOfPlugins)
                .containsExactly(plugin("non-existing-plugin", "1.31"));
    }

    @Test
    public void pluginHasCustomUrl() {
        Plugin mailer = pluginUrl("mailer", "http://archives.jenkins-ci.org/plugins/mailer/1.31/mailer.hpi");
        List<Plugin> latestVersionsOfPlugins = pm.getLatestVersionsOfPlugins(singletonList(
                mailer)
        );

        assertThat(latestVersionsOfPlugins)
                .containsExactly(pluginUrl("mailer", "http://archives.jenkins-ci.org/plugins/mailer/1.31/mailer.hpi"));
    }

    @Test
    public void pluginIsFromExperimentalUpdateCenter() {
        List<Plugin> latestVersionsOfPlugins = pm.getLatestVersionsOfPlugins(singletonList(
                plugin("help-editor", "0.1-beta-1"))
        );

        assertThat(latestVersionsOfPlugins)
                .containsExactly(plugin("help-editor", "0.1-beta-2"));
    }

    @Test
    public void currentPluginIsExperimentalButGAVersionIsNewer() {
        List<Plugin> latestVersionsOfPlugins = pm.getLatestVersionsOfPlugins(singletonList(
                plugin("mailer", "1.31-beta-1"))
        );

        assertThat(latestVersionsOfPlugins)
                .containsExactly(plugin("mailer", "1.32.1"));
    }

    @Test
    public void newerExperimentalIsNotDowngradedToGA() {
        List<Plugin> latestVersionsOfPlugins = pm.getLatestVersionsOfPlugins(singletonList(
            plugin("mailer", "1.33-beta-1"))
        );

        assertThat(latestVersionsOfPlugins)
            .containsExactly(plugin("mailer", "1.33-beta-1"));
    }

    private Plugin plugin(String name, String version) {
        return new Plugin(name, version, null, null);
    }

    @SuppressWarnings("SameParameterValue")
    private Plugin pluginUrl(String name, String url) {
        return new Plugin(name, null, url, null);
    }

    @SuppressWarnings("SameParameterValue")
    private Plugin pluginIncremental(String name, String groupId, String version) {
        return new Plugin(name, version, null, groupId);
    }

}
