package io.jenkins.tools.pluginmanager.impl;

import hudson.util.VersionNumber;
import io.jenkins.tools.pluginmanager.config.Config;
import io.jenkins.tools.pluginmanager.config.Settings;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.contrib.java.lang.system.SystemOutRule;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

/**
 * Tests for {@link PluginManager} which operate with real data.
 * No mocks here.
 */
public class PluginManagerIntegrationTest {

    static JSONObject latestUcJson;

    @ClassRule
    public static TemporaryFolder tmp = new TemporaryFolder();
    public static File jenkinsWar;
    public static File cacheDir;
    public static File pluginsDir;

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog();

    public interface Configurator {
        void configure(Config.Builder configBuilder);
    }

    public static PluginManager initPluginManager(Configurator configurator) throws IOException {
        Config.Builder configBuilder = Config.builder()
                .withJenkinsWar(jenkinsWar.getAbsolutePath())
                .withPluginDir(pluginsDir)
                .withShowAvailableUpdates(true)
                .withIsVerbose(true)
                .withDoDownload(false);
        configurator.configure(configBuilder);
        Config config = configBuilder.build();

        PluginManager pluginManager = new PluginManager(config);
        pluginManager.setCm(new CacheManager(cacheDir.toPath(), true));
        pluginManager.setJenkinsVersion(new VersionNumber("2.222.1"));
        pluginManager.setLatestUcJson(latestUcJson);
        pluginManager.setLatestUcPlugins(latestUcJson.getJSONObject("plugins"));
        pluginManager.setPluginInfoJson(pluginManager.getJson(new URL(Settings.DEFAULT_PLUGIN_INFO_LOCATION), "plugin-versions"));

        return pluginManager;
    }

    @BeforeClass
    public static void setup() throws Exception {
        String jsonString = IOUtils.toString(PluginManagerIntegrationTest.class.getResource("updates.json"), StandardCharsets.UTF_8);
        latestUcJson = new JSONObject(jsonString);
        //TODO: Use real 2.222.1 war instead
        jenkinsWar = new File(tmp.getRoot(), "jenkins.war");
        try(InputStream war = PluginManagerIntegrationTest.class.getResourceAsStream("/bundledplugintest.war")) {
            Files.copy(war, jenkinsWar.toPath());
        }

        cacheDir = tmp.newFolder("cache");
        pluginsDir = tmp.newFolder("pluginsDir");
    }

    // https://github.com/jenkinsci/plugin-installation-manager-tool/issues/101
    @Test
    public void showAvailableUpdates_shouldNotFailOnUIThemes() throws IOException {
        Plugin pluginDockerCommons = new Plugin("docker-commons", "1.16", null, null);
        Plugin pluginYAD = new Plugin("yet-another-docker-plugin", "0.2.0", null, null);
        Plugin pluginIconShim = new Plugin("icon-shim", "2.0.3", null, null);
        PluginManager pluginManager = initPluginManager(
                configBuilder -> configBuilder.withPlugins(Arrays.asList(pluginDockerCommons, pluginIconShim, pluginYAD)));

        pluginManager.start(false);
        assertThat(systemOutRule.getLog(), not(containsString("uithemes")));
    }
}
