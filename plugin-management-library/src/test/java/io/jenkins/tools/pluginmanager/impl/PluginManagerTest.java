package io.jenkins.tools.pluginmanager.impl;

import hudson.util.VersionNumber;
import io.jenkins.tools.pluginmanager.config.Config;
import io.jenkins.tools.pluginmanager.config.Settings;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOutNormalized;
import static io.jenkins.tools.pluginmanager.util.PluginManagerUtils.dirName;
import static java.nio.file.Files.createDirectory;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.whenNew;

@RunWith(PowerMockRunner.class)
@PrepareForTest({HttpClients.class, PluginManager.class, HttpClientContext.class, URIUtils.class, HttpHost.class,
        URI.class, FileUtils.class, Files.class, HttpClientBuilder.class})
@PowerMockIgnore({"javax.net.ssl.*","javax.security.*", "javax.net.*"})
public class PluginManagerTest {
    private PluginManager pm;
    private Config cfg;
    private List<Plugin> directDependencyExpectedPlugins;

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Before
    public void setUp() {
        cfg = Config.builder()
            .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(new File(folder.getRoot(), "plugins"))
                .build();

        pm = new PluginManager(cfg);

        directDependencyExpectedPlugins = Arrays.asList(
            new Plugin("workflow-api", "2.22", null, null),
            new Plugin("workflow-step-api", "2.12", null, null),
            new Plugin("mailer", "1.18", null, null),
            new Plugin("script-security", "1.30", null, null)
        );
    }

    @Test
    public void startTest() {
        Config config = Config.builder()
                .withPluginDir(new File(folder.getRoot(), "plugins"))
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withJenkinsUc(Settings.DEFAULT_UPDATE_CENTER)
                .withPlugins(new ArrayList<>())
                .withDoDownload(true)
                .withJenkinsUcExperimental(Settings.DEFAULT_EXPERIMENTAL_UPDATE_CENTER)
                .build();

        PluginManager pluginManager = new PluginManager(config);

        PluginManager pluginManagerSpy = spy(pluginManager);

        doNothing().when(pluginManagerSpy).createRefDir();
        doReturn(new VersionNumber("2.182")).when(pluginManagerSpy).getJenkinsVersionFromWar();
        doNothing().when(pluginManagerSpy).checkAndSetLatestUpdateCenter();
        doNothing().when(pluginManagerSpy).getUCJson();
        doReturn(new HashMap<>()).when(pluginManagerSpy).getSecurityWarnings();
        doNothing().when(pluginManagerSpy).showAllSecurityWarnings();

        doReturn(new HashMap<>()).when(pluginManagerSpy).bundledPlugins();
        doReturn(new HashMap<>()).when(pluginManagerSpy).installedPlugins();
        doReturn(new HashMap<>()).when(pluginManagerSpy).findPluginsAndDependencies(anyList());
        doReturn(new ArrayList<>()).when(pluginManagerSpy).findPluginsToDownload(anyMap());
        doReturn(new HashMap<>()).when(pluginManagerSpy).findEffectivePlugins(anyList());
        doNothing().when(pluginManagerSpy).listPlugins();
        doNothing().when(pluginManagerSpy).showSpecificSecurityWarnings(anyList());
        doNothing().when(pluginManagerSpy).showAvailableUpdates(anyList());
        doNothing().when(pluginManagerSpy).checkVersionCompatibility(anyList());
        doNothing().when(pluginManagerSpy).downloadPlugins(anyList());

        pluginManagerSpy.start();
    }

    @Test
    public void startNoDirectoryTest() throws IOException {
        // by using a file as the parent dir of the plugin folder we force that
        // the plugin folder cannot be created
        File pluginParentDir = folder.newFile();
        Config config = Config.builder()
                .withPluginDir(new File(pluginParentDir, "plugins"))
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withJenkinsUc(Settings.DEFAULT_UPDATE_CENTER)
                .build();

        PluginManager pluginManager = new PluginManager(config);

        assertThatThrownBy(pluginManager::start)
                .isInstanceOf(DirectoryCreationException.class);
    }

    @Test
    public void findEffectivePluginsTest() {
        Map<String, Plugin> bundledPlugins = new HashMap<>();
        Map<String, Plugin> installedPlugins = new HashMap<>();
        bundledPlugins.put("git", new Plugin("git", "1.2", null, null));
        bundledPlugins.put("aws-credentials", new Plugin("aws-credentials", "1.24", null, null));
        bundledPlugins.put("p4", new Plugin("p4", "1.3.3", null, null));
        installedPlugins.put("script-security", new Plugin("script-security", "1.26", null, null));
        installedPlugins.put("credentials", new Plugin("credentials", "2.1.11", null, null));
        installedPlugins.put("ace-editor", new Plugin("ace-editor", "1.0.1", null, null));
        installedPlugins.put("p4", new Plugin("p4", "1.3.0", null, null));

        pm.setBundledPluginVersions(bundledPlugins);
        pm.setInstalledPluginVersions(installedPlugins);

        List<Plugin> requestedPlugins = Arrays.asList(
                new Plugin("git", "1.3", null, null),
                new Plugin("script-security", "1.25", null, null),
                new Plugin("scm-api", "2.2.3", null, null));

        Map<String, Plugin> effectivePlugins = pm.findEffectivePlugins(requestedPlugins);

        assertThat(effectivePlugins.get("git").getVersion())
                .hasToString("1.3");
        assertThat(effectivePlugins.get("scm-api").getVersion())
                .hasToString("2.2.3");
        assertThat(effectivePlugins.get("aws-credentials").getVersion())
                .hasToString("1.24");
        assertThat(effectivePlugins.get("p4").getVersion())
                .hasToString("1.3.3");
        assertThat(effectivePlugins.get("script-security").getVersion())
                .hasToString("1.26");
        assertThat(effectivePlugins.get("credentials").getVersion())
                .hasToString("2.1.11");
        assertThat(effectivePlugins.get("ace-editor").getVersion())
                .hasToString("1.0.1");
    }

    @Test
    public void listPluginsNoOutputTest() throws Exception {
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(new File(folder.getRoot(), "plugins"))
                .withShowPluginsToBeDownloaded(false)
                .build();

        PluginManager pluginManager = new PluginManager(config);

        String output = tapSystemOutNormalized(
                pluginManager::listPlugins);

        assertThat(output).isEmpty();
    }



