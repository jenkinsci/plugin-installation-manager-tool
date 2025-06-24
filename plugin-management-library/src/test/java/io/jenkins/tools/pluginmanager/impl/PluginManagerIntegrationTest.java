package io.jenkins.tools.pluginmanager.impl;

import io.jenkins.tools.pluginmanager.config.Config;
import io.jenkins.tools.pluginmanager.util.ManifestTools;
import io.jenkins.tools.pluginmanager.util.PluginManagerUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut;
import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link PluginManager} which operate with real data.
 * No mocks here.
 */
class PluginManagerIntegrationTest {

    private static JSONObject latestUcJson;
    private static File pluginVersionsFile;

    @TempDir(cleanup = CleanupMode.NEVER)
    private static File classTmpDir;
    @TempDir(cleanup = CleanupMode.NEVER)
    private File tmpDir;

    private static File jenkinsWar;
    private static File cacheDir;
    private File pluginsDir;

    private interface Configurator {
        void configure(Config.Builder configBuilder);
    }

    private PluginManager initPluginManager(Configurator configurator) throws IOException {
        return initPluginManagerWithSecurityWarning(false, configurator);
    }

    private PluginManager initPluginManagerWithSecurityWarning(boolean showSecurityWarnings, Configurator configurator) throws IOException {
        Config.Builder configBuilder = Config.builder()
                .withJenkinsWar(jenkinsWar.getAbsolutePath())
                .withPluginDir(pluginsDir)
                .withIsVerbose(true)
                .withDoDownload(false)
                .withCachePath(cacheDir.toPath());
        if (showSecurityWarnings) {
            // if showing security warnings, do not show available updates
            configBuilder
                .withHideWarnings(false)
                .withShowWarnings(true)
                .withShowAvailableUpdates(false);
        } else {
            // if showing available updates, do not show security warnings
            configBuilder
                .withHideWarnings(true)
                .withShowWarnings(false)
                .withShowAvailableUpdates(true);
        }
        configurator.configure(configBuilder);
        Config config = configBuilder.build();

        PluginManager pluginManager = new PluginManager(config);
        pluginManager.setLatestUcJson(latestUcJson);
        pluginManager.setLatestUcPlugins(latestUcJson.getJSONObject("plugins"));
        pluginManager.setPluginInfoJson(pluginManager.getJson(pluginVersionsFile.toURI().toURL(), "plugin-versions"));

        return pluginManager;
    }

    private static void unzipResource(Class<?> clazz, String resourceName, String fileName, File target) throws IOException {
        final File archivePath = new File(classTmpDir, target.toPath().getFileName() + ".zip");
        try (InputStream archive = clazz.getResourceAsStream(resourceName)) {
            if (archive == null) {
                throw new IOException("Cannot find " + resourceName + " in " + clazz);
            }
            Files.copy(archive, archivePath.toPath());
        }

        try (ZipFile zip = new ZipFile(archivePath)) {
            ZipEntry entry = zip.getEntry(fileName);
            if (entry == null) {
                throw new IOException(String.format("Cannot find file %s within ZIP resource %s/%s", fileName, clazz.getName(), resourceName));
            }
            try (InputStream archive = zip.getInputStream(entry)) {
                Files.copy(archive, target.toPath());
            }
        }
    }

    @BeforeAll
    static void setupCaches() throws Exception {
        File updatesJSON = new File(classTmpDir, "updates.json");
        unzipResource(PluginManagerIntegrationTest.class, "updates.zip", "updates.json", updatesJSON);
        try (InputStream istream = new FileInputStream(updatesJSON)) {
            latestUcJson = new JSONObject(IOUtils.toString(istream, StandardCharsets.UTF_8));
        }

        pluginVersionsFile = new File(classTmpDir, "plugin-versions.json");
        unzipResource(PluginManagerIntegrationTest.class, "plugin-versions.zip", "plugin-versions.json", pluginVersionsFile);

        //TODO: Use real 2.222.1 war instead
        jenkinsWar = new File(classTmpDir, "jenkins.war");
        try (InputStream war = PluginManagerIntegrationTest.class.getResourceAsStream("/bundledplugintest.war")) {
            Files.copy(war, jenkinsWar.toPath());
        }

        cacheDir = newFolder(classTmpDir, "cache");
    }

