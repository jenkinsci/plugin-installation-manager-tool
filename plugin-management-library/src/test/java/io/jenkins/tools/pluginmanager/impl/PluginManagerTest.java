package io.jenkins.tools.pluginmanager.impl;

import hudson.util.VersionNumber;
import io.jenkins.tools.pluginmanager.config.Config;
import io.jenkins.tools.pluginmanager.config.Settings;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOutNormalized;
import static io.jenkins.tools.pluginmanager.util.PluginManagerUtils.dirName;
import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
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
        URI.class, FileUtils.class, URL.class, IOUtils.class, Files.class, HttpClientBuilder.class})
@PowerMockIgnore({"javax.net.ssl.*","javax.security.*", "javax.net.*"})
public class PluginManagerTest {
    private PluginManager pm;
    private Config cfg;
    private List<Plugin> directDependencyExpectedPlugins;

    @Before
    public void setUp() throws IOException {
        cfg = Config.builder()
            .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(Files.createTempDirectory("plugins").toFile())
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
    public void startTest() throws IOException {
        File refDir = mock(File.class);

        Config config = mock(Config.class);

        when(config.getPluginDir()).thenReturn(refDir);
        when(config.getJenkinsWar()).thenReturn(Settings.DEFAULT_WAR);
        when(config.getJenkinsUc()).thenReturn(Settings.DEFAULT_UPDATE_CENTER);
        when(config.getPlugins()).thenReturn(new ArrayList<Plugin>());
        when(config.doDownload()).thenReturn(true);
        when(config.getJenkinsUcExperimental()).thenReturn(Settings.DEFAULT_EXPERIMENTAL_UPDATE_CENTER);

        PluginManager pluginManager = new PluginManager(config);

        when(refDir.exists()).thenReturn(false);

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
        File refDir = mock(File.class);

        Config config = mock(Config.class);

        when(config.getPluginDir()).thenReturn(refDir);
        when(config.getJenkinsWar()).thenReturn(Settings.DEFAULT_WAR);
        when(config.getJenkinsUc()).thenReturn(Settings.DEFAULT_UPDATE_CENTER);

        PluginManager pluginManager = new PluginManager(config);

        when(refDir.exists()).thenReturn(false);

        Path refPath = mock(Path.class);
        when(refDir.toPath()).thenReturn(refPath);

        mockStatic(Files.class);
        when(Files.createDirectories(refPath)).thenThrow(IOException.class);

        assertThrows(DirectoryCreationException.class, pluginManager::start);
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

        assertEquals("1.3", effectivePlugins.get("git").getVersion().toString());
        assertEquals("2.2.3", effectivePlugins.get("scm-api").getVersion().toString());
        assertEquals("1.24", effectivePlugins.get("aws-credentials").getVersion().toString());
        assertEquals("1.3.3", effectivePlugins.get("p4").getVersion().toString());
        assertEquals("1.26", effectivePlugins.get("script-security").getVersion().toString());
        assertEquals("2.1.11", effectivePlugins.get("credentials").getVersion().toString());
        assertEquals("1.0.1", effectivePlugins.get("ace-editor").getVersion().toString());
    }

    @Test
    public void listPluginsNoOutputTest() throws Exception {
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(Files.createTempDirectory("plugins").toFile())
                .withShowPluginsToBeDownloaded(false)
                .build();

        PluginManager pluginManager = new PluginManager(config);

        String output = tapSystemOutNormalized(
                pluginManager::listPlugins);

        assertEquals("", output);
    }



    @Test
    public void listPluginsOutputTest() throws Exception {
         Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(Files.createTempDirectory("plugins").toFile())
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

        String expectedOutput =
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
                "plugin2 2.0\n";

        String output = tapSystemOutNormalized(
                pluginManager::listPlugins);

        assertEquals(expectedOutput, output);
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
        JSONArray awsCodebuildDependencies = new JSONArray();

        JSONObject workflowStepApiDependency = new JSONObject();
        workflowStepApiDependency.put("name", "workflow-step-api");
        workflowStepApiDependency.put("optional", "false");
        workflowStepApiDependency.put("version", "2.5");

        JSONObject cloudBeesFolderDependency = new JSONObject();
        cloudBeesFolderDependency.put("name", "cloudbees-folder");
        cloudBeesFolderDependency.put("optional", "false");
        cloudBeesFolderDependency.put("version", "6.1.0");

        JSONObject credentialsFolderDependency = new JSONObject();
        credentialsFolderDependency.put("name", "credentials");
        credentialsFolderDependency.put("optional", "false");
        credentialsFolderDependency.put("version", "2.1.14");

        JSONObject scriptSecurityDependency = new JSONObject();
        scriptSecurityDependency.put("name", "script-security");
        scriptSecurityDependency.put("optional", "false");
        scriptSecurityDependency.put("version", "1.29");

        awsCodebuild.put("dependencies", awsCodebuildDependencies);

        awsCodebuildDependencies.put(workflowStepApiDependency);
        awsCodebuildDependencies.put(cloudBeesFolderDependency);
        awsCodebuildDependencies.put(credentialsFolderDependency);
        awsCodebuildDependencies.put(scriptSecurityDependency);

        JSONObject awsGlobalConfig = new JSONObject();
        awsGlobalConfig.put("buildDate", "Mar 27, 2019");
        awsGlobalConfig.put("version", "1.3");
        awsGlobalConfig.put("requiredCore", "2.121.1");

        JSONArray awsGlobalConfigDependencies = new JSONArray();

        JSONObject awsCredDependency = new JSONObject();
        awsCredDependency.put("name", "aws-credentials");
        awsCredDependency.put("optional", "false");
        awsCredDependency.put("version", "1.23");

        JSONObject structsDepedency = new JSONObject();
        structsDepedency.put("name", "structs");
        structsDepedency.put("optional", "false");
        structsDepedency.put("version", "1.14");

        awsGlobalConfig.put("dependencies", awsGlobalConfigDependencies);

        plugins.put("aws-global-configuration", awsGlobalConfig);
        plugins.put("aws-codebuild", awsCodebuild);

        updateCenterJson.put("plugins", plugins);

        Plugin awsCodeBuildPlugin = new Plugin("aws-codebuild", "1.2", null , null);
        Plugin awsGlobalConfigPlugin = new Plugin("aws-global-configuration", "1.1", null, null);
        Plugin gitPlugin = new Plugin("structs", "1.17", null, null);
        JSONArray gitJson = pm.getPluginDependencyJsonArray(gitPlugin, updateCenterJson);

        assertEquals(gitJson, null);

        JSONArray awsCodeBuildJson = pm.getPluginDependencyJsonArray(awsCodeBuildPlugin, updateCenterJson);
        assertEquals(awsCodebuildDependencies.toString(), awsCodeBuildJson.toString());

        JSONArray awsGlobalConfJson = pm.getPluginDependencyJsonArray(awsGlobalConfigPlugin, updateCenterJson);
        assertEquals(awsGlobalConfigDependencies.toString(), awsGlobalConfJson.toString());
    }

    @Test
    public void getPluginDependencyJsonArrayTest2() {
        //test plugin-version json, which has a different format
        JSONObject pluginVersionJson = new JSONObject();
        JSONObject plugins = new JSONObject();

        JSONObject browserStackIntegration= new JSONObject();
        JSONObject browserStack1 = new JSONObject();
        browserStack1.put("requiredCore", "1.580.1");
        JSONArray dependencies1 = new JSONArray();
        JSONObject credentials1 = new JSONObject();
        credentials1.put("name", "credentials");
        credentials1.put("optional", "false");
        credentials1.put("version", "1.8");
        JSONObject junit1 = new JSONObject();
        junit1.put("name", "junit");
        junit1.put("optional", "false");
        junit1.put("version", "1.10");

        dependencies1.put(credentials1);
        dependencies1.put(junit1);
        browserStack1.put("dependencies", dependencies1);
        browserStackIntegration.put("1.0.0", browserStack1);

        JSONObject browserStack111 = new JSONObject();
        browserStack111.put("requiredCore", "1.580.1");
        JSONArray dependencies111 = new JSONArray();
        JSONObject credentials111 = new JSONObject();
        credentials111.put("name", "credentials");
        credentials111.put("optional", "false");
        credentials111.put("version", "1.8");
        JSONObject junit111 = new JSONObject();
        junit111.put("name", "junit");
        junit111.put("optional", "false");
        junit111.put("version", "1.10");

        dependencies111.put(credentials111);
        dependencies111.put(junit111);
        browserStack111.put("dependencies", dependencies111);
        browserStackIntegration.put("1.1.1", browserStack111);

        JSONObject browserStack112 = new JSONObject();
        browserStack112.put("requiredCore", "1.653");
        JSONArray dependencies112 = new JSONArray();
        JSONObject credentials112 = new JSONObject();
        credentials112.put("name", "credentials");
        credentials112.put("optional", "false");
        credentials112.put("version", "2.1.17");
        JSONObject junit112 = new JSONObject();
        junit112.put("name", "junit");
        junit112.put("optional", "false");
        junit112.put("version", "1.10");
        JSONObject workflowApi112 = new JSONObject();
        workflowApi112.put("name", "workflow-api");
        workflowApi112.put("optional", "false");
        workflowApi112.put("version", "2.6");
        JSONObject workflowBasicSteps112 = new JSONObject();
        workflowBasicSteps112.put("name", "workflow-basic-steps");
        workflowBasicSteps112.put("optional", false);
        workflowBasicSteps112.put("version", "2.6");
        JSONObject workflowCps112 = new JSONObject();
        workflowCps112.put("name", "workflow-cps");
        workflowCps112.put("optional", "false");
        workflowCps112.put("version", "2.39");

        dependencies112.put(credentials112);
        dependencies112.put(junit112);
        dependencies112.put(workflowApi112);
        dependencies112.put(workflowBasicSteps112);
        dependencies112.put(workflowCps112);
        browserStack112.put("dependencies", dependencies112);
        browserStackIntegration.put("1.1.2", browserStack112);

        plugins.put("browserstack-integration", browserStackIntegration);

        pluginVersionJson.put("plugins", plugins);

        Plugin gitPlugin = new Plugin("git", "1.17", null, null);
        JSONArray gitJson = pm.getPluginDependencyJsonArray(gitPlugin, pluginVersionJson);

        assertEquals(gitJson, null);

        pm.setPluginInfoJson(pluginVersionJson);

        Plugin browserStackPlugin1 = new Plugin("browserstack-integration", "1.0.0", null, null);
        JSONArray browserStackPluginJson1 = pm.getPluginDependencyJsonArray(browserStackPlugin1, pluginVersionJson);
        assertEquals(dependencies1.toString(), browserStackPluginJson1.toString());

        Plugin browserStackPlugin111 = new Plugin("browserstack-integration", "1.1.1", null, null);
        JSONArray browserStackPluginJson111 = pm.getPluginDependencyJsonArray(browserStackPlugin111, pluginVersionJson);
        assertEquals(dependencies111.toString(), browserStackPluginJson111.toString());

        Plugin browserStackPlugin112 = new Plugin("browserstack-integration", "1.1.2", null, null);
        JSONArray browserStackPluginJson112 = pm.getPluginDependencyJsonArray(browserStackPlugin112, pluginVersionJson);
        assertEquals(dependencies112.toString(), browserStackPluginJson112.toString());
    }

    @Test
    public void getSecurityWarningsTest() {
        setTestUcJson();

        Map<String, List<SecurityWarning>> allSecurityWarnings = pm.getSecurityWarnings();

        assertEquals(null, allSecurityWarnings.get("core"));

        assertEquals(3, allSecurityWarnings.size());

        SecurityWarning googleLoginSecurityWarning = allSecurityWarnings.get("google-login").get(0);
        assertEquals("SECURITY-208", googleLoginSecurityWarning.getId());
        assertEquals("1[.][01](|[.-].*)", googleLoginSecurityWarning.getSecurityVersions().get(0).getPattern().toString());

        List<SecurityWarning> pipelineMavenSecurityWarning = allSecurityWarnings.get("pipeline-maven");

        assertEquals(2, pipelineMavenSecurityWarning.size());

        List<String> securityWarningInfo = new ArrayList<>();
        for (SecurityWarning securityWarning : pipelineMavenSecurityWarning) {
            securityWarningInfo.add(securityWarning.getId() + " " + securityWarning.getMessage() + " " + securityWarning.getUrl());
        }
        Collections.sort(securityWarningInfo);
        List<String> expectedSecurityInfo = new ArrayList<>();
        expectedSecurityInfo.add("SECURITY-441 Arbitrary files from Jenkins master available in Pipeline by using the " +
                        "withMaven step https://jenkins.io/security/advisory/2017-03-09/");
        expectedSecurityInfo.add("SECURITY-1409 XML External Entity processing vulnerability " +
                "https://jenkins.io/security/advisory/2019-05-31/#SECURITY-1409");
        Collections.sort(expectedSecurityInfo);

        assertEquals(expectedSecurityInfo, securityWarningInfo);

        assertEquals(2, pipelineMavenSecurityWarning.get(0).getSecurityVersions().size());
    }

    @Test
    public void getSecurityWarningsWhenNoWarningsTest() {
        JSONObject json = setTestUcJson();
        json.remove("warnings");
        assertFalse(json.has("warnings"));

        Map<String, List<SecurityWarning>> allSecurityWarnings = pm.getSecurityWarnings();

        assertEquals(0, allSecurityWarnings.size());
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

        assertEquals(true, pm.warningExists(scriptler));
        assertEquals(true, pm.warningExists(lockableResource));
        assertEquals(false, pm.warningExists(lockableResource2));
        assertEquals(false, pm.warningExists(cucumberReports1));
        assertEquals(true, pm.warningExists(cucumberReports2));
        //assertEquals(false, pm.warningExists(cucumberReports3));
        // currently fails since 2.5.3 matches pattern even though 2.5.1 is last effected version
        assertEquals(true, pm.warningExists(sshAgents1));
        assertEquals(false, pm.warningExists(sshAgents2));
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

        assertThrows(
                VersionCompatibilityException.class,
                () -> pm.checkVersionCompatibility(pluginsToDownload));
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
                .withPluginDir(Files.createTempDirectory("tmpplugins").toFile())
                .withShowAvailableUpdates(false)
                .build();

        PluginManager pluginManager = new PluginManager(config);


        List<Plugin> plugins = Arrays.asList(
                new Plugin("ant", "1.8", null, null),
                new Plugin("amazon-ecs", "1.15", null, null));

        String output = tapSystemOutNormalized(
                () -> pluginManager.showAvailableUpdates(plugins));

        assertEquals("", output);
    }

    @Test
    public void showAvailableUpdates() throws Exception {
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(Files.createTempDirectory("tmpplugins").toFile())
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

        String expectedOutput = "\nAvailable updates:\n" +
                "ant (1.8) has an available update: 1.9\n" +
                "amazon-ecs (1.15) has an available update: 1.20\n";

        assertEquals(expectedOutput, output);
    }

    @Test
    public void downloadPluginsSuccessfulTest() throws IOException {
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(Files.createTempDirectory("tmpplugins").toFile())
                .build();

        PluginManager pluginManager = new PluginManager(config);
        PluginManager pluginManagerSpy = spy(pluginManager);

        doReturn(true).when(pluginManagerSpy).downloadPlugin(any(Plugin.class), nullable(File.class));

        List<Plugin> plugins = singletonList(
                new Plugin("plugin", "1.0", null, null));

        pluginManagerSpy.downloadPlugins(plugins);

        assertEquals(true, pluginManagerSpy.getFailedPlugins().isEmpty());
    }

    @Test
    public void downloadPluginsUnsuccessfulTest() throws IOException {
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(Files.createTempDirectory("tmpplugins").toFile())
                .build();

        PluginManager pluginManager = new PluginManager(config);
        PluginManager pluginManagerSpy = spy(pluginManager);

        doReturn(false).when(pluginManagerSpy).downloadPlugin(any(Plugin.class), nullable(File.class));

        List<Plugin> plugins = singletonList(
                new Plugin("plugin", "1.0", null, null));

        assertThrows(
                DownloadPluginException.class,
                () -> pluginManagerSpy.downloadPlugins(plugins));
    }

    @Test
    public void downloadPluginAlreadyInstalledTest() {
        Map<String, Plugin> installedVersions = new HashMap<>();
        installedVersions.put("plugin1", new Plugin("plugin1", "1.0", null, null));
        installedVersions.put("plugin2", new Plugin("plugin2", "2.0", null, null));

        pm.setInstalledPluginVersions(installedVersions);

        assertEquals(true, pm.downloadPlugin(new Plugin("plugin1", "1.0", null, null), null));
    }

    @Test
    public void downloadPluginSuccessfulFirstAttempt() throws IOException {
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(Files.createTempDirectory("tmpplugins").toFile())
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

        assertEquals(true, pluginManagerSpy.downloadPlugin(pluginToDownload, null));
        assertEquals("plugin", pluginToDownload.getName());
        assertEquals("plugin", pluginToDownload.getOriginalName());
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
        assertEquals(expected, pm.getJenkinsUCLatest());
    }

    @Test
    public void outputPluginReplacementInfoTest() throws Exception {
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(Files.createTempDirectory("tmpplugins").toFile())
                .withIsVerbose(true)
                .build();

        PluginManager pluginManager = new PluginManager(config);
        Plugin lowerVersion = new Plugin("plugin1", "1.0", null, null);
        Plugin lowerVersionParent = new Plugin("plugin1parent1", "1.0.0", null, null);
        Plugin higherVersion = new Plugin("plugin1", "2.0", null, null);
        Plugin highVersionParent = new Plugin("plugin1parent2", "2.0.0", null, null);
        lowerVersion.setParent(lowerVersionParent);
        higherVersion.setParent(highVersionParent);

        String expected = "Version of plugin1 (1.0) required by plugin1parent1 (1.0.0) is lower than the version " +
                "required (2.0) by plugin1parent2 (2.0.0), upgrading required plugin version\n";

        String output = tapSystemOutNormalized(
                () -> pluginManager.outputPluginReplacementInfo(lowerVersion, higherVersion));

        assertEquals(expected, output);
    }

    @Test
    public void getJsonURLExceptionTest() {
        assertThrows(
                UpdateCenterInfoRetrievalException.class,
                () -> pm.getJson("htttp://ftp-chi.osuosl.org/pub/jenkins/updates/current/update-center.json"));
    }

    @Test
    public void getJsonURLIOTest() throws IOException{
        mockStatic(IOUtils.class);

        when(IOUtils.toString(any(URL.class), any(Charset.class))).thenThrow(IOException.class);

        pm.setCm(new MockCacheManager());

        assertThrows(
                UpdateCenterInfoRetrievalException.class,
                () -> pm.getJson(new URL("http://ftp-chi.osuosl.org/pub/jenkins/updates/current/update-center.json"), "update-center"));
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
        assertEquals(expected, pm.getJenkinsUCLatest());
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

        assertEquals(
                singletonList(new Plugin("git", "1.0.0", null, null)),
                actualPlugins);

        requestedPlugins.put("credentials", new Plugin("credentials", "2.1.14", null, null));
        requestedPlugins.put("structs", new Plugin("structs", "1.18", null, null));
        requestedPlugins.put("ssh-credentials", new Plugin("ssh-credentials", "1.13", null, null));

        installedPlugins.put("git", new Plugin("git", "1.1.1", null, null));
        installedPlugins.put("git-client", new Plugin("git-client","2.7.5", null, null));

        bundledPlugins.put("structs", new Plugin("structs", "1.16", null, null));

        pm.setInstalledPluginVersions(installedPlugins);
        pm.setBundledPluginVersions(bundledPlugins);

        actualPlugins = pm.findPluginsToDownload(requestedPlugins);

        List<Plugin> expected = Arrays.asList(
                new Plugin("credentials", "2.1.14", null, null),
                new Plugin("structs", "1.18", null, null),
                new Plugin("ssh-credentials", "1.13", null, null));

        assertEquals(expected, actualPlugins);
    }

    @Test
    public void getPluginVersionTest() {
        URL jpiURL = this.getClass().getResource("/delivery-pipeline-plugin.jpi");
        File testJpi = new File(jpiURL.getFile());

        assertEquals("1.3.2", pm.getPluginVersion(testJpi));

        URL hpiURL = this.getClass().getResource("/ssh-credentials.hpi");
        File testHpi = new File(hpiURL.getFile());

        assertEquals("1.10", pm.getPluginVersion(testHpi));
    }

    @Test
    public void getLatestPluginVersionExceptionTest() {
        setTestUcJson();

        assertThrows(
                PluginNotFoundException.class,
                () -> pm.getLatestPluginVersion("git"));
    }

    @Test
    public void getLatestPluginTest() {
        setTestUcJson();
        VersionNumber antLatestVersion = pm.getLatestPluginVersion("ant");
        assertEquals("1.9", antLatestVersion.toString());

        VersionNumber amazonEcsLatestVersion = pm.getLatestPluginVersion("amazon-ecs");
        assertEquals("1.20", amazonEcsLatestVersion.toString());
    }

    @Test
    public void resolveDependenciesFromManifestLatestSpecified() throws IOException {
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(Files.createTempDirectory("tmpplugins").toFile())
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

        List<Plugin> expectedPlugins = Arrays.asList(
                new Plugin("workflow-scm-step", "2.4", null, null),
                new Plugin("workflow-step-api", "2.20", null, null));

        List<Plugin> actualPlugins = pluginManagerSpy.resolveDependenciesFromManifest(testPlugin);

        assertEquals(expectedPlugins, actualPlugins);
        assertEquals(testPlugin.getVersion().toString(), "1.0.0");
    }

    @Test
    public void resolveDependenciesFromManifestLatestAll() throws IOException {
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(Files.createTempDirectory("tmpplugins").toFile())
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

        List<Plugin> expectedPlugins = Arrays.asList(
                new Plugin("workflow-scm-step", "2.4", null, null),
                new Plugin("workflow-step-api", "2.20", null, null));

        List<Plugin> actualPlugins = pluginManagerSpy.resolveDependenciesFromManifest(testPlugin);

        assertEquals(expectedPlugins, actualPlugins);
    }

    @Test
    public void resolveDependenciesFromManifestExceptionTest() throws IOException {
        mockStatic(Files.class);
        when(Files.createTempFile(any(String.class), any(String.class))).thenThrow(IOException.class);
        Plugin testPlugin = new Plugin("test", "latest", null, null);
        assertEquals(true, pm.resolveDependenciesFromManifest(testPlugin).isEmpty());
    }

    @Test
    public void resolveDependenciesFromManifestNoDownload() throws IOException{
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(Files.createTempDirectory("tmpplugins").toFile())
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

        assertThrows(
                DownloadPluginException.class,
                () -> pluginManager.resolveDependenciesFromManifest(testPlugin));
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

        List<Plugin> expectedPlugins = Arrays.asList(
                new Plugin("workflow-scm-step", "2.4", null, null),
                new Plugin("workflow-step-api", "2.13", null, null),
                new Plugin("credentials", "2.1.14", null, null),
                new Plugin("git-client", "2.7.7", null, null),
                new Plugin("mailer", "1.18", null, null),
                new Plugin("scm-api", "2.6.3", null, null),
                new Plugin("ssh-credentials", "1.13", null, null));

        List<Plugin> actualPlugins = pluginManagerSpy.resolveDependenciesFromManifest(testPlugin);

        assertEquals(expectedPlugins, actualPlugins);
        assertEquals(testPlugin.getVersion().toString(), "1.0.0");
    }

    @Test
    public void resolveDirectDependenciesManifestTest1() {
        PluginManager pluginManagerSpy = spy(pm);
        Plugin plugin = new Plugin("maven-invoker-plugin", "2.4", "url", null);

        doReturn(directDependencyExpectedPlugins).when(pluginManagerSpy).resolveDependenciesFromManifest(plugin);

        List<Plugin> actualPlugins = pluginManagerSpy.resolveDirectDependencies(plugin);

        assertEquals(directDependencyExpectedPlugins, actualPlugins);
    }


    @Test
    public void resolveDirectDependenciesManifestTest2() {
        PluginManager pluginManagerSpy = spy(pm);
        Plugin plugin = new Plugin("maven-invoker-plugin", "2.19-rc289.d09828a05a74", null,
                "org.jenkins-ci.plugins.workflow");

        doReturn(directDependencyExpectedPlugins).when(pluginManagerSpy).resolveDependenciesFromManifest(plugin);

        List<Plugin> actualPlugins = pluginManagerSpy.resolveDirectDependencies(plugin);

        assertEquals(directDependencyExpectedPlugins, actualPlugins);
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

        assertEquals(directDependencyExpectedPlugins, actualPlugins);
    }


    @Test
    public void resolveDirectDependenciesLatest() {
        PluginManager pluginManagerSpy = spy(pm);
        Plugin plugin = new Plugin("maven-invoker-plugin", "latest", null, null);

        doReturn(directDependencyExpectedPlugins).when(pluginManagerSpy).resolveDependenciesFromJson(nullable(Plugin.class),
                nullable(JSONObject.class));

        List<Plugin> actualPlugins = pluginManagerSpy.resolveDirectDependencies(plugin);

        assertEquals(directDependencyExpectedPlugins, actualPlugins);
    }

    @Test
    public void resolveDirectDependenciesExperimental() {
        PluginManager pluginManagerSpy = spy(pm);
        Plugin plugin = new Plugin("maven-invoker-plugin", "experimental", null, null);

        doReturn(directDependencyExpectedPlugins).when(pluginManagerSpy).resolveDependenciesFromJson(nullable(Plugin.class),
                nullable(JSONObject.class));

        List<Plugin> actualPlugins = pluginManagerSpy.resolveDirectDependencies(plugin);

        assertEquals(directDependencyExpectedPlugins, actualPlugins);
    }

    @Test
    public void resolveDependenciesFromJsonTest() {
        JSONObject json = (JSONObject) setTestUcJson();

        Plugin mavenInvoker = new Plugin("maven-invoker-plugin", "2.4", null, null);
        List<Plugin> actualPlugins = pm.resolveDependenciesFromJson(mavenInvoker, json);

        assertEquals(directDependencyExpectedPlugins, actualPlugins);
    }

    @Test
    public void resolveDependenciesFromJsonLatestSpecifiedTest() throws IOException {
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(Files.createTempDirectory("tmpplugins").toFile())
                .withUseLatestSpecified(true)
                .build();

        PluginManager pluginManager = new PluginManager(config);

        JSONObject testJson = setTestUcJson();
        pluginManager.setLatestUcJson(testJson);
        pluginManager.setLatestUcPlugins(testJson.getJSONObject("plugins"));

        Plugin testWeaver = new Plugin("testweaver", "1.0.1", null, null);
        testWeaver.setLatest(true);
        List<Plugin> actualPlugins = pluginManager.resolveDependenciesFromJson(testWeaver, testJson);

        assertEquals(
                singletonList(new Plugin("structs", "1.19", null, null)),
                actualPlugins);
    }

    @Test
    public void resolveDependenciesFromJsonLatestAll() throws IOException {
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(Files.createTempDirectory("tmpplugins").toFile())
                .withUseLatestAll(true)
                .build();

        PluginManager pluginManager = new PluginManager(config);
        PluginManager pluginManagerSpy = spy(pluginManager);

        JSONObject pluginJson = setTestUcJson();
        Plugin mvnInvokerPlugin = new Plugin("maven-invoker-plugin", "2.4", null, null);

        JSONArray mavenInvokerDependencies = new JSONArray();

        JSONObject workflowApiDependency = new JSONObject();
        workflowApiDependency.put("name", "workflow-api");
        workflowApiDependency.put("optional", "false");
        workflowApiDependency.put("version", "2.22");

        JSONObject workflowStepApi = new JSONObject();
        workflowStepApi.put("name", "workflow-step-api");
        workflowStepApi.put("optional", "false");
        workflowStepApi.put("version", "2.12");

        JSONObject mailer = new JSONObject();
        mailer.put("name", "mailer");
        mailer.put("optional", "false");
        mailer.put("version", "1.18");

        JSONObject scriptSecurity = new JSONObject();
        scriptSecurity.put("name", "script-security");
        scriptSecurity.put("optional", "false");
        scriptSecurity.put("version", "1.30");

        JSONObject structs = new JSONObject();
        structs.put("name", "structs");
        structs.put("optional", "true");
        structs.put("version", "1.7");

        mavenInvokerDependencies.put(workflowApiDependency);
        mavenInvokerDependencies.put(workflowStepApi);
        mavenInvokerDependencies.put(mailer);
        mavenInvokerDependencies.put(scriptSecurity);
        mavenInvokerDependencies.put(structs);

        doReturn(mavenInvokerDependencies).when(pluginManagerSpy).getPluginDependencyJsonArray(any(Plugin.class), any(JSONObject.class));
        doReturn(new VersionNumber("2.44")).doReturn(new VersionNumber("2.30")).doReturn(new VersionNumber("1.18"))
                .doReturn(new VersionNumber("2.0"))
                .when(pluginManagerSpy).getLatestPluginVersion(any(String.class));

        List<Plugin> expectedPlugins = Arrays.asList(
                new Plugin("workflow-api", "2.44", null, null),
                new Plugin("workflow-step-api", "2.30", null, null),
                new Plugin("mailer", "1.18", null, null),
                new Plugin("script-security", "2.0", null, null));

        List<Plugin> actualPlugins = pluginManagerSpy.resolveDependenciesFromJson(mvnInvokerPlugin, pluginJson);

        assertEquals(expectedPlugins, actualPlugins);
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

        Set<Plugin> expectedDependencies = new HashSet<>();
        expectedDependencies.add(grandParent);
        expectedDependencies.add(parent1);
        expectedDependencies.add(child4);  //highest version of replaced1
        expectedDependencies.add(parent3);
        expectedDependencies.add(child2);
        expectedDependencies.add(child3);
        expectedDependencies.add(child8);
        expectedDependencies.add(child9);
        expectedDependencies.add(child5);
        expectedDependencies.add(child6);

        //See Jira JENKINS-58775 - the ideal solution has the following dependencies: grandparent, parent1, child4,
        //parent3, child2, child8, child3, and child9

        Map<String, Plugin> recursiveDependencies = pluginManagerSpy.resolveRecursiveDependencies(grandParent);

        Set<Plugin> actualDependencies = new HashSet<>(recursiveDependencies.values());

        assertEquals(expectedDependencies, actualDependencies);
    }


    @Test
    public void installedPluginsTest() throws IOException {
        File pluginDir = cfg.getPluginDir();

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

        Set<Plugin> expectedPlugins = new HashSet<>(Arrays.asList(
                new Plugin(tmp1name, "1.3.2", null, null),
                new Plugin(tmp2name, "1.8", null, null)));

        Map<String, Plugin> actualPlugins = pm.installedPlugins();

        assertEquals(expectedPlugins, new HashSet<>(actualPlugins.values()));
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
        URL url = PowerMockito.mock(URL.class);

        //File pluginDir = cfg.getPluginDir();
        //File tmp3 = File.createTempFile("test", ".jpi", pluginDir);

        Plugin plugin = new Plugin("pluginName", "pluginVersion", "pluginURL", null);

        JarFile pluginJpi = mock(JarFile.class);

        whenNew(JarFile.class).withAnyArguments().thenReturn(pluginJpi);
        Assert.assertTrue(pm.downloadToFile("downloadURL", plugin, null));
    }

    @Test
    public void getPluginDownloadUrlTest() {
        Plugin plugin = new Plugin("pluginName", "pluginVersion", "pluginURL", null);

        assertEquals("pluginURL", pm.getPluginDownloadUrl(plugin));

        // Note: As of now (2019/12/18 lmm) there is no 'latest' folder in the cloudbees update center as a sibling of the "download" folder
        //  so this is only applicable on jenkins.io
        Plugin pluginNoUrl = new Plugin("pluginName", "latest", null, null);
        String latestUcUrl = "https://updates.jenkins.io/2.176";
        pm.setJenkinsUCLatest(latestUcUrl + "/update-center.json");
        VersionNumber latestVersion = new VersionNumber("latest");
        String latestUrl = latestUcUrl + "/latest/pluginName.hpi";
        Assert.assertEquals(latestUrl, pm.getPluginDownloadUrl(pluginNoUrl));

        Plugin pluginNoVersion = new Plugin("pluginName", null, null, null);
        assertEquals(latestUrl, pm.getPluginDownloadUrl(pluginNoVersion));

        Plugin pluginExperimentalVersion = new Plugin("pluginName", "experimental", null, null);
        String experimentalUrl = dirName(cfg.getJenkinsUcExperimental()) + "latest/pluginName.hpi";
        Assert.assertEquals(experimentalUrl, pm.getPluginDownloadUrl(pluginExperimentalVersion));

        Plugin pluginIncrementalRepo = new Plugin("pluginName", "2.19-rc289.d09828a05a74", null, "org.jenkins-ci.plugins.pluginName");

        String incrementalUrl = cfg.getJenkinsIncrementalsRepoMirror() +
                "/org/jenkins-ci/plugins/pluginName/pluginName/2.19-rc289.d09828a05a74/pluginName-2.19-rc289.d09828a05a74.hpi";

        assertEquals(incrementalUrl, pm.getPluginDownloadUrl(pluginIncrementalRepo));

        Plugin pluginOtherVersion = new Plugin("pluginName", "otherversion", null, null);
        String otherURL = dirName(cfg.getJenkinsUc().toString()) +
                "download/plugins/pluginName/otherversion/pluginName.hpi";
        assertEquals(otherURL, pm.getPluginDownloadUrl(pluginOtherVersion));
    }

    @Test
    public void getDownloadPluginUrlTestComplexUpdateCenterUrl() throws IOException {
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(Files.createTempDirectory("tmpplugins").toFile())
                .withJenkinsUc(new URL("https://jenkins-updates.cloudbees.com/update-center/envelope-cje/update-center.json?version=2.176.4.3"))
                .build();

        PluginManager pluginManager = new PluginManager(config);

        Plugin plugin = new Plugin("the-plugin", "1.0", null, null);

        String result = pluginManager.getPluginDownloadUrl(plugin);

        assertThat(result, is("https://jenkins-updates.cloudbees.com/download/plugins/the-plugin/1.0/the-plugin.hpi"));
    }

    @Test
    public void getAttributeFromManifestExceptionTest() throws Exception {
        URL jpiURL = this.getClass().getResource("/delivery-pipeline-plugin.jpi");
        File testJpi = new File(jpiURL.getFile());

        whenNew(JarFile.class).withArguments(testJpi).thenThrow(new IOException());

        assertThrows(
                DownloadPluginException.class,
                () -> pm.getAttributeFromManifest(testJpi, "Plugin-Dependencies"));
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

        assertEquals(value, pm.getAttributeFromManifest(testJpi, "key"));
    }

    public void showAllSecurityWarningsNoOutput() throws Exception {
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(Files.createTempDirectory("plugins").toFile())
                .withShowAllWarnings(false)
                .build();

        PluginManager pluginManager = new PluginManager(config);

        String output = tapSystemOutNormalized(
                pluginManager::showAllSecurityWarnings);

        assertEquals("", output);
    }


    public void showAllSecurityWarnings() throws Exception {
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(Files.createTempDirectory("plugins").toFile())
                .withShowAllWarnings(true)
                .build();

        PluginManager pluginManager = new PluginManager(config);

        pluginManager.setLatestUcJson(setTestUcJson());

        String output = tapSystemOutNormalized(
                pluginManager::showAllSecurityWarnings);

        String expectedOutput = "google-login - Authentication bypass vulnerability\n" +
                "cucumber-reports - Plugin disables Content-Security-Policy for files served by Jenkins\n" +
                "pipeline-maven - Arbitrary files from Jenkins master available in Pipeline by using the withMaven step\n" +
                "pipeline-maven - XML External Entity processing vulnerability\n";

        assertEquals(expectedOutput, output);
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
        assertEquals(new VersionNumber("2.164.1").compareTo(pluginManager.getJenkinsVersionFromWar()), 0);
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

        Set<Plugin> expectedPlugins = new HashSet<>(Arrays.asList(
                new Plugin("credentials","2.1.18", null, null),
                new Plugin("display-url-api","2.0", null, null),
                new Plugin("github-branch-source", "1.8", null, null)));

        Map<String, Plugin> actualPlugins = pluginManager.bundledPlugins();

        assertEquals(expectedPlugins, new HashSet<>(actualPlugins.values()));
    }

    private JSONObject setTestUcJson() {
        JSONObject latestUcJson = new JSONObject();

        JSONObject pluginJson = new JSONObject();
        latestUcJson.put("plugins", pluginJson);

        JSONObject structsPlugin = new JSONObject();
        JSONArray structsDependencies = new JSONArray();

        JSONObject credentials = new JSONObject();
        credentials.put("name", "scm-api");
        credentials.put("optional", "false");
        credentials.put("version", "2.2.6");

        structsDependencies.put(credentials);
        structsPlugin.put("dependencies", structsDependencies);
        structsPlugin.put("version", "1.19");
        structsPlugin.put("requiredCore", "2.60.3");
        pluginJson.put("structs", structsPlugin);

        JSONObject testWeaverPlugin = new JSONObject();
        JSONArray testWeaverDependencies = new JSONArray();

        JSONObject structsDependency = new JSONObject();
        structsDependency.put("name", "structs");
        structsDependency.put("optional", "false");
        structsDependency.put("version", "1.7");

        testWeaverDependencies.put(structsDependency);
        testWeaverPlugin.put("dependencies", testWeaverDependencies);
        testWeaverPlugin.put("version", "1.0.1");
        testWeaverPlugin.put("requiredCore", "2.7.3");
        pluginJson.put("testweaver", testWeaverPlugin);

        JSONObject mavenInvokerPlugin = new JSONObject();

        JSONArray mavenInvokerDependencies = new JSONArray();

        JSONObject workflowApiDependency = new JSONObject();
        workflowApiDependency.put("name", "workflow-api");
        workflowApiDependency.put("optional", "false");
        workflowApiDependency.put("version", "2.22");

        JSONObject workflowStepApi = new JSONObject();
        workflowStepApi.put("name", "workflow-step-api");
        workflowStepApi.put("optional", "false");
        workflowStepApi.put("version", "2.12");

        JSONObject mailer = new JSONObject();
        mailer.put("name", "mailer");
        mailer.put("optional", "false");
        mailer.put("version", "1.18");

        JSONObject scriptSecurity = new JSONObject();
        scriptSecurity.put("name", "script-security");
        scriptSecurity.put("optional", "false");
        scriptSecurity.put("version", "1.30");

        JSONObject structs = new JSONObject();
        structs.put("name", "structs");
        structs.put("optional", "true");
        structs.put("version", "1.7");

        mavenInvokerDependencies.put(workflowApiDependency);
        mavenInvokerDependencies.put(workflowStepApi);
        mavenInvokerDependencies.put(mailer);
        mavenInvokerDependencies.put(scriptSecurity);
        mavenInvokerDependencies.put(structs);
        mavenInvokerPlugin.put("dependencies", mavenInvokerDependencies);
        mavenInvokerPlugin.put("version", "2.4");
        mavenInvokerPlugin.put("requiredCore", "2.89.4");

        pluginJson.put("maven-invoker-plugin", mavenInvokerPlugin);

        JSONObject amazonEcsPlugin = new JSONObject();
        JSONArray amazonEcsPluginDependencies = new JSONArray();

        JSONObject apacheHttpComponentsClientApi = new JSONObject();
        apacheHttpComponentsClientApi.put("name", "workflow-step-api");
        apacheHttpComponentsClientApi.put("optional", false);
        apacheHttpComponentsClientApi.put("version", "4.5.5-3.0");

        JSONObject awsCredentials = new JSONObject();
        awsCredentials.put("name", "aws-credentials");
        awsCredentials.put("optional", false);
        awsCredentials.put("version", "1.23");

        amazonEcsPluginDependencies.put(workflowStepApi);
        amazonEcsPluginDependencies.put(apacheHttpComponentsClientApi);
        amazonEcsPluginDependencies.put(awsCredentials);

        amazonEcsPlugin.put("dependencies", amazonEcsPluginDependencies);
        amazonEcsPlugin.put("version", "1.20");
        amazonEcsPlugin.put("requiredCore", "2.107.3");

        pluginJson.put("amazon-ecs", amazonEcsPlugin);

        JSONObject antPlugin = new JSONObject();
        JSONArray antPluginDependencies = new JSONArray();
        antPluginDependencies.put(structs);

        antPlugin.put("dependencies", antPluginDependencies);
        antPlugin.put("version", "1.9");
        antPlugin.put("requiredCore", "2.121.2");

        pluginJson.put("ant", antPlugin);

        JSONObject signatureJSON = new JSONObject();
        latestUcJson.put("signature", signatureJSON);

        latestUcJson.put("updateCenterVersion", "1");

        JSONArray warningArray = new JSONArray();

        JSONObject security208 = new JSONObject();
        security208.put("id", "SECURITY-208");
        security208.put("message", "Authentication bypass vulnerability");
        security208.put("name", "google-login");
        security208.put("type", "plugin");
        security208.put("url", "https://jenkins.io/security/advisory/2015-10-12/");
        JSONArray versionArray208 = new JSONArray();
        JSONObject version208 = new JSONObject();
        version208.put("lastVersion", "1.1");
        version208.put("pattern", "1[.][01](|[.-].*)");
        versionArray208.put(version208);
        security208.put("versions", versionArray208);
        versionArray208.put(version208);

        warningArray.put(security208);

        JSONObject security309 = new JSONObject();
        security309.put("id", "SECURITY-309");
        security309.put("message", "Plugin disables Content-Security-Policy for files served by Jenkins");
        security309.put("name", "cucumber-reports");
        security309.put("type", "plugin");
        security309.put("url", "https://jenkins.io/security/advisory/2016-07-27/");

        JSONArray versionArray309 = new JSONArray();
        JSONObject version309 = new JSONObject();
        version309.put("firstVersion", "1.3.0");
        version309.put("lastVersion", "2.5.1");
        version309.put("pattern", "(1[.][34]|2[.][012345])(|[.-].*)");
        versionArray309.put(version309);
        security309.put("versions", versionArray309);

        warningArray.put(security309);

        JSONObject core = new JSONObject();
        core.put("id", "core-2_44");
        core.put("message", "Multiple security vulnerabilities in Jenkins 2.43 and earlier, and LTS 2.32.1 and earlier");
        core.put("name", "core");
        core.put("type", "core");
        core.put("url", "https://jenkins.io/security/advisory/2017-02-01/");

        JSONArray coreVersionArray = new JSONArray();
        JSONObject coreVersion = new JSONObject();
        coreVersion.put("lastVersion", "2.43");
        coreVersion.put("pattern", "(1[.].*|2[.]\\d|2[.][123]\\d|2[.]4[0123])(|[-].*)");
        coreVersionArray.put(coreVersion);
        core.put("versions", coreVersionArray);

        warningArray.put(core);

        JSONObject security441 = new JSONObject();
        security441.put("id", "SECURITY-441");
        security441.put("message", "Arbitrary files from Jenkins master available in Pipeline by using the withMaven step");
        security441.put("name", "pipeline-maven");
        security441.put("type", "plugin");
        security441.put("url", "https://jenkins.io/security/advisory/2017-03-09/");
        JSONArray versionArray441 = new JSONArray();
        JSONObject firstVersions441 = new JSONObject();
        firstVersions441.put("lastVersion", "0.6");
        firstVersions441.put("pattern", "0[.][123456](|[.-].*)");
        JSONObject laterVersions441 = new JSONObject();
        laterVersions441.put("lastVersion", "2.0-beta-5");
        laterVersions441.put("pattern", "2[.]0[-]beta[-][345](|[.-].*)");
        versionArray441.put(firstVersions441);
        versionArray441.put(laterVersions441);
        security441.put("versions", versionArray441);

        warningArray.put(security441);

        JSONObject security1409 = new JSONObject();
        security1409.put("id", "SECURITY-1409");
        security1409.put("message", "XML External Entity processing vulnerability");
        security1409.put("name", "pipeline-maven");
        security1409.put("type", "plugin");
        security1409.put("url", "https://jenkins.io/security/advisory/2019-05-31/#SECURITY-1409");

        JSONArray versionArray1409 = new JSONArray();
        JSONObject version1409 = new JSONObject();
        version1409.put("lastVersion", "3.7.0");
        version1409.put("pattern", "([0-2]|3[.][0-6]|3[.]7[.]0)(|[.-].*)");
        versionArray1409.put(version1409);
        security1409.put("versions", versionArray1409);
        warningArray.put(security1409);

        latestUcJson.put("warnings", warningArray);

        pm.setLatestUcJson(latestUcJson);
        pm.setLatestUcPlugins(pluginJson);
        return latestUcJson;
    }
}