    @Test
    public void listPluginsOutputTest() throws Exception {
         Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(new File(folder.getRoot(), "plugins"))
                .withShowPluginsToBeDownloaded(true)
                .build();

        PluginManager pluginManager = new PluginManager(config);

        Map<String, Plugin> installedPluginVersions = new HashMap<>();
        Map<String, Plugin> bundledPluginVersions = new HashMap<>();
        Map<String, Plugin> allPluginsAndDependencies = new HashMap<>();
        HashMap<String, Plugin> effectivePlugins = new HashMap<>();

        installedPluginVersions.put("installed1", new Plugin("installed1", "1.0", null, null));
        installedPluginVersions.put("installed2", new Plugin("installed2", "2.0", null, null));

        bundledPluginVersions.put("bundled1", new Plugin("bundled1", "1.0", null, null));
        bundledPluginVersions.put("bundled2", new Plugin("bundled2", "2.0", null, null));

        Plugin plugin1 = new Plugin("plugin1", "1.0", null, null);
        Plugin plugin2 = new Plugin("plugin2", "2.0", null, null);
        Plugin dependency1 = new Plugin("dependency1", "1.0.0", null, null);
        Plugin dependency2 = new Plugin("dependency2", "1.0.0", null, null);

        allPluginsAndDependencies.put("plugin1", plugin1);
        allPluginsAndDependencies.put("plugin2", plugin2);
        allPluginsAndDependencies.put("dependency1", dependency1);
        allPluginsAndDependencies.put("dependency2", dependency2);

        Plugin installed1 = new Plugin("installed1", "1.0", null, null);
        Plugin installed2 = new Plugin("installed2", "2.0", null, null);
        Plugin bundled1 = new Plugin("bundled1", "1.0", null, null);
        Plugin bundled2 = new Plugin("bundled2", "2.0", null, null);

        effectivePlugins.put("installed1", installed1);
        effectivePlugins.put("installed2", installed2);
        effectivePlugins.put("bundled1", bundled1);
        effectivePlugins.put("bundled2", bundled2);
        effectivePlugins.put("plugin1", plugin1);
        effectivePlugins.put("plugin2", plugin2);
        effectivePlugins.put("dependency1", dependency1);
        effectivePlugins.put("dependency2", dependency2);

        pluginManager.setInstalledPluginVersions(installedPluginVersions);
        pluginManager.setBundledPluginVersions(bundledPluginVersions);
        pluginManager.setAllPluginsAndDependencies(allPluginsAndDependencies);
        pluginManager.setPluginsToBeDownloaded(
                Arrays.asList(plugin1, plugin2, dependency1, dependency2));
        pluginManager.setEffectivePlugins(effectivePlugins);

        String output = tapSystemOutNormalized(
                pluginManager::listPlugins);

        assertThat(output).isEqualTo(
                "\nInstalled plugins:\n" +
                        "installed1 1.0\n" +
                        "installed2 2.0\n" +
                        "\nBundled plugins:\n" +
                        "bundled1 1.0\n" +
                        "bundled2 2.0\n" +
                        "\nSet of all requested plugins:\n" +
                        "dependency1 1.0.0\n" +
                        "dependency2 1.0.0\n" +
                        "plugin1 1.0\n" +
                        "plugin2 2.0\n" +
                        "\nSet of all requested plugins that will be downloaded:\n" +
                        "dependency1 1.0.0\n" +
                        "dependency2 1.0.0\n" +
                        "plugin1 1.0\n" +
                        "plugin2 2.0\n" +
                        "\nSet of all existing plugins and plugins that will be downloaded:\n" +
                        "bundled1 1.0\n" +
                        "bundled2 2.0\n" +
                        "dependency1 1.0.0\n" +
                        "dependency2 1.0.0\n" +
                        "installed1 1.0\n" +
                        "installed2 2.0\n" +
                        "plugin1 1.0\n" +
                        "plugin2 2.0\n");
    }

    @Test
    public void getPluginDependencyJsonArrayTest1() {
        //test for update center or experimental json
        JSONObject updateCenterJson = new JSONObject();
        JSONObject plugins = new JSONObject();

        JSONObject awsCodebuild = new JSONObject();
        awsCodebuild.put("buildDate", "Jun 20, 2019");
        awsCodebuild.put("version", "0.42");
        awsCodebuild.put("requiredCore", "1.642,3");

        JSONArray awsCodebuildDependencies = array(
                dependency("workflow-step-api", false, "2.5"),
                dependency("cloudbees-folder", false, "6.1.0"),
                dependency("credentials", false, "2.1.14"),
                dependency("script-security", false, "1.29"));
        awsCodebuild.put("dependencies", awsCodebuildDependencies);

        JSONObject awsGlobalConfig = new JSONObject();
        awsGlobalConfig.put("buildDate", "Mar 27, 2019");
        awsGlobalConfig.put("version", "1.3");
        awsGlobalConfig.put("requiredCore", "2.121.1");

        JSONArray awsGlobalConfigDependencies = array(
                dependency("aws-credentials", false, "1.23"),
                dependency("structs", false, "1.14"));
        awsGlobalConfig.put("dependencies", awsGlobalConfigDependencies);

        plugins.put("aws-global-configuration", awsGlobalConfig);
        plugins.put("aws-codebuild", awsCodebuild);

        updateCenterJson.put("plugins", plugins);

        Plugin awsCodeBuildPlugin = new Plugin("aws-codebuild", "1.2", null , null);
        Plugin awsGlobalConfigPlugin = new Plugin("aws-global-configuration", "1.1", null, null);
        Plugin gitPlugin = new Plugin("structs", "1.17", null, null);
        JSONArray gitJson = pm.getPluginDependencyJsonArray(gitPlugin, updateCenterJson);

        assertThat(gitJson).isNull();

        JSONArray awsCodeBuildJson = pm.getPluginDependencyJsonArray(awsCodeBuildPlugin, updateCenterJson);
        assertThat(awsCodeBuildJson).hasToString(awsCodebuildDependencies.toString());

        JSONArray awsGlobalConfJson = pm.getPluginDependencyJsonArray(awsGlobalConfigPlugin, updateCenterJson);
        assertThat(awsGlobalConfJson).hasToString(awsGlobalConfigDependencies.toString());
    }

    @Test
    public void getPluginDependencyJsonArrayTest2() {
        //test plugin-version json, which has a different format
        JSONObject pluginVersionJson = new JSONObject();
        JSONObject plugins = new JSONObject();

        JSONObject browserStackIntegration= new JSONObject();
        JSONObject browserStack1 = new JSONObject();
        browserStack1.put("requiredCore", "1.580.1");

        JSONArray dependencies1 = array(
                dependency("credentials", false, "1.8"),
                dependency("junit", false, "1.10"));
        browserStack1.put("dependencies", dependencies1);
        browserStackIntegration.put("1.0.0", browserStack1);

        JSONObject browserStack111 = new JSONObject();
        browserStack111.put("requiredCore", "1.580.1");

        JSONArray dependencies111 = array(
                dependency("credentials", false, "1.8"),
                dependency("junit", false, "1.10"));
        browserStack111.put("dependencies", dependencies111);
        browserStackIntegration.put("1.1.1", browserStack111);

        JSONObject browserStack112 = new JSONObject();
        browserStack112.put("requiredCore", "1.653");
        JSONArray dependencies112 = array(
                dependency("credentials", false, "2.1.17"),
                dependency("junit", false, "1.10"),
                dependency("workflow-api", false, "2.6"),
                dependency("workflow-basic-steps", false, "2.6"),
                dependency("workflow-cps", false, "2.39"));
        browserStack112.put("dependencies", dependencies112);
        browserStackIntegration.put("1.1.2", browserStack112);

        plugins.put("browserstack-integration", browserStackIntegration);

        pluginVersionJson.put("plugins", plugins);

        Plugin gitPlugin = new Plugin("git", "1.17", null, null);
        JSONArray gitJson = pm.getPluginDependencyJsonArray(gitPlugin, pluginVersionJson);

        assertThat(gitJson).isNull();

        pm.setPluginInfoJson(pluginVersionJson);

        Plugin browserStackPlugin1 = new Plugin("browserstack-integration", "1.0.0", null, null);
        JSONArray browserStackPluginJson1 = pm.getPluginDependencyJsonArray(browserStackPlugin1, pluginVersionJson);
        assertThat(browserStackPluginJson1)
                .hasToString(dependencies1.toString());

        Plugin browserStackPlugin111 = new Plugin("browserstack-integration", "1.1.1", null, null);
        JSONArray browserStackPluginJson111 = pm.getPluginDependencyJsonArray(browserStackPlugin111, pluginVersionJson);
        assertThat(browserStackPluginJson111)
                .hasToString(dependencies111.toString());

        Plugin browserStackPlugin112 = new Plugin("browserstack-integration", "1.1.2", null, null);
        JSONArray browserStackPluginJson112 = pm.getPluginDependencyJsonArray(browserStackPlugin112, pluginVersionJson);
        assertThat(browserStackPluginJson112)
                .hasToString(dependencies112.toString());
    }