    @BeforeEach
    void before() {
        pluginsDir = newFolder(tmpDir, "pluginsDir");
    }

    // https://github.com/jenkinsci/plugin-installation-manager-tool/issues/101
    @Test
    void showAvailableUpdates_shouldNotFailOnUIThemes() throws Exception {
        Plugin pluginDockerCommons = new Plugin("docker-commons", "1.16", null, null);
        Plugin pluginYAD = new Plugin("yet-another-docker-plugin", "0.2.0", null, null);
        Plugin pluginIconShim = new Plugin("icon-shim", "2.0.3", null, null);
        PluginManager pluginManager = initPluginManager(
                configBuilder -> configBuilder.withPlugins(Arrays.asList(pluginDockerCommons, pluginIconShim, pluginYAD)));

        String output = tapSystemOut(
                () -> pluginManager.start(false));
        assertThat(output).doesNotContain("uithemes");
    }

    @Test
    void securityWarningsShownByDefaultTest() throws Exception {
        Plugin pluginScriptSecurityWithSecurityWarning = new Plugin("script-security", "1.30", null, null);
        PluginManager pluginManager = initPluginManagerWithSecurityWarning(true,
                configBuilder -> configBuilder.withPlugins(
                    List.of(pluginScriptSecurityWithSecurityWarning)));

        String output = tapSystemErr(
                () -> pluginManager.start(false));
        assertThat(output).contains("Security warnings");
    }

    @Test
    void securityWarningsNotShownTest() throws Exception {
        Plugin pluginScriptSecurityWithSecurityWarning = new Plugin("script-security", "1.30", null, null);
        PluginManager pluginManager = initPluginManagerWithSecurityWarning(false,
                configBuilder -> configBuilder.withPlugins(
                    List.of(pluginScriptSecurityWithSecurityWarning)));

        String output = tapSystemErr(
                () -> pluginManager.start(false));
        assertThat(output).doesNotContain("Security warnings");
    }

    @Test
    void findPluginsAndDependenciesTest() throws IOException {
        Plugin plugin1 = new Plugin("plugin1", "1.0", null, null);
        Plugin plugin2 = new Plugin("plugin2", "2.0", null, null);
        Plugin plugin3 = new Plugin("plugin3", "3.0", null, null);

        Plugin plugin1Dependency1 = new Plugin("plugin1Dependency1", "1.0.1", null, null).withoutDependencies();
        Plugin plugin1Dependency2 = new Plugin("plugin1Dependency2", "1.0.2", null, null).withoutDependencies();
        plugin1.setDependencies(Arrays.asList(plugin1Dependency1, plugin1Dependency2));

        Plugin plugin2Dependency1 = new Plugin("plugin2Dependency1", "2.0.1", null, null).withoutDependencies();
        Plugin plugin2Dependency2 = new Plugin("plugin2Dependency2", "2.0.2", null, null).withoutDependencies();
        Plugin replaced2 = new Plugin("replaced", "2.0.2", null, null).withoutDependencies();
        Plugin replacedSecond = new Plugin("replaced2", "2.0.2", null, null).withoutDependencies();
        plugin2.setDependencies(Arrays.asList(plugin2Dependency1, plugin2Dependency2, replaced2, replacedSecond));

        Plugin plugin3Dependency1 = new Plugin("plugin3Dependency1", "3.0.1", null, null).withoutDependencies();
        Plugin replacedSecond2 = new Plugin("replaced2", "3.2", null, null).withoutDependencies();
        plugin3.setDependencies(Arrays.asList(plugin3Dependency1, replacedSecond2));

        // Actual
        List<Plugin> requestedPlugins = Arrays.asList(plugin1, plugin2, plugin3);
        PluginManager pluginManager = initPluginManager(
                configBuilder -> configBuilder.withPlugins(requestedPlugins));
        Map<String, Plugin> pluginsAndDependencies = pluginManager.findPluginsAndDependencies(requestedPlugins);

        assertThat(pluginsAndDependencies.values()).containsExactlyInAnyOrder(
                plugin1, plugin1Dependency1, plugin1Dependency2,
                plugin2Dependency1, plugin2, plugin2Dependency2,
                plugin3, plugin3Dependency1,
                replaced2, replacedSecond2);
    }

