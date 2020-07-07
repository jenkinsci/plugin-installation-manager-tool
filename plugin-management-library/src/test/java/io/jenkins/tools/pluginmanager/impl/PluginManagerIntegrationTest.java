package io.jenkins.tools.pluginmanager.impl;

import hudson.util.VersionNumber;
import io.jenkins.tools.pluginmanager.config.Config;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * Tests for {@link PluginManager} which operate with real data.
 * No mocks here.
 */
public class PluginManagerIntegrationTest {

    static JSONObject latestUcJson;
    static File pluginVersionsFile;

    @ClassRule
    public static TemporaryFolder tmp = new TemporaryFolder();
    public static File jenkinsWar;
    public static File cacheDir;
    public static File pluginsDir;

    // TODO: Convert to a rule
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
        pluginManager.setPluginInfoJson(pluginManager.getJson(pluginVersionsFile.toURI().toURL(), "plugin-versions"));

        return pluginManager;
    }

    private static void unzipResource(Class clazz, String resourceName, String fileName, File target) throws IOException {
        final File archivePath = new File(tmp.getRoot(), target.toPath().getFileName() + ".zip");
        try (InputStream archive = clazz.getResourceAsStream(resourceName)) {
            if (archive == null) {
                throw new IOException("Cannot find " + resourceName + " in " + clazz);
            }
            Files.copy(archive, archivePath.toPath());
        }

        try (ZipFile zip = new ZipFile(archivePath)){
            ZipEntry entry = zip.getEntry(fileName);
            if (entry == null) {
                throw new IOException(String.format("Cannot find file %s within ZIP resource %s/%s", fileName, clazz.getName(), resourceName));
            }
            try(InputStream archive = zip.getInputStream(entry)) {
                Files.copy(archive, target.toPath());
            }
        }
    }

    @BeforeClass
    public static void setup() throws Exception {
        File updatesJSON = new File(tmp.getRoot(), "updates.json");
        unzipResource(PluginManagerIntegrationTest.class, "updates.zip", "updates.json", updatesJSON);
        try (InputStream istream = new FileInputStream(updatesJSON)) {
            latestUcJson = new JSONObject(IOUtils.toString(istream, StandardCharsets.UTF_8));
        }

        pluginVersionsFile = new File(tmp.getRoot(), "plugin-versions.json");
        unzipResource(PluginManagerIntegrationTest.class, "plugin-versions.zip", "plugin-versions.json", pluginVersionsFile);

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
    public void showAvailableUpdates_shouldNotFailOnUIThemes() throws Exception {
        Plugin pluginDockerCommons = new Plugin("docker-commons", "1.16", null, null);
        Plugin pluginYAD = new Plugin("yet-another-docker-plugin", "0.2.0", null, null);
        Plugin pluginIconShim = new Plugin("icon-shim", "2.0.3", null, null);
        PluginManager pluginManager = initPluginManager(
                configBuilder -> configBuilder.withPlugins(Arrays.asList(pluginDockerCommons, pluginIconShim, pluginYAD)));

        String output = tapSystemOut(
                () -> pluginManager.start(false));
        assertThat(output, not(containsString("uithemes")));
    }

    @Test
    public void findPluginsAndDependenciesTest() throws IOException {
        Plugin plugin1 = new Plugin("plugin1", "1.0", null, null);
        Plugin replaced = new Plugin("replaced", "1.0", null, null).withoutDependencies();
        Plugin plugin2 = new Plugin("plugin2", "2.0", null, null);
        Plugin plugin3 = new Plugin("plugin3", "3.0", null, null);

        Plugin plugin1Dependency1 = new Plugin("plugin1Dependency1", "1.0.1", null, null).withoutDependencies();
        Plugin plugin1Dependency2 = new Plugin("plugin1Dependency2", "1.0.2", null, null).withoutDependencies();
        Plugin replaced1 = new Plugin("replaced", "1.0.1", null, null).withoutDependencies();
        plugin1.setDependencies(Arrays.asList(plugin1Dependency1, plugin1Dependency2, replaced1));

        Plugin plugin2Dependency1 = new Plugin("plugin2Dependency1", "2.0.1", null, null).withoutDependencies();
        Plugin plugin2Dependency2 = new Plugin("plugin2Dependency2", "2.0.2", null, null).withoutDependencies();
        Plugin replaced2 = new Plugin("replaced", "2.0.2", null, null).withoutDependencies();
        Plugin replacedSecond = new Plugin("replaced2", "2.0.2", null, null).withoutDependencies();
        plugin2.setDependencies(Arrays.asList(plugin2Dependency1, plugin2Dependency2, replaced2, replacedSecond));

        Plugin plugin3Dependency1 = new Plugin("plugin3Dependency1", "3.0.1", null, null).withoutDependencies();
        Plugin replacedSecond2 = new Plugin("replaced2", "3.2", null, null).withoutDependencies();
        plugin3.setDependencies(Arrays.asList(plugin3Dependency1, replacedSecond2));

        // Expected
        List<Plugin> expectedPlugins = new ArrayList<>(Arrays.asList(plugin1, plugin1Dependency1, plugin1Dependency2,
                plugin2Dependency1, plugin2, plugin2Dependency2, plugin3, plugin3Dependency1, replaced2, replacedSecond2));
        Collections.sort(expectedPlugins);

        // Actual
        List<Plugin> requestedPlugins = new ArrayList<>(Arrays.asList(plugin1, plugin2, plugin3, replaced));
        PluginManager pluginManager = initPluginManager(
            configBuilder -> configBuilder.withPlugins(requestedPlugins));
        List<Plugin> pluginsAndDependencies = new ArrayList<>(pluginManager.findPluginsAndDependencies(requestedPlugins).values());
        Collections.sort(pluginsAndDependencies);

        assertEquals(expectedPlugins, pluginsAndDependencies);
    }
}