    @Test
    public void getSecurityWarningsTest() {
        setTestUcJson();

        Map<String, List<SecurityWarning>> allSecurityWarnings = pm.getSecurityWarnings();

        assertThat(allSecurityWarnings)
                .doesNotContainKey("core")
                .hasSize(3);

        SecurityWarning googleLoginSecurityWarning = allSecurityWarnings.get("google-login").get(0);
        assertThat(googleLoginSecurityWarning.getId()).isEqualTo("SECURITY-208");
        assertThat(googleLoginSecurityWarning.getSecurityVersions().get(0).getPattern())
                .hasToString("1[.][01](|[.-].*)");

        List<SecurityWarning> pipelineMavenSecurityWarning = allSecurityWarnings.get("pipeline-maven");

        assertThat(pipelineMavenSecurityWarning)
                .extracting(warning -> warning.getId() + " " + warning.getMessage() + " " + warning.getUrl())
                .containsExactlyInAnyOrder(
                        "SECURITY-441 Arbitrary files from Jenkins master available in Pipeline by using the " +
                                "withMaven step https://jenkins.io/security/advisory/2017-03-09/",
                        "SECURITY-1409 XML External Entity processing vulnerability " +
                                "https://jenkins.io/security/advisory/2019-05-31/#SECURITY-1409");

        assertThat(pipelineMavenSecurityWarning.get(0).getSecurityVersions())
                .hasSize(2);
    }

    @Test
    public void getSecurityWarningsWhenNoWarningsTest() {
        JSONObject json = setTestUcJson();
        json.remove("warnings");
        assertThat(json.has("warnings")).isFalse();

        Map<String, List<SecurityWarning>> allSecurityWarnings = pm.getSecurityWarnings();

        assertThat(allSecurityWarnings).isEmpty();
    }

    @Test
    public void warningExistsTest() {
        Map<String, List<SecurityWarning>> securityWarnings = new HashMap<>();
        SecurityWarning scriptlerWarning = new SecurityWarning("SECURITY", "security warning",
                "scriptler", "url");
        scriptlerWarning.addSecurityVersion("", "",".*");
        securityWarnings.put("scriptler", singletonList(scriptlerWarning));

        SecurityWarning lockableResourceWarning1 = new SecurityWarning("SECURITY", "security warning1",
                "lockable-resources", "url");
        lockableResourceWarning1.addSecurityVersion("", "1.59", "1[.][0-9](|[.-].*)|1[.][12345][0-9](|[.-].*)");
        SecurityWarning lockableResourceWarning2 = new SecurityWarning("SECURITY", "security warning2",
                "lockable-resources", "url");
        lockableResourceWarning2.addSecurityVersion("", "2.2", "1[.].*|2[.][012](|[.-].*)");

        securityWarnings.put("lockable-resources",
                Arrays.asList(lockableResourceWarning1, lockableResourceWarning2));

        SecurityWarning cucumberReports = new SecurityWarning("SECURITY", "security warning", "cucumber-reports",
                "url");
        cucumberReports.addSecurityVersion("1.3.0", "2.5.1", "(1[.][34]|2[.][012345])(|[.-].*)");

        securityWarnings.put("cucumber-reports", singletonList(cucumberReports));

        SecurityWarning sshAgentsWarning = new SecurityWarning("SECURITY", "security warning", "ssh-slaves", "url");
        sshAgentsWarning.addSecurityVersion("", "1.14", "0[.].*|1[.][0-9](|[.-].*)|1[.]1[01234](|[.-].*)");

        securityWarnings.put("ssh-slaves", singletonList(sshAgentsWarning));

        pm.setAllSecurityWarnings(securityWarnings);

        Plugin scriptler = new Plugin("scriptler", "1.2", null, null);
        Plugin lockableResource = new Plugin("lockable-resources", "1.60", null, null);
        Plugin lockableResource2 = new Plugin("lockable-resources", "2.3.0", null, null);
        Plugin cucumberReports1 = new Plugin("cucumber-reports", "1.2.1", null, null);
        Plugin cucumberReports2 = new Plugin("cucumber-reports", "1.4.1", null, null);
        Plugin cucumberReports3 = new Plugin("cucumber-reports", "2.5.3", null, null);
        Plugin sshAgents1 = new Plugin("ssh-slaves", "0.9", null, null);
        Plugin sshAgents2 = new Plugin("ssh-slaves", "9.2", null, null);

        assertThat(pm.warningExists(scriptler)).isTrue();
        assertThat(pm.warningExists(lockableResource)).isTrue();
        assertThat(pm.warningExists(lockableResource2)).isFalse();
        assertThat(pm.warningExists(cucumberReports1)).isFalse();
        assertThat(pm.warningExists(cucumberReports2)).isTrue();
        //assertThat(pm.warningExists(cucumberReports3)).isFalse();
        // currently fails since 2.5.3 matches pattern even though 2.5.1 is last effected version
        assertThat(pm.warningExists(sshAgents1)).isTrue();
        assertThat(pm.warningExists(sshAgents2)).isFalse();
    }

    @Test
    public void checkVersionCompatibilityNullTest() throws Exception {
        pm.setJenkinsVersion(null);

        Plugin plugin1 = new Plugin("plugin1", "1.0", null, null);
        plugin1.setJenkinsVersion("2.121.2");

        Plugin plugin2 = new Plugin("plugin2", "2.1.1", null, null);
        plugin2.setJenkinsVersion("1.609.3");

        //check passes if no exception is thrown
        pm.checkVersionCompatibility(Arrays.asList(plugin1, plugin2));
    }

    @Test
    public void checkVersionCompatibilityFailTest() {
        pm.setJenkinsVersion(new VersionNumber("1.609.3"));

        Plugin plugin1 = new Plugin("plugin1", "1.0", null, null);
        plugin1.setJenkinsVersion("2.121.2");

        Plugin plugin2 = new Plugin("plugin2", "2.1.1", null, null);
        plugin2.setJenkinsVersion("1.609.3");

        List<Plugin> pluginsToDownload = new ArrayList<>(Arrays.asList(plugin1, plugin2));

        assertThatThrownBy(() -> pm.checkVersionCompatibility(pluginsToDownload))
                .isInstanceOf(VersionCompatibilityException.class);
    }

    @Test
    public void checkVersionCompatibilityPassTest() {
        pm.setJenkinsVersion(new VersionNumber("2.121.2"));

        Plugin plugin1 = new Plugin("plugin1", "1.0", null, null);
        plugin1.setJenkinsVersion("2.121.2");

        Plugin plugin2 = new Plugin("plugin2", "2.1.1", null, null);
        plugin2.setJenkinsVersion("1.609.3");

        //check passes if no exception is thrown
        pm.checkVersionCompatibility(Arrays.asList(plugin1, plugin2));
    }

    @Test
    public void showAvailableUpdatesNoOutputTest() throws Exception {
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(new File(folder.getRoot(), "plugins"))
                .withShowAvailableUpdates(false)
                .build();

        PluginManager pluginManager = new PluginManager(config);


        List<Plugin> plugins = Arrays.asList(
                new Plugin("ant", "1.8", null, null),
                new Plugin("amazon-ecs", "1.15", null, null));

        String output = tapSystemOutNormalized(
                () -> pluginManager.showAvailableUpdates(plugins));

        assertThat(output).isEmpty();
    }

    @Test
    public void showAvailableUpdates() throws Exception {
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(new File(folder.getRoot(), "plugins"))
                .withShowAvailableUpdates(true)
                .build();

        PluginManager pluginManager = new PluginManager(config);
        PluginManager pluginManagerSpy = spy(pluginManager);

        List<Plugin> plugins = Arrays.asList(
                new Plugin("ant", "1.8", null, null),
                new Plugin("amazon-ecs", "1.15", null, null),
                new Plugin("maven-invoker-plugin", "2.4", null, null ));

        doReturn(new VersionNumber("1.9")).when(pluginManagerSpy).getLatestPluginVersion("ant");
        doReturn(new VersionNumber("1.20")).when(pluginManagerSpy).getLatestPluginVersion("amazon-ecs");
        doReturn(new VersionNumber("2.4")).when(pluginManagerSpy).getLatestPluginVersion("maven-invoker-plugin");

        String output = tapSystemOutNormalized(
                () -> pluginManagerSpy.showAvailableUpdates(plugins));

        assertThat(output).isEqualTo(
                "\nAvailable updates:\n"
                        + "ant (1.8) has an available update: 1.9\n"
                        + "amazon-ecs (1.15) has an available update: 1.20\n");
    }