    @Test
    void failsIfTopLevelDeclaresNewVersionThanRequiredDependency() throws IOException {
        // given
        Plugin plugin1 = new Plugin("plugin1", "1.0", null, null);
        Plugin replaced = new Plugin("replaced", "1.0", null, null).withoutDependencies();

        Plugin replaced1 = new Plugin("replaced", "1.0.1", null, null).withoutDependencies();
        plugin1.setDependencies(Collections.singletonList(replaced1));

        List<Plugin> requestedPlugins = Arrays.asList(plugin1, replaced);

        // when
        PluginManager pluginManager = initPluginManager(configBuilder -> configBuilder.withPlugins(requestedPlugins));

        // then
        assertThatThrownBy(() -> pluginManager.findPluginsAndDependencies(requestedPlugins))
                .hasMessage("Plugin plugin1:1.0 depends on replaced:1.0.1, but there is an older version defined on the top level - replaced:1.0")
                .isInstanceOf(PluginDependencyException.class);
    }

    @Test
    void failsWithMultiplePrerequisitesNotMet() throws IOException {
        // given
        Plugin plugin1 = new Plugin("plugin1", "1.0", null, null);
        Plugin replaced = new Plugin("replaced", "1.0", null, null).withoutDependencies();
        Plugin plugin2 = new Plugin("plugin2", "1.0", null, null);

        Plugin replaced1 = new Plugin("replaced", "1.0.1", null, null).withoutDependencies();
        plugin1.setDependencies(Collections.singletonList(replaced1));
        plugin2.setDependencies(Collections.singletonList(replaced1));
        List<Plugin> requestedPlugins = Arrays.asList(plugin1, plugin2, replaced);

        // when
        PluginManager pluginManager = initPluginManager(configBuilder -> configBuilder.withPlugins(requestedPlugins));

        // then
        assertThatThrownBy(pluginManager::start)
                .hasMessage("""
                    Multiple plugin prerequisites not met:
                    Plugin plugin1:1.0 depends on replaced:1.0.1, but there is an older version defined on the top level - replaced:1.0,
                    Plugin plugin2:1.0 depends on replaced:1.0.1, but there is an older version defined on the top level - replaced:1.0""")
                .isInstanceOf(AggregatePluginPrerequisitesNotMetException.class);
    }

    @Test
    void allowsLatestTopLevelDependency() throws IOException {
        // given
        Plugin plugin1 = new Plugin("plugin1", "latest", null, null);
        Plugin replaced = new Plugin("replaced", "latest", null, null).withoutDependencies();

        Plugin replaced1 = new Plugin("replaced", "1.0", null, null).withoutDependencies();
        plugin1.setDependencies(Collections.singletonList(replaced1));

        List<Plugin> requestedPlugins = Arrays.asList(plugin1, replaced);

        // when
        PluginManager pluginManager = initPluginManager(configBuilder -> configBuilder.withPlugins(requestedPlugins));

        // then
        pluginManager.findPluginsAndDependencies(requestedPlugins);
        assertThatNoException();
    }

    @Test
    void latestAllPinnedPluginsIsLowerThanLatest() throws Exception {
        // given
        Plugin mailer = new Plugin("mailer", "1.34", null, null);
        Plugin pinnedDisplayUrlApi = new Plugin("display-url-api", "2.3.4", null, null);

        List<Plugin> requestedPlugins = Arrays.asList(mailer, pinnedDisplayUrlApi);

        // when
        PluginManager pluginManager = initPluginManager(
            configBuilder -> configBuilder.withPlugins(requestedPlugins).withUseLatestAll(true));

        // then
        Map<String, Plugin> pluginsAndDependencies = pluginManager.findPluginsAndDependencies(requestedPlugins);

        assertThat(pluginsAndDependencies.values()).containsExactlyInAnyOrder(
                mailer, pinnedDisplayUrlApi);
    }

