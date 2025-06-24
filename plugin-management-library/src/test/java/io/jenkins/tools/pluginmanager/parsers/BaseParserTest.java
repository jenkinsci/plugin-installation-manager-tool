package io.jenkins.tools.pluginmanager.parsers;

import io.jenkins.tools.pluginmanager.config.Config;
import io.jenkins.tools.pluginmanager.config.Settings;
import io.jenkins.tools.pluginmanager.impl.Plugin;
import io.jenkins.tools.pluginmanager.impl.PluginManager;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class BaseParserTest {

    protected PluginManager pm;

    @BeforeEach
    void before() throws Exception {
        pm = new PluginManager(Config.builder().withJenkinsWar(Settings.DEFAULT_WAR).build());
        JSONObject pluginInfoJson = loadPluginVersionsFromClassPath();
        pm.setLatestUcPlugins(pluginInfoJson.getJSONObject("plugins"));
        JSONObject experimentalPlugins = loadExperimentalPluginVersionsFromClassPath();
        pm.setExperimentalPlugins(experimentalPlugins.getJSONObject("plugins"));
    }

    @SuppressWarnings("SameParameterValue")
    protected Plugin plugin(String name, String version) {
        return new Plugin(name, version, null, null);
    }

    protected Plugin plugin(String name, String version, String groupId) {
        return new Plugin(name, version, null, groupId);
    }

    protected Plugin pluginWithUrl(String name, String url) {
        return new Plugin(name, null, url, null);
    }

    protected JSONObject loadPluginVersionsFromClassPath() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("../impl/available-updates/update-center.actual.json")) {
          assertNotNull(stream);
          return new JSONObject(IOUtils.toString(stream, StandardCharsets.UTF_8));
        }
    }

    private JSONObject loadExperimentalPluginVersionsFromClassPath() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("../impl/available-updates/update-center.experimental.json")) {
          assertNotNull(stream);
          return new JSONObject(IOUtils.toString(stream, StandardCharsets.UTF_8));
        }
    }
}