    @Test
    public void downloadPluginsSuccessfulTest() {
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(new File(folder.getRoot(), "plugins"))
                .build();

        PluginManager pluginManager = new PluginManager(config);
        PluginManager pluginManagerSpy = spy(pluginManager);

        doReturn(true).when(pluginManagerSpy).downloadPlugin(any(Plugin.class), nullable(File.class));

        List<Plugin> plugins = singletonList(
                new Plugin("plugin", "1.0", null, null));

        pluginManagerSpy.downloadPlugins(plugins);

        assertThat(pluginManagerSpy.getFailedPlugins()).isEmpty();
    }

    @Test
    public void downloadPluginsUnsuccessfulTest() {
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(new File(folder.getRoot(), "plugins"))
                .build();

        PluginManager pluginManager = new PluginManager(config);
        PluginManager pluginManagerSpy = spy(pluginManager);

        doReturn(false).when(pluginManagerSpy).downloadPlugin(any(Plugin.class), nullable(File.class));

        List<Plugin> plugins = singletonList(
                new Plugin("plugin", "1.0", null, null));

        assertThatThrownBy(() -> pluginManagerSpy.downloadPlugins(plugins))
                .isInstanceOf(DownloadPluginException.class);
    }

    @Test
    public void downloadPluginAlreadyInstalledTest() {
        Map<String, Plugin> installedVersions = new HashMap<>();
        installedVersions.put("plugin1", new Plugin("plugin1", "1.0", null, null));
        installedVersions.put("plugin2", new Plugin("plugin2", "2.0", null, null));

        pm.setInstalledPluginVersions(installedVersions);

        assertThat(pm.downloadPlugin(new Plugin("plugin1", "1.0", null, null), null))
                .isTrue();
    }

    @Test
    public void downloadPluginSuccessfulFirstAttempt() {
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(new File(folder.getRoot(), "plugins"))
                .build();

        PluginManager pluginManager = new PluginManager(config);
        PluginManager pluginManagerSpy = spy(pluginManager);

        Map<String, Plugin> installedVersions = new HashMap<>();
        installedVersions.put("plugin1", new Plugin("plugin1", "1.0", null, null));
        installedVersions.put("plugin2", new Plugin("plugin2", "2.0", null, null));

        pluginManagerSpy.setInstalledPluginVersions(installedVersions);

        Plugin pluginToDownload = new Plugin("plugin", "0.2", null, null);

        doReturn("url").when(pluginManagerSpy).getPluginDownloadUrl(pluginToDownload);
        doReturn(true).when(pluginManagerSpy).downloadToFile("url", pluginToDownload, null);

        assertThat(pluginManagerSpy.downloadPlugin(pluginToDownload, null)).isTrue();
        assertThat(pluginToDownload.getName()).isEqualTo("plugin");
        assertThat(pluginToDownload.getOriginalName()).isEqualTo("plugin");
    }

    @Test
    public void checkVersionSpecificUpdateCenterTest() throws Exception {
        //Test where version specific update center exists
        pm.setJenkinsVersion(new VersionNumber("2.176"));

        mockStatic(HttpClients.class);
        CloseableHttpClient httpclient = mock(CloseableHttpClient.class);

        when(HttpClients.createSystem()).thenReturn(httpclient);
        HttpHead httphead = mock(HttpHead.class);

        whenNew(HttpHead.class).withAnyArguments().thenReturn(httphead);
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        when(httpclient.execute(httphead)).thenReturn(response);

        StatusLine statusLine = mock(StatusLine.class);
        when(response.getStatusLine()).thenReturn(statusLine);

        int statusCode = HttpStatus.SC_OK;
        when(statusLine.getStatusCode()).thenReturn(statusCode);

        pm.checkAndSetLatestUpdateCenter();

        String expected = dirName(cfg.getJenkinsUc()) + pm.getJenkinsVersion() + Settings.DEFAULT_UPDATE_CENTER_FILENAME;
        assertThat(pm.getJenkinsUCLatest()).isEqualTo(expected);
    }

    @Test
    public void outputPluginReplacementInfoTest() throws Exception {
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(new File(folder.getRoot(), "plugins"))
                .withIsVerbose(true)
                .build();

        PluginManager pluginManager = new PluginManager(config);
        Plugin lowerVersion = new Plugin("plugin1", "1.0", null, null);
        Plugin lowerVersionParent = new Plugin("plugin1parent1", "1.0.0", null, null);
        Plugin higherVersion = new Plugin("plugin1", "2.0", null, null);
        Plugin highVersionParent = new Plugin("plugin1parent2", "2.0.0", null, null);
        lowerVersion.setParent(lowerVersionParent);
        higherVersion.setParent(highVersionParent);

        String output = tapSystemOutNormalized(
                () -> pluginManager.outputPluginReplacementInfo(lowerVersion, higherVersion));

        assertThat(output).isEqualTo(
                "Version of plugin1 (1.0) required by plugin1parent1 (1.0.0) is lower than the version " +
                        "required (2.0) by plugin1parent2 (2.0.0), upgrading required plugin version\n");
    }

    @Test
    public void deprecatedGetJsonThrowsExceptionForMalformedURL() {
        assertThatThrownBy(() -> pm.getJson("htttp://ftp-chi.osuosl.org/pub/jenkins/updates/current/update-center.json"))
                .isInstanceOf(UpdateCenterInfoRetrievalException.class)
                .hasMessage("Malformed url for update center");
    }

    @Test
    public void getJsonThrowsExceptionWhenUrlDoesNotExists() throws IOException{
        pm.setCm(new CacheManager(folder.newFolder().toPath(), false));

        assertThatThrownBy(() -> pm.getJson(new File("does/not/exist").toURI().toURL(), "update-center"))
                .isInstanceOf(UpdateCenterInfoRetrievalException.class)
                .hasMessage("Error getting update center json");
    }

    @Test
    public void checkVersionSpecificUpdateCenterBadRequestTest() throws Exception {
        pm.setJenkinsVersion(new VersionNumber("2.176"));

        mockStatic(HttpClients.class);
        CloseableHttpClient httpclient = mock(CloseableHttpClient.class);

        when(HttpClients.createSystem()).thenReturn(httpclient);
        HttpHead httphead = mock(HttpHead.class);

        whenNew(HttpHead.class).withAnyArguments().thenReturn(httphead);
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        when(httpclient.execute(httphead)).thenReturn(response);

        StatusLine statusLine = mock(StatusLine.class);
        when(response.getStatusLine()).thenReturn(statusLine);

        int statusCode = HttpStatus.SC_BAD_REQUEST;
        when(statusLine.getStatusCode()).thenReturn(statusCode);

        pm.checkAndSetLatestUpdateCenter();
        String expected = cfg.getJenkinsUc().toString();
        assertThat(pm.getJenkinsUCLatest()).isEqualTo(expected);
    }

    @Test
    public void findPluginsToDownloadTest() {
        Map<String, Plugin> requestedPlugins = new HashMap<>();

        Map<String, Plugin> installedPlugins = new HashMap<>();
        Map<String, Plugin> bundledPlugins = new HashMap<>();

        List<Plugin> actualPlugins;

        requestedPlugins.put("git", new Plugin("git", "1.0.0", null, null));

        pm.setInstalledPluginVersions(installedPlugins);
        pm.setBundledPluginVersions(bundledPlugins);

        actualPlugins = pm.findPluginsToDownload(requestedPlugins);

        assertThat(actualPlugins)
                .containsExactly(new Plugin("git", "1.0.0", null, null));

        requestedPlugins.put("credentials", new Plugin("credentials", "2.1.14", null, null));
        requestedPlugins.put("structs", new Plugin("structs", "1.18", null, null));
        requestedPlugins.put("ssh-credentials", new Plugin("ssh-credentials", "1.13", null, null));

        installedPlugins.put("git", new Plugin("git", "1.1.1", null, null));
        installedPlugins.put("git-client", new Plugin("git-client","2.7.5", null, null));

        bundledPlugins.put("structs", new Plugin("structs", "1.16", null, null));

        pm.setInstalledPluginVersions(installedPlugins);
        pm.setBundledPluginVersions(bundledPlugins);

        actualPlugins = pm.findPluginsToDownload(requestedPlugins);

        assertThat(actualPlugins)
                .containsExactlyInAnyOrder(
                        new Plugin("credentials", "2.1.14", null, null),
                        new Plugin("structs", "1.18", null, null),
                        new Plugin("ssh-credentials", "1.13", null, null));
    }