    @Test
    void latestSpecifiedPinnedPluginsIsLowerThanLatest() throws Exception {
        // given
        Plugin testweaver = new Plugin("testweaver", "1.0.1", null, null);
        Plugin pinnedStructs = new Plugin("structs", "1.18", null, null);

        List<Plugin> requestedPlugins = Arrays.asList(testweaver, pinnedStructs);

        // when
        PluginManager pluginManager = initPluginManager(
            configBuilder -> configBuilder.withPlugins(requestedPlugins).withUseLatestSpecified(true));

        // then
        Map<String, Plugin> pluginsAndDependencies = pluginManager.findPluginsAndDependencies(requestedPlugins);

        assertThat(pluginsAndDependencies.values()).containsExactlyInAnyOrder(
                testweaver, pinnedStructs);
    }

    @Test
    void latestSpecifiedNoPinned() throws Exception {
        // given
        Plugin testweaver = new Plugin("testweaver", "latest", null, null);
        Plugin structs = new Plugin("structs", "1.7", null, null);

        List<Plugin> requestedPlugins = Collections.singletonList(testweaver);

        // when
        PluginManager pluginManager = initPluginManager(
            configBuilder -> configBuilder.withPlugins(requestedPlugins).withUseLatestSpecified(true));

        // then
        Map<String, Plugin> pluginsAndDependencies = pluginManager.findPluginsAndDependencies(requestedPlugins);

        assertThat(pluginsAndDependencies.values()).hasSameElementsAs(
                pluginManager.getLatestVersionsOfPlugins(Arrays.asList(testweaver, structs)));
    }

    @Test
    void verifyDownloads_smoke() throws Exception {
        // First cycle, empty dir
        Plugin initialTrileadAPI = new Plugin("trilead-api", "1.0.12", null, null);
        List<Plugin> requestedPlugins_1 = Collections.singletonList(initialTrileadAPI);
        PluginManager pluginManager = initPluginManager(
                configBuilder -> configBuilder.withPlugins(requestedPlugins_1).withDoDownload(true));
        pluginManager.start();
        assertPluginInstalled(initialTrileadAPI);

        // Second cycle, with plugin update and new plugin installation
        Plugin trileadAPI = new Plugin("trilead-api", "1.0.13", null, null);
        Plugin snakeYamlAPI = new Plugin("snakeyaml-api", "1.31-84.ve43da_fb_49d0b", null, null);
        List<Plugin> requestedPlugins_2 = Arrays.asList(trileadAPI, snakeYamlAPI);
        PluginManager pluginManager2 = initPluginManager(
                configBuilder -> configBuilder.withPlugins(requestedPlugins_2).withDoDownload(true));
        pluginManager2.start();

        // Ensure that the plugins are actually in place
        assertPluginInstalled(trileadAPI);
        assertPluginInstalled(snakeYamlAPI);
    }

    /**
     * Jenkins core supports passing plugins as unzipped {@code plugin.hpi} directories.
     * This test ensures this mode actually works.
     */
    @Test
    void verifyDownloads_toUnzipped() throws Exception {
        // First cycle, empty dir
        Plugin initialTrileadAPI = new Plugin("trilead-api", "1.0.12", null, null);
        List<Plugin> requestedPlugins_1 = Collections.singletonList(initialTrileadAPI);
        PluginManager pluginManager = initPluginManager(
                configBuilder -> configBuilder.withPlugins(requestedPlugins_1).withDoDownload(true));
        pluginManager.start();
        assertPluginInstalled(initialTrileadAPI);

        // Replace the plugin
        File pluginArchive = new File(pluginsDir, initialTrileadAPI.getArchiveFileName());
        File pluginDir = new File(pluginsDir, "trilead-api");
        PluginManagerUtils.explodePlugin(pluginArchive, pluginDir);
        Files.delete(pluginArchive.toPath());
        Files.move(pluginDir.toPath(), pluginArchive.toPath());
        assertPluginInstalled(initialTrileadAPI);

        // Second cycle, with plugin update and new plugin installation
        Plugin trileadAPI = new Plugin("trilead-api", "1.0.13", null, null);
        List<Plugin> requestedPlugins_2 = Collections.singletonList(trileadAPI);
        PluginManager pluginManager2 = initPluginManager(
                configBuilder -> configBuilder.withPlugins(requestedPlugins_2).withDoDownload(true));
        pluginManager2.start();

        // Ensure that the plugins are actually in place
        assertPluginInstalled(trileadAPI);
    }