    @Test
    public void getPluginVersionTest() {
        URL jpiURL = this.getClass().getResource("/delivery-pipeline-plugin.jpi");
        File testJpi = new File(jpiURL.getFile());

        assertThat(pm.getPluginVersion(testJpi)).isEqualTo("1.3.2");

        URL hpiURL = this.getClass().getResource("/ssh-credentials.hpi");
        File testHpi = new File(hpiURL.getFile());

        assertThat(pm.getPluginVersion(testHpi)).isEqualTo("1.10");
    }

    @Test
    public void getLatestPluginVersionExceptionTest() {
        setTestUcJson();

        assertThatThrownBy(() -> pm.getLatestPluginVersion("git"))
                .isInstanceOf(PluginNotFoundException.class);
    }

    @Test
    public void getLatestPluginTest() {
        setTestUcJson();
        VersionNumber antLatestVersion = pm.getLatestPluginVersion("ant");
        assertThat(antLatestVersion).hasToString("1.9");

        VersionNumber amazonEcsLatestVersion = pm.getLatestPluginVersion("amazon-ecs");
        assertThat(amazonEcsLatestVersion).hasToString("1.20");
    }

    @Test
    public void resolveDependenciesFromManifestLatestSpecified() throws IOException {
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(new File(folder.getRoot(), "plugins"))
                .withUseLatestSpecified(true)
                .build();

        PluginManager pluginManager = new PluginManager(config);
        PluginManager pluginManagerSpy = spy(pluginManager);

        Plugin testPlugin = new Plugin("test", "latest", null, null);
        doReturn(true).when(pluginManagerSpy).downloadPlugin(any(Plugin.class), any(File.class));

        mockStatic(Files.class);
        Path tempPath = mock(Path.class);
        File tempFile = mock(File.class);

        when(Files.createTempFile(any(String.class), any(String.class))).thenReturn(tempPath);
        when(tempPath.toFile()).thenReturn(tempFile);

        doReturn("1.0.0").doReturn("workflow-scm-step:2.4,workflow-step-api:2.13")
                .when(pluginManagerSpy).getAttributeFromManifest(any(File.class), any(String.class));

        doReturn(new VersionNumber("2.4")).doReturn(new VersionNumber("2.20")).when(pluginManagerSpy).
                getLatestPluginVersion(any(String.class));

        List<Plugin> actualPlugins = pluginManagerSpy.resolveDependenciesFromManifest(testPlugin);

        assertThat(actualPlugins)
                .containsExactlyInAnyOrder(
                        new Plugin("workflow-scm-step", "2.4", null, null),
                        new Plugin("workflow-step-api", "2.20", null, null));
        assertThat(testPlugin.getVersion()).hasToString("1.0.0");
    }

    @Test
    public void resolveDependenciesFromManifestLatestAll() throws IOException {
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(new File(folder.getRoot(), "plugins"))
                .withUseLatestAll(true)
                .build();

        PluginManager pluginManager = new PluginManager(config);
        PluginManager pluginManagerSpy = spy(pluginManager);

        Plugin testPlugin = new Plugin("test", "1.1", null, null);
        doReturn(true).when(pluginManagerSpy).downloadPlugin(any(Plugin.class), any(File.class));

        mockStatic(Files.class);
        Path tempPath = mock(Path.class);
        File tempFile = mock(File.class);

        when(Files.createTempFile(any(String.class), any(String.class))).thenReturn(tempPath);
        when(tempPath.toFile()).thenReturn(tempFile);

        doReturn("workflow-scm-step:2.4,workflow-step-api:2.13")
                .when(pluginManagerSpy).getAttributeFromManifest(any(File.class), any(String.class));

        doReturn(new VersionNumber("2.4")).doReturn(new VersionNumber("2.20")).when(pluginManagerSpy).
                getLatestPluginVersion(any(String.class));

        List<Plugin> actualPlugins = pluginManagerSpy.resolveDependenciesFromManifest(testPlugin);

        assertThat(actualPlugins)
                .containsExactlyInAnyOrder(
                        new Plugin("workflow-scm-step", "2.4", null, null),
                        new Plugin("workflow-step-api", "2.20", null, null));
    }

    @Test
    public void resolveDependenciesFromManifestExceptionTest() throws IOException {
        mockStatic(Files.class);
        when(Files.createTempFile(any(String.class), any(String.class))).thenThrow(IOException.class);
        Plugin testPlugin = new Plugin("test", "latest", null, null);
        assertThat(pm.resolveDependenciesFromManifest(testPlugin)).isEmpty();
    }

    @Test
    public void resolveDependenciesFromManifestNoDownload() throws IOException{
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(new File(folder.getRoot(), "plugins"))
                .build();

        PluginManager pluginManager = new PluginManager(config);
        PluginManager pluginManagerSpy = spy(pluginManager);

        Plugin testPlugin = new Plugin("test", "latest", null, null);
        doReturn(false).when(pluginManagerSpy).downloadPlugin(any(Plugin.class), any(File.class));

        mockStatic(Files.class);
        Path tempPath = mock(Path.class);
        File tempFile = mock(File.class);

        when(Files.createTempFile(any(String.class), any(String.class))).thenReturn(tempPath);
        when(tempPath.toFile()).thenReturn(tempFile);

        assertThatThrownBy(() -> pluginManager.resolveDependenciesFromManifest(testPlugin))
                .isInstanceOf(DownloadPluginException.class);
    }

    @Test
    public void resolveDependenciesFromManifestDownload() throws IOException {
        PluginManager pluginManagerSpy = spy(pm);

        Plugin testPlugin = new Plugin("test", "latest", null, null);
        doReturn(true).when(pluginManagerSpy).downloadPlugin(any(Plugin.class), any(File.class));

        mockStatic(Files.class);
        Path tempPath = mock(Path.class);
        File tempFile = mock(File.class);

        when(Files.createTempFile(any(String.class), any(String.class))).thenReturn(tempPath);
        when(tempPath.toFile()).thenReturn(tempFile);

        doReturn("1.0.0").doReturn("workflow-scm-step:2.4,workflow-step-api:2.13," +
                "credentials:2.1.14,git-client:2.7.7,mailer:1.18," +
                "parameterized-trigger:2.33;resolution:=optional," +
                "promoted-builds:2.27;resolution:=optional," +
                "scm-api:2.6.3,ssh-credentials:1.13," +
                "token-macro:1.12.1;resolution:=optional")
                .when(pluginManagerSpy).getAttributeFromManifest(any(File.class), any(String.class));

        List<Plugin> actualPlugins = pluginManagerSpy.resolveDependenciesFromManifest(testPlugin);

        assertThat(actualPlugins)
                .containsExactlyInAnyOrder(
                        new Plugin("workflow-scm-step", "2.4", null, null),
                        new Plugin("workflow-step-api", "2.13", null, null),
                        new Plugin("credentials", "2.1.14", null, null),
                        new Plugin("git-client", "2.7.7", null, null),
                        new Plugin("mailer", "1.18", null, null),
                        new Plugin("scm-api", "2.6.3", null, null),
                        new Plugin("ssh-credentials", "1.13", null, null));
        assertThat(testPlugin.getVersion()).hasToString("1.0.0");
    }

    @Test
    public void resolveDirectDependenciesManifestTest1() {
        PluginManager pluginManagerSpy = spy(pm);
        Plugin plugin = new Plugin("maven-invoker-plugin", "2.4", "url", null);

        doReturn(directDependencyExpectedPlugins).when(pluginManagerSpy).resolveDependenciesFromManifest(plugin);

        List<Plugin> actualPlugins = pluginManagerSpy.resolveDirectDependencies(plugin);

        assertThat(actualPlugins).isEqualTo(directDependencyExpectedPlugins);
    }


    @Test
    public void resolveDirectDependenciesManifestTest2() {
        PluginManager pluginManagerSpy = spy(pm);
        Plugin plugin = new Plugin("maven-invoker-plugin", "2.19-rc289.d09828a05a74", null,
                "org.jenkins-ci.plugins.workflow");

        doReturn(directDependencyExpectedPlugins).when(pluginManagerSpy).resolveDependenciesFromManifest(plugin);

        List<Plugin> actualPlugins = pluginManagerSpy.resolveDirectDependencies(plugin);

        assertThat(actualPlugins).isEqualTo(directDependencyExpectedPlugins);
    }

    @Test
    public void resolveDirectDependenciesManifestTest3() {
        PluginManager pluginManagerSpy = spy(pm);
        Plugin plugin = new Plugin("maven-invoker-plugin", "2.4", null,
                "org.jenkins-ci.plugins.workflow");

        doReturn(null).when(pluginManagerSpy).resolveDependenciesFromJson(any(Plugin.class),
                any(JSONObject.class));

        doReturn(directDependencyExpectedPlugins).when(pluginManagerSpy).resolveDependenciesFromManifest(plugin);

        List<Plugin> actualPlugins = pluginManagerSpy.resolveDirectDependencies(plugin);

        assertThat(actualPlugins).isEqualTo(directDependencyExpectedPlugins);
    }


    @Test
    public void resolveDirectDependenciesLatest() {
        PluginManager pluginManagerSpy = spy(pm);
        Plugin plugin = new Plugin("maven-invoker-plugin", "latest", null, null);

        doReturn(directDependencyExpectedPlugins).when(pluginManagerSpy).resolveDependenciesFromJson(nullable(Plugin.class),
                nullable(JSONObject.class));

        List<Plugin> actualPlugins = pluginManagerSpy.resolveDirectDependencies(plugin);

        assertThat(actualPlugins).isEqualTo(directDependencyExpectedPlugins);
    }

    @Test
    public void resolveDirectDependenciesExperimental() {
        PluginManager pluginManagerSpy = spy(pm);
        Plugin plugin = new Plugin("maven-invoker-plugin", "experimental", null, null);

        doReturn(directDependencyExpectedPlugins).when(pluginManagerSpy).resolveDependenciesFromJson(nullable(Plugin.class),
                nullable(JSONObject.class));

        List<Plugin> actualPlugins = pluginManagerSpy.resolveDirectDependencies(plugin);

        assertThat(actualPlugins).isEqualTo(directDependencyExpectedPlugins);
    }

    @Test
    public void resolveDependenciesFromJsonTest() {
        JSONObject json = (JSONObject) setTestUcJson();

        Plugin mavenInvoker = new Plugin("maven-invoker-plugin", "2.4", null, null);
        List<Plugin> actualPlugins = pm.resolveDependenciesFromJson(mavenInvoker, json);

        assertThat(actualPlugins).isEqualTo(directDependencyExpectedPlugins);
    }

    @Test
    public void resolveDependenciesFromJsonLatestSpecifiedTest() {
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(new File(folder.getRoot(), "plugins"))
                .withUseLatestSpecified(true)
                .build();

        PluginManager pluginManager = new PluginManager(config);

        JSONObject testJson = setTestUcJson();
        pluginManager.setLatestUcJson(testJson);
        pluginManager.setLatestUcPlugins(testJson.getJSONObject("plugins"));

        Plugin testWeaver = new Plugin("testweaver", "1.0.1", null, null);
        testWeaver.setLatest(true);
        List<Plugin> actualPlugins = pluginManager.resolveDependenciesFromJson(testWeaver, testJson);

        assertThat(actualPlugins)
                .containsExactly(new Plugin("structs", "1.19", null, null));
    }

    @Test
    public void resolveDependenciesFromJsonLatestAll() {
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(new File(folder.getRoot(), "plugins"))
                .withUseLatestAll(true)
                .build();

        PluginManager pluginManager = new PluginManager(config);
        PluginManager pluginManagerSpy = spy(pluginManager);

        JSONObject pluginJson = setTestUcJson();
        Plugin mvnInvokerPlugin = new Plugin("maven-invoker-plugin", "2.4", null, null);

        JSONArray mavenInvokerDependencies = array(
                dependency("workflow-api", false, "2.22"),
                dependency("workflow-step-api", false, "2.12"),
                dependency("mailer", false, "1.18"),
                dependency("script-security", false, "1.30"),
                dependency("structs", true, "1.7"));

        doReturn(mavenInvokerDependencies).when(pluginManagerSpy).getPluginDependencyJsonArray(any(Plugin.class), any(JSONObject.class));
        doReturn(new VersionNumber("2.44")).doReturn(new VersionNumber("2.30")).doReturn(new VersionNumber("1.18"))
                .doReturn(new VersionNumber("2.0"))
                .when(pluginManagerSpy).getLatestPluginVersion(any(String.class));

        List<Plugin> actualPlugins = pluginManagerSpy.resolveDependenciesFromJson(mvnInvokerPlugin, pluginJson);

        assertThat(actualPlugins)
                .containsExactlyInAnyOrder(
                        new Plugin("workflow-api", "2.44", null, null),
                        new Plugin("workflow-step-api", "2.30", null, null),
                        new Plugin("mailer", "1.18", null, null),
                        new Plugin("script-security", "2.0", null, null));
    }

    @Test
    public void resolveRecursiveDependenciesTest() {
        PluginManager pluginManagerSpy = spy(pm);
        doReturn(new ArrayList<Plugin>()).when(pluginManagerSpy).resolveDirectDependencies(any(Plugin.class));

        Plugin grandParent = new Plugin("grandparent", "1.0", null, null);

        Plugin parent1 = new Plugin("parent1", "1.0", null, null);
        Plugin parent2 = new Plugin("replaced1", "1.0", null, null);
        Plugin parent3= new Plugin("parent3", "1.2", null, null);

        Plugin child1 = new Plugin("replaced1", "1.3", null, null);
        Plugin child2 = new Plugin("child2", "3.2.1", null, null);
        Plugin child3 = new Plugin("child3", "0.9", null, null);
        Plugin child4 = new Plugin("replaced1", "1.5.1", null, null);
        Plugin child5 = new Plugin("child5", "2.2.8", null, null);
        Plugin child6 = new Plugin("child6", "2.1.1", null, null);
        Plugin child7 = new Plugin("replaced2", "1.1.0", null, null);
        Plugin child8 = new Plugin("replaced2", "2.3", null, null);
        Plugin child9 = new Plugin("child9", "1.0.3", null, null);

        List<Plugin> grandParentDependencies = Arrays.asList(
            parent1, parent2, parent3);

        grandParent.setDependencies(grandParentDependencies);

        parent1.setDependencies(Arrays.asList(child1, child2, child7, child3));
        parent2.setDependencies(Arrays.asList(child3, child8));
        parent3.setDependencies(singletonList(child9));
        child9.setDependencies(singletonList(child4));
        child1.setDependencies(singletonList(child6));
        child8.setDependencies(singletonList(child5));

        //See Jira JENKINS-58775 - the ideal solution has the following dependencies: grandparent, parent1, child4,
        //parent3, child2, child8, child3, and child9

        Map<String, Plugin> recursiveDependencies = pluginManagerSpy.resolveRecursiveDependencies(grandParent);

        assertThat(recursiveDependencies)
                .hasSize(10)
                .containsValues(
                        grandParent, parent1, child4, parent3, child2, child3,
                        child8, child9, child5, child6);
    }