    @Test
    void verifyBackups() throws Exception {
        // First cycle, empty dir
        Plugin initialTrileadAPI = new Plugin("trilead-api", "1.0.12", null, null);
        File pluginArchive = new File(pluginsDir, initialTrileadAPI.getArchiveFileName());
        File pluginBackup = new File(pluginsDir, initialTrileadAPI.getBackupFileName());
        List<Plugin> requestedPlugins_1 = Collections.singletonList(initialTrileadAPI);
        PluginManager pluginManager = initPluginManager(
                configBuilder -> configBuilder.withPlugins(requestedPlugins_1).withDoDownload(true));
        pluginManager.start();
        assertPluginInstalled(initialTrileadAPI);
        assertFalse(pluginBackup.exists());


        // Second cycle, with plugin update and new plugin installation
        Plugin trileadAPI = new Plugin("trilead-api", "1.0.13", null, null);
        List<Plugin> requestedPlugins_2 = Collections.singletonList(trileadAPI);
        PluginManager pluginManager2 = initPluginManager(
                configBuilder -> configBuilder.withPlugins(requestedPlugins_2).withDoDownload(true));
        pluginManager2.start();

        // Ensure that the plugins are actually in place and backup now exists
        assertPluginInstalled(trileadAPI);
        assertTrue(pluginBackup.exists());
    }

    @Test
    @Disabled("Enable as auto-test once it can run without big traffic overhead (15 plugin downloads)")
    void verifyDownloads_withDependencies() throws Exception {
        // First cycle, empty dir
        Plugin initialWorkflowJob = new Plugin("workflow-job", "2.39", null, null);
        List<Plugin> requestedPlugins_1 = Collections.singletonList(initialWorkflowJob);
        PluginManager pluginManager = initPluginManager(
                configBuilder -> configBuilder.withPlugins(requestedPlugins_1).withDoDownload(true));
        pluginManager.start();
        assertPluginInstalled(initialWorkflowJob);

        // Second cycle, with plugin update and new plugin installation
        Plugin workflowJob = new Plugin("workflow-job", "2.40", null, null);
        Plugin utilitySteps = new Plugin("pipeline-utility-steps", "2.6.1", null, null);
        List<Plugin> requestedPlugins_2 = Arrays.asList(workflowJob, utilitySteps);
        PluginManager pluginManager2 = initPluginManager(
                configBuilder -> configBuilder.withPlugins(requestedPlugins_2).withDoDownload(true));
        pluginManager2.start();

        // Ensure that the plugins are actually in place
        assertPluginInstalled(workflowJob);
        assertPluginInstalled(utilitySteps);
    }

    private void assertPluginInstalled(Plugin plugin) throws IOException, AssertionError {
        File pluginArchive = new File(pluginsDir, plugin.getArchiveFileName());

        assertTrue(pluginArchive.exists(), "Plugin is not installed: " + plugin);
        Plugin installed = ManifestTools.readPluginFromFile(pluginArchive);
        assertThat(installed.getVersion()).isEqualByComparingTo(plugin.getVersion());
    }

    private static File newFolder(File root, String... subDirs) {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        assertTrue(result.mkdirs(), "Couldn't create folders " + result);
        return result;
    }
}