    @Test
    public void installedPluginsTest() throws IOException {
        File pluginDir = cfg.getPluginDir();
        createDirectory(pluginDir.toPath());

        File tmp1 = File.createTempFile("test", ".jpi", pluginDir);
        File tmp2 = File.createTempFile("test2", ".jpi", pluginDir);

        URL deliveryPipelineJpi = this.getClass().getResource("/delivery-pipeline-plugin.jpi");
        File deliveryPipelineFile = new File(deliveryPipelineJpi.getFile());

        URL githubJpi = this.getClass().getResource("/github-branch-source.jpi");
        File githubFile = new File(githubJpi.getFile());

        FileUtils.copyFile(deliveryPipelineFile, tmp1);
        FileUtils.copyFile(githubFile, tmp2);

        String tmp1name = FilenameUtils.getBaseName(tmp1.getName());
        String tmp2name = FilenameUtils.getBaseName(tmp2.getName());

        Map<String, Plugin> actualPlugins = pm.installedPlugins();

        assertThat(actualPlugins)
                .hasSize(2)
                .containsValues(
                        new Plugin(tmp1name, "1.3.2", null, null),
                        new Plugin(tmp2name, "1.8", null, null));
    }

    @Test
    public void downloadToFileTest() throws Exception {
        mockStatic(HttpClients.class);
        CloseableHttpClient httpclient = mock(CloseableHttpClient.class);

        HttpClientBuilder builder = mock(HttpClientBuilder.class);
        when(HttpClients.custom()).thenReturn(builder);
        when(builder.build()).thenReturn(httpclient);

        HttpHead httphead = mock(HttpHead.class);

        mockStatic(HttpClientContext.class);

        HttpClientContext context = mock(HttpClientContext.class);
        when(HttpClientContext.create()).thenReturn(context);

        whenNew(HttpHead.class).withAnyArguments().thenReturn(httphead);
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        when(httpclient.execute(httphead, context)).thenReturn(response);

        HttpHost target = mock(HttpHost.class);
        when(context.getTargetHost()).thenReturn(target);

        List<URI> redirectLocations = singletonList(new URI("downloadURI"));

        when(context.getRedirectLocations()).thenReturn(redirectLocations);

        mockStatic(URIUtils.class);

        URI downloadLocation = PowerMockito.mock(URI.class);

        URI requestedLocation = PowerMockito.mock(URI.class);
        when(httphead.getURI()).thenReturn(requestedLocation);

        when(URIUtils.resolve(requestedLocation, target, redirectLocations)).thenReturn(downloadLocation);

        mockStatic(FileUtils.class);

        //File pluginDir = cfg.getPluginDir();
        //File tmp3 = File.createTempFile("test", ".jpi", pluginDir);

        Plugin plugin = new Plugin("pluginName", "pluginVersion", "pluginURL", null);

        JarFile pluginJpi = mock(JarFile.class);

        whenNew(JarFile.class).withAnyArguments().thenReturn(pluginJpi);
        assertThat(pm.downloadToFile("downloadURL", plugin, null)).isTrue();
    }

    @Test
    public void getPluginDownloadUrlTest() {
        Plugin plugin = new Plugin("pluginName", "pluginVersion", "pluginURL", null);

        assertThat(pm.getPluginDownloadUrl(plugin)).isEqualTo("pluginURL");

        // Note: As of now (2019/12/18 lmm) there is no 'latest' folder in the cloudbees update center as a sibling of the "download" folder
        //  so this is only applicable on jenkins.io
        Plugin pluginNoUrl = new Plugin("pluginName", "latest", null, null);
        String latestUcUrl = "https://updates.jenkins.io/2.176";
        pm.setJenkinsUCLatest(latestUcUrl + "/update-center.json");
        VersionNumber latestVersion = new VersionNumber("latest");
        String latestUrl = latestUcUrl + "/latest/pluginName.hpi";
        Assert.assertEquals(latestUrl, pm.getPluginDownloadUrl(pluginNoUrl));

        Plugin pluginNoVersion = new Plugin("pluginName", null, null, null);
        assertThat(pm.getPluginDownloadUrl(pluginNoVersion)).isEqualTo(latestUrl);

        Plugin pluginExperimentalVersion = new Plugin("pluginName", "experimental", null, null);
        String experimentalUrl = dirName(cfg.getJenkinsUcExperimental()) + "latest/pluginName.hpi";
        Assert.assertEquals(experimentalUrl, pm.getPluginDownloadUrl(pluginExperimentalVersion));

        Plugin pluginIncrementalRepo = new Plugin("pluginName", "2.19-rc289.d09828a05a74", null, "org.jenkins-ci.plugins.pluginName");

        String incrementalUrl = cfg.getJenkinsIncrementalsRepoMirror() +
                "/org/jenkins-ci/plugins/pluginName/pluginName/2.19-rc289.d09828a05a74/pluginName-2.19-rc289.d09828a05a74.hpi";

        assertThat(pm.getPluginDownloadUrl(pluginIncrementalRepo)).isEqualTo(incrementalUrl);

        Plugin pluginOtherVersion = new Plugin("pluginName", "otherversion", null, null);
        String otherURL = dirName(cfg.getJenkinsUc().toString()) +
                "download/plugins/pluginName/otherversion/pluginName.hpi";
        assertThat(pm.getPluginDownloadUrl(pluginOtherVersion)).isEqualTo(otherURL);
    }

    @Test
    public void getDownloadPluginUrlTestComplexUpdateCenterUrl() throws IOException {
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(new File(folder.getRoot(), "plugins"))
                .withJenkinsUc(new URL("https://jenkins-updates.cloudbees.com/update-center/envelope-cje/update-center.json?version=2.176.4.3"))
                .build();

        PluginManager pluginManager = new PluginManager(config);

        Plugin plugin = new Plugin("the-plugin", "1.0", null, null);

        String result = pluginManager.getPluginDownloadUrl(plugin);

        assertThat(result).isEqualTo("https://jenkins-updates.cloudbees.com/download/plugins/the-plugin/1.0/the-plugin.hpi");
    }

    @Test
    public void getAttributeFromManifestExceptionTest() throws Exception {
        URL jpiURL = this.getClass().getResource("/delivery-pipeline-plugin.jpi");
        File testJpi = new File(jpiURL.getFile());

        whenNew(JarFile.class).withArguments(testJpi).thenThrow(new IOException());

        assertThatThrownBy(() -> pm.getAttributeFromManifest(testJpi, "Plugin-Dependencies"))
                .isInstanceOf(DownloadPluginException.class);
    }

    public void getAttributeFromManifestTest() throws Exception {
        URL jpiURL = this.getClass().getResource("/delivery-pipeline-plugin.jpi");
        File testJpi = new File(jpiURL.getFile());

        String key = "key";
        String value = "value";

        JarFile jarFile = mock(JarFile.class);
        Manifest manifest = mock(Manifest.class);
        Attributes attributes = mock(Attributes.class);

        whenNew(JarFile.class).withArguments(testJpi).thenReturn(jarFile);
        when(jarFile.getManifest()).thenReturn(manifest);
        when(manifest.getMainAttributes()).thenReturn(attributes);
        when(attributes.getValue(key)).thenReturn(value);

        assertThat(pm.getAttributeFromManifest(testJpi, "key")).isEqualTo(value);
    }

    public void showAllSecurityWarningsNoOutput() throws Exception {
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(new File(folder.getRoot(), "plugins"))
                .withShowAllWarnings(false)
                .build();

        PluginManager pluginManager = new PluginManager(config);

        String output = tapSystemOutNormalized(
                pluginManager::showAllSecurityWarnings);

        assertThat(output).isEmpty();
    }


    public void showAllSecurityWarnings() throws Exception {
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(new File(folder.getRoot(), "plugins"))
                .withShowAllWarnings(true)
                .build();

        PluginManager pluginManager = new PluginManager(config);

        pluginManager.setLatestUcJson(setTestUcJson());

        String output = tapSystemOutNormalized(
                pluginManager::showAllSecurityWarnings);

        assertThat(output).isEqualTo(
                "google-login - Authentication bypass vulnerability\n" +
                        "cucumber-reports - Plugin disables Content-Security-Policy for files served by Jenkins\n" +
                        "pipeline-maven - Arbitrary files from Jenkins master available in Pipeline by using the withMaven step\n" +
                        "pipeline-maven - XML External Entity processing vulnerability\n");
    }


    @Test
    public void getJenkinsVersionFromWarTest() throws Exception {
        URL warURL = this.getClass().getResource("/jenkinsversiontest.war");
        File testWar = new File(warURL.getFile());

        //the only time the file for a particular war string is created is in the PluginManager constructor
        Config config = Config.builder()
                .withJenkinsWar(testWar.toString())
                .build();
        PluginManager pluginManager = new PluginManager(config);
        assertThat(pluginManager.getJenkinsVersionFromWar())
                .isEqualByComparingTo(new VersionNumber("2.164.1"));
    }

    @Test
    @PrepareForTest({HttpClients.class, HttpClientContext.class, HttpHost.class})
    public void bundledPluginsTest() {
        URL warURL = this.getClass().getResource("/bundledplugintest.war");
        File testWar = new File(warURL.getFile());

        Config config = Config.builder()
                .withJenkinsWar(testWar.toString())
                .build();
        PluginManager pluginManager = new PluginManager(config);

        Map<String, Plugin> actualPlugins = pluginManager.bundledPlugins();

        assertThat(actualPlugins.values())
                .containsExactlyInAnyOrder(
                        new Plugin("credentials","2.1.18", null, null),
                        new Plugin("display-url-api","2.0", null, null),
                        new Plugin("github-branch-source", "1.8", null, null));
    }

    private JSONObject setTestUcJson() {
        JSONObject latestUcJson = new JSONObject();

        JSONObject pluginJson = new JSONObject();
        latestUcJson.put("plugins", pluginJson);

        JSONObject structsPlugin = new JSONObject();

        JSONObject credentials = dependency("scm-api", false, "2.2.6");

        structsPlugin.put("dependencies", array(credentials));
        structsPlugin.put("version", "1.19");
        structsPlugin.put("requiredCore", "2.60.3");
        pluginJson.put("structs", structsPlugin);

        JSONObject testWeaverPlugin = new JSONObject();

        JSONObject structsDependency = dependency("structs", false, "1.7");

        testWeaverPlugin.put("dependencies", array(structsDependency));
        testWeaverPlugin.put("version", "1.0.1");
        testWeaverPlugin.put("requiredCore", "2.7.3");
        pluginJson.put("testweaver", testWeaverPlugin);

        JSONObject mavenInvokerPlugin = new JSONObject();

        mavenInvokerPlugin.put("dependencies", array(
                dependency("workflow-api", false, "2.22"),
                dependency("workflow-step-api", false, "2.12"),
                dependency("mailer", false, "1.18"),
                dependency("script-security", false, "1.30"),
                dependency("structs", true, "1.7")));
        mavenInvokerPlugin.put("version", "2.4");
        mavenInvokerPlugin.put("requiredCore", "2.89.4");

        pluginJson.put("maven-invoker-plugin", mavenInvokerPlugin);

        JSONObject amazonEcsPlugin = new JSONObject();

        amazonEcsPlugin.put("dependencies", array(
                dependency("workflow-step-api", false, "2.12"),
                dependency("workflow-step-api", false, "4.5.5-3.0"),
                dependency("aws-credentials", false, "1.23")
        ));
        amazonEcsPlugin.put("version", "1.20");
        amazonEcsPlugin.put("requiredCore", "2.107.3");

        pluginJson.put("amazon-ecs", amazonEcsPlugin);

        JSONObject antPlugin = new JSONObject();

        antPlugin.put("dependencies", array(dependency("structs", true, "1.7")));
        antPlugin.put("version", "1.9");
        antPlugin.put("requiredCore", "2.121.2");

        pluginJson.put("ant", antPlugin);

        JSONObject signatureJSON = new JSONObject();
        latestUcJson.put("signature", signatureJSON);

        latestUcJson.put("updateCenterVersion", "1");

        JSONObject security208 = new JSONObject();
        security208.put("id", "SECURITY-208");
        security208.put("message", "Authentication bypass vulnerability");
        security208.put("name", "google-login");
        security208.put("type", "plugin");
        security208.put("url", "https://jenkins.io/security/advisory/2015-10-12/");
        JSONObject version208 = new JSONObject();
        version208.put("lastVersion", "1.1");
        version208.put("pattern", "1[.][01](|[.-].*)");
        security208.put("versions", array(version208, version208));

        JSONObject security309 = new JSONObject();
        security309.put("id", "SECURITY-309");
        security309.put("message", "Plugin disables Content-Security-Policy for files served by Jenkins");
        security309.put("name", "cucumber-reports");
        security309.put("type", "plugin");
        security309.put("url", "https://jenkins.io/security/advisory/2016-07-27/");

        JSONObject version309 = new JSONObject();
        version309.put("firstVersion", "1.3.0");
        version309.put("lastVersion", "2.5.1");
        version309.put("pattern", "(1[.][34]|2[.][012345])(|[.-].*)");
        security309.put("versions", array(version309));

        JSONObject core = new JSONObject();
        core.put("id", "core-2_44");
        core.put("message", "Multiple security vulnerabilities in Jenkins 2.43 and earlier, and LTS 2.32.1 and earlier");
        core.put("name", "core");
        core.put("type", "core");
        core.put("url", "https://jenkins.io/security/advisory/2017-02-01/");

        JSONObject coreVersion = new JSONObject();
        coreVersion.put("lastVersion", "2.43");
        coreVersion.put("pattern", "(1[.].*|2[.]\\d|2[.][123]\\d|2[.]4[0123])(|[-].*)");
        core.put("versions", array(coreVersion));

        JSONObject security441 = new JSONObject();
        security441.put("id", "SECURITY-441");
        security441.put("message", "Arbitrary files from Jenkins master available in Pipeline by using the withMaven step");
        security441.put("name", "pipeline-maven");
        security441.put("type", "plugin");
        security441.put("url", "https://jenkins.io/security/advisory/2017-03-09/");
        JSONObject firstVersions441 = new JSONObject();
        firstVersions441.put("lastVersion", "0.6");
        firstVersions441.put("pattern", "0[.][123456](|[.-].*)");
        JSONObject laterVersions441 = new JSONObject();
        laterVersions441.put("lastVersion", "2.0-beta-5");
        laterVersions441.put("pattern", "2[.]0[-]beta[-][345](|[.-].*)");
        security441.put("versions", array(firstVersions441, laterVersions441));

        JSONObject security1409 = new JSONObject();
        security1409.put("id", "SECURITY-1409");
        security1409.put("message", "XML External Entity processing vulnerability");
        security1409.put("name", "pipeline-maven");
        security1409.put("type", "plugin");
        security1409.put("url", "https://jenkins.io/security/advisory/2019-05-31/#SECURITY-1409");

        JSONObject version1409 = new JSONObject();
        version1409.put("lastVersion", "3.7.0");
        version1409.put("pattern", "([0-2]|3[.][0-6]|3[.]7[.]0)(|[.-].*)");
        security1409.put("versions", array(version1409));

        latestUcJson.put("warnings", array(
                security208, security309, core, security441, security1409));

        pm.setLatestUcJson(latestUcJson);
        pm.setLatestUcPlugins(pluginJson);
        return latestUcJson;
    }

    private JSONArray array(
            JSONObject... objects) {
        return new JSONArray(objects);
    }

    private JSONObject dependency(
            String name,
            boolean optional,
            String version) {
        return new JSONObject()
                .put("name", name)
                .put("optional", optional)
                .put("version", version);
    }
}
