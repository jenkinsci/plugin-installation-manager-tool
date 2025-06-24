package io.jenkins.tools.pluginmanager.impl;

import hudson.util.VersionNumber;
import io.jenkins.tools.pluginmanager.config.Config;
import io.jenkins.tools.pluginmanager.config.OutputFormat;
import io.jenkins.tools.pluginmanager.config.Settings;
import io.jenkins.tools.pluginmanager.parsers.StdOutPluginOutputConverter;
import io.jenkins.tools.pluginmanager.util.ManifestTools;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErrNormalized;
import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOutNormalized;
import static io.jenkins.tools.pluginmanager.util.PluginManagerUtils.dirName;
import static java.nio.file.Files.createDirectory;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

class PluginManagerTest {

    private PluginManager pm;
    private Config cfg;
    private List<Plugin> directDependencyExpectedPlugins;

    @TempDir
    private File folder;

    @BeforeEach
    void setUp() {
        cfg = Config.builder()
            .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(new File(folder, "plugins"))
                .withCachePath(newFolder(folder, "junit").toPath())
                .build();

        pm = new PluginManager(cfg);

        directDependencyExpectedPlugins = Arrays.asList(
            new Plugin("workflow-api", "2.22", null, null),
            new Plugin("workflow-step-api", "2.12", null, null),
            new Plugin("mailer", "1.18", null, null),
            new Plugin("script-security", "1.30", null, null),
            new Plugin("structs", "1.7", null, null).setOptional(true)
        );
    }

    @Test
    void startTest() {
        Config config = Config.builder()
                .withPluginDir(new File(folder, "plugins"))
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withJenkinsUc(Settings.DEFAULT_UPDATE_CENTER)
                .withPlugins(new ArrayList<>())
                .withDoDownload(true)
                .withJenkinsUcExperimental(Settings.DEFAULT_EXPERIMENTAL_UPDATE_CENTER)
                .build();

        PluginManager pluginManager = new PluginManager(config);

        PluginManager pluginManagerSpy = spy(pluginManager);

        doNothing().when(pluginManagerSpy).createPluginDir(true);
        VersionNumber versionNumber = new VersionNumber("2.182");
        doReturn(versionNumber).when(pluginManagerSpy).getJenkinsVersionFromWar();
        doNothing().when(pluginManagerSpy).getUCJson(versionNumber);
        doReturn(new HashMap<>()).when(pluginManagerSpy).getSecurityWarnings();
        doNothing().when(pluginManagerSpy).showAllSecurityWarnings();

        doReturn(new HashMap<>()).when(pluginManagerSpy).bundledPlugins();
        doReturn(new HashMap<>()).when(pluginManagerSpy).installedPlugins();
        doReturn(new HashMap<>()).when(pluginManagerSpy).findPluginsAndDependencies(anyList());
        doReturn(new ArrayList<>()).when(pluginManagerSpy).findPluginsToDownload(anyMap());
        doReturn(new HashMap<>()).when(pluginManagerSpy).findEffectivePlugins(anyList());
        doNothing().when(pluginManagerSpy).listPlugins();
        doNothing().when(pluginManagerSpy).showSpecificSecurityWarnings(anyList());
        doNothing().when(pluginManagerSpy).checkVersionCompatibility(any(), anyList());
        doNothing().when(pluginManagerSpy).downloadPlugins(anyList());

        pluginManagerSpy.start();
    }

    @Test
    void startNoDirectoryTest() throws Exception {
        // by using a file as the parent dir of the plugin folder we force that
        // the plugin folder cannot be created
        File pluginParentDir = File.createTempFile("junit", null, folder);
        Config config = Config.builder()
                .withPluginDir(new File(pluginParentDir, "plugins"))
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withJenkinsUc(Settings.DEFAULT_UPDATE_CENTER)
                .withDoDownload(true)
                .build();

        PluginManager pluginManager = new PluginManager(config);

        assertThatThrownBy(pluginManager::start)
                .isInstanceOf(DirectoryCreationException.class);
    }

    @Test
    void startNoDirectoryNoPluginDirTest() throws Exception {
        File pluginParentDir = File.createTempFile("junit", null, folder);
        Config config = Config.builder()
                .withPluginDir(new File(pluginParentDir, "plugins"))
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withJenkinsUc(Settings.DEFAULT_UPDATE_CENTER)
                .withDoDownload(false)
                .build();

        PluginManager pluginManager = new PluginManager(config);
        pluginManager.start();
        // everything should be ok.
    }

    @Test
    void findEffectivePluginsTest() {
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

        assertAll(
                () -> assertThat(effectivePlugins.get("git").getVersion())
                        .hasToString("1.3"),
                () -> assertThat(effectivePlugins.get("scm-api").getVersion())
                        .hasToString("2.2.3"),
                () -> assertThat(effectivePlugins.get("aws-credentials").getVersion())
                        .hasToString("1.24"),
                () -> assertThat(effectivePlugins.get("p4").getVersion())
                        .hasToString("1.3.3"),
                () -> assertThat(effectivePlugins.get("script-security").getVersion())
                        .hasToString("1.26"),
                () -> assertThat(effectivePlugins.get("credentials").getVersion())
                        .hasToString("2.1.11"),
                () -> assertThat(effectivePlugins.get("ace-editor").getVersion())
                        .hasToString("1.0.1")
        );
    }

    @Test
    void listPluginsNoOutputTest() throws Exception {
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(new File(folder, "plugins"))
                .withShowPluginsToBeDownloaded(false)
                .build();

        PluginManager pluginManager = new PluginManager(config);

        String output = tapSystemOutNormalized(
                pluginManager::listPlugins);

        assertThat(output).isEmpty();
    }

    @Test
    void listPluginsOutputTest() throws Exception {
         Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(new File(folder, "plugins"))
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

        String stdOutput = tapSystemOutNormalized(pluginManager::listPlugins);
        String stdErr = tapSystemErrNormalized(pluginManager::listPlugins);

        // CHECKSTYLE:OFF
        assertThat(stdErr).isEqualTo(
            """
                
                Installed plugins:
                installed1 1.0
                installed2 2.0
                
                Bundled plugins:
                bundled1 1.0
                bundled2 2.0
                
                All requested plugins:
                dependency1 1.0.0
                dependency2 1.0.0
                plugin1 1.0
                plugin2 2.0
                
                Plugins that will be downloaded:
                dependency1 1.0.0
                dependency2 1.0.0
                plugin1 1.0
                plugin2 2.0
                
                """);

        assertThat(stdOutput).isEqualTo("""
            Resulting plugin list:
            bundled1 1.0
            bundled2 2.0
            dependency1 1.0.0
            dependency2 1.0.0
            installed1 1.0
            installed2 2.0
            plugin1 1.0
            plugin2 2.0
            
            """);
        // CHECKSTYLE:ON
    }

    @Test
    void listPluginsOutputYamlTest() throws Exception {
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(new File(folder, "plugins"))
                .withOutputFormat(OutputFormat.YAML)
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
        pluginManager.setPluginsToBeDownloaded(Arrays.asList(plugin1, plugin2, dependency1, dependency2));
        pluginManager.setEffectivePlugins(effectivePlugins);

        String stdOutput = tapSystemOutNormalized(pluginManager::listPlugins);
        String stdErr = tapSystemErrNormalized(pluginManager::listPlugins);

        // CHECKSTYLE:OFF
        assertThat(stdErr).isEqualTo(
            """
                
                Installed plugins:
                installed1 1.0
                installed2 2.0
                
                Bundled plugins:
                bundled1 1.0
                bundled2 2.0
                
                All requested plugins:
                dependency1 1.0.0
                dependency2 1.0.0
                plugin1 1.0
                plugin2 2.0
                
                Plugins that will be downloaded:
                dependency1 1.0.0
                dependency2 1.0.0
                plugin1 1.0
                plugin2 2.0
                
                """);

        assertThat(stdOutput).isEqualTo("""
            plugins:
            - artifactId: "bundled1"
              source:
                version: "1.0"
            - artifactId: "bundled2"
              source:
                version: "2.0"
            - artifactId: "dependency1"
              source:
                version: "1.0.0"
            - artifactId: "dependency2"
              source:
                version: "1.0.0"
            - artifactId: "installed1"
              source:
                version: "1.0"
            - artifactId: "installed2"
              source:
                version: "2.0"
            - artifactId: "plugin1"
              source:
                version: "1.0"
            - artifactId: "plugin2"
              source:
                version: "2.0"
            
            """);
        // CHECKSTYLE:ON
    }

    @Test
    void outputPluginsListYamlTest() throws Exception {
        Plugin a = new Plugin("a", "1.0", null, null);
        Plugin b = new Plugin("b", "2.0", null, null);
        Plugin c = new Plugin("c", "3.0", null, null);
        List<Plugin> plugins = Arrays.asList(a, b, c);

        cfg = Config.builder()
                .withOutputFormat(OutputFormat.YAML)
                .build();
        pm = new PluginManager(cfg);
        String stdout = tapSystemOutNormalized(() ->
            pm.outputPluginList(plugins, () -> new StdOutPluginOutputConverter("test plugins")));
        // CHECKSTYLE:OFF
        assertThat(stdout).isEqualTo("""
            plugins:
            - artifactId: "a"
              source:
                version: "1.0"
            - artifactId: "b"
              source:
                version: "2.0"
            - artifactId: "c"
              source:
                version: "3.0"
            
            """);
        // CHECKSTYLE:ON
    }

    @Test
    void outputPluginsListTextTest() throws Exception {
        Plugin a = new Plugin("a", "1.0", null, null);
        Plugin b = new Plugin("b", "2.0", null, null);
        Plugin c = new Plugin("c", "3.0", null, null);
        List<Plugin> plugins = Arrays.asList(a, b, c);

        cfg = Config.builder()
                .withOutputFormat(OutputFormat.TXT)
                .build();
        pm = new PluginManager(cfg);
        String stdout = tapSystemOutNormalized(() ->
            pm.outputPluginList(plugins, () -> new StdOutPluginOutputConverter("test plugins")));
        assertThat(stdout).isEqualTo("""
            a:1.0
            b:2.0
            c:3.0
            """);
    }

    @Test
    void outputPluginsListStdOutTest() throws Exception {
        Plugin a = new Plugin("a", "1.0", null, null);
        Plugin b = new Plugin("b", "2.0", null, null);
        Plugin c = new Plugin("c", "3.0", null, null);
        List<Plugin> plugins = Arrays.asList(a, b, c);

        cfg = Config.builder()
                .withOutputFormat(OutputFormat.STDOUT)
                .build();
        pm = new PluginManager(cfg);
        String stdout = tapSystemOutNormalized(() ->
            pm.outputPluginList(plugins, () -> new StdOutPluginOutputConverter("test plugins")));
        // CHECKSTYLE:OFF
        assertThat(stdout).isEqualTo("""
            test plugins
            a 1.0
            b 2.0
            c 3.0
            
            """);
        // CHECKSTYLE:ON
    }

    @Test
    void getPluginDependencyJsonArrayTest1() {
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
    void getPluginDependencyJsonArrayTest2() {
        //test plugin-version json, which has a different format
        JSONObject pluginVersionJson = new JSONObject();
        JSONObject plugins = new JSONObject();

        JSONObject browserStackIntegration= new JSONObject();
        JSONObject browserStack1 = new JSONObject();
        browserStack1.put("requiredCore", "1.580.1");

        JSONArray dependencies1 = array(
                dependency("credentials", false, "1.8"),
                dependency("junit", false, "1.10"));
        browserStack1.put("sha256", "browser stack checksum");
        browserStack1.put("dependencies", dependencies1);
        browserStackIntegration.put("1.0.0", browserStack1);

        JSONObject browserStack111 = new JSONObject();
        browserStack111.put("requiredCore", "1.580.1");

        JSONArray dependencies111 = array(
                dependency("credentials", false, "1.8"),
                dependency("junit", false, "1.10"));
        browserStack111.put("sha256", "browser stack checksum");
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
        browserStack112.put("sha256", "browser stack checksum");
        browserStack112.put("dependencies", dependencies112);
        browserStackIntegration.put("1.1.2", browserStack112);

        plugins.put("browserstack-integration", browserStackIntegration);

        pluginVersionJson.put("plugins", plugins);

        Plugin gitPlugin = new Plugin("git", "1.17", null, null);
        JSONArray gitJson = pm.getPluginDependencyJsonArray(gitPlugin, pluginVersionJson);

        assertThat(gitJson).isNull();

        pm.setPluginInfoJson(pluginVersionJson);

        assertAll(
                () -> {
                    Plugin browserStackPlugin100 = new Plugin("browserstack-integration", "1.0.0", null, null);
                    browserStackPlugin100.setChecksum("1234");
                    JSONArray browserStackPluginJson1 = pm.getPluginDependencyJsonArray(browserStackPlugin100, pluginVersionJson);
                    assertThat(browserStackPluginJson1)
                            .hasToString(dependencies1.toString());
                },
                () -> {
                    Plugin browserStackPlugin111 = new Plugin("browserstack-integration", "1.1.1", null, null);
                    JSONArray browserStackPluginJson111 = pm.getPluginDependencyJsonArray(browserStackPlugin111, pluginVersionJson);
                    assertThat(browserStackPluginJson111)
                            .hasToString(dependencies111.toString());
                },
                () -> {
                    Plugin browserStackPlugin112 = new Plugin("browserstack-integration", "1.1.2", null, null);
                    JSONArray browserStackPluginJson112 = pm.getPluginDependencyJsonArray(browserStackPlugin112, pluginVersionJson);
                    assertThat(browserStackPluginJson112)
                            .hasToString(dependencies112.toString());
                }
        );
    }

    @Test
    void getSecurityWarningsTest() {
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
    void getSecurityWarningsWhenNoWarningsTest() {
        JSONObject json = setTestUcJson();
        json.remove("warnings");
        assertThat(json.has("warnings")).isFalse();

        Map<String, List<SecurityWarning>> allSecurityWarnings = pm.getSecurityWarnings();

        assertThat(allSecurityWarnings).isEmpty();
    }

    @Test
    void warningExistsTest() {
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
        Plugin sshAgents1 = new Plugin("ssh-slaves", "0.9", null, null);
        Plugin sshAgents2 = new Plugin("ssh-slaves", "9.2", null, null);

        assertAll(
                () -> assertThat(pm.warningExists(scriptler)).isTrue(),
                () -> assertThat(pm.warningExists(lockableResource)).isTrue(),
                () -> assertThat(pm.warningExists(lockableResource2)).isFalse(),
                () -> assertThat(pm.warningExists(cucumberReports1)).isFalse(),
                () -> assertThat(pm.warningExists(cucumberReports2)).isTrue(),
                //() -> assertThat(pm.warningExists(cucumberReports3)).isFalse(),
                // currently fails since 2.5.3 matches pattern even though 2.5.1 is last effected version
                () -> assertThat(pm.warningExists(sshAgents1)).isTrue(),
                () -> assertThat(pm.warningExists(sshAgents2)).isFalse()
        );
    }

    @Test
    void getPluginsFilterOptionalTest() {
         Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withUseLatestSpecified(true)
                .build();

        PluginManager pluginManager = new PluginManager(config);
        PluginManager pluginManagerSpy = spy(pluginManager);

        JSONObject testJson = setTestUcJson();
        pluginManagerSpy.setLatestUcJson(testJson);
        pluginManagerSpy.setLatestUcPlugins(testJson.getJSONObject("plugins"));

        Plugin plugin1 = new Plugin("plugin1", "1.0", null, null);
        Map<String, Plugin> deps1 = new HashMap<>();
        // structs at v1.8 will be non-optional  in the end because
        // plugin2 has a real dependency on structs and plugin1 requires
        // that the version be >= 1.8.
        deps1.put("structs", new Plugin("structs", "1.8", null, null).setOptional(true));
        // matrix will not be in the resulting dependency set because all
        // dependencies on it are optional.
        deps1.put("matrix", new Plugin("matrix", "2.5", null, null).setOptional(true));
        doReturn(deps1).when(pluginManagerSpy).resolveRecursiveDependencies(
            eq(plugin1), nullable(Map.class), eq(null));

        Plugin plugin2 = new Plugin("plugin2", "2.1.1", null, null);
        Map<String, Plugin> deps2 = new HashMap<>();
        deps2.put("structs", new Plugin("structs", "1.7", null, null));
        deps2.put("matrix", new Plugin("matrix", "2.5", null, null).setOptional(true));
        doReturn(deps2).when(pluginManagerSpy).resolveRecursiveDependencies(
            eq(plugin2), nullable(Map.class), eq(null));

        Map<String, Plugin> dependencies = pluginManagerSpy.findPluginsAndDependencies(
                Arrays.asList(plugin1, plugin2));

        assertThat(dependencies)
            .hasSize(3)
            .containsValues(
                    plugin1, plugin2,
                    new Plugin("structs", "1.8", null, null));
    }

    @Test
    void checkVersionCompatibilityNullTest() {
        Plugin plugin1 = new Plugin("plugin1", "1.0", null, null);
        plugin1.setJenkinsVersion("2.121.2");

        Plugin plugin2 = new Plugin("plugin2", "2.1.1", null, null);
        plugin2.setJenkinsVersion("1.609.3");

        //check passes if no exception is thrown
        pm.checkVersionCompatibility(null, Arrays.asList(plugin1, plugin2));
    }

    @Test
    void checkVersionCompatibilityFailTest() {
        Plugin plugin1 = new Plugin("plugin1", "1.0", null, null);
        plugin1.setJenkinsVersion("2.121.2");

        Plugin plugin2 = new Plugin("plugin2", "2.1.1", null, null);
        plugin2.setJenkinsVersion("1.609.3");

        List<Plugin> pluginsToDownload = new ArrayList<>(Arrays.asList(plugin1, plugin2));

        assertThatThrownBy(() -> pm.checkVersionCompatibility(new VersionNumber("1.609.3"), pluginsToDownload))
                .isInstanceOf(VersionCompatibilityException.class);
    }

    @Test
    void checkVersionCompatibilityPassTest() {
        Plugin plugin1 = new Plugin("plugin1", "1.0", null, null);
        plugin1.setJenkinsVersion("2.121.2");

        Plugin plugin2 = new Plugin("plugin2", "2.1.1", null, null);
        plugin2.setJenkinsVersion("1.609.3");

        //check passes if no exception is thrown
        pm.checkVersionCompatibility(new VersionNumber("2.121.2"), Arrays.asList(plugin1, plugin2));
    }

    @Test
    void downloadPluginsUnsuccessfulTest() {
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(new File(folder, "plugins"))
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
    void downloadPluginAlreadyInstalledTest() {
        Map<String, Plugin> installedVersions = new HashMap<>();
        installedVersions.put("plugin1", new Plugin("plugin1", "1.0", null, null));
        installedVersions.put("plugin2", new Plugin("plugin2", "2.0", null, null));

        pm.setInstalledPluginVersions(installedVersions);

        assertThat(pm.downloadPlugin(new Plugin("plugin1", "1.0", null, null), null))
                .isTrue();
    }

    @Test
    void downloadPluginSuccessfulFirstAttempt() {
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(new File(folder, "plugins"))
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
    }

    @Test
    void outputPluginReplacementInfoTest() throws Exception {
        String output = tapSystemErrNormalized(() -> {
            Config config = Config.builder()
                    .withJenkinsWar(Settings.DEFAULT_WAR)
                    .withPluginDir(new File(folder, "plugins"))
                    .withIsVerbose(true)
                    .build();

            PluginManager pluginManager = new PluginManager(config);
            Plugin lowerVersion = new Plugin("plugin1", "1.0", null, null);
            Plugin lowerVersionParent = new Plugin("plugin1parent1", "1.0.0", null, null);
            Plugin higherVersion = new Plugin("plugin1", "2.0", null, null);
            Plugin highVersionParent = new Plugin("plugin1parent2", "2.0.0", null, null);
            lowerVersion.setParent(lowerVersionParent);
            higherVersion.setParent(highVersionParent);

            pluginManager.outputPluginReplacementInfo(lowerVersion, higherVersion);
        });

        assertThat(output).isEqualTo(
                "Version of plugin1 (1.0) required by plugin1parent1 (1.0.0) is lower than the version " +
                        "required (2.0) by plugin1parent2 (2.0.0), upgrading required plugin version\n");
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecatedGetJsonThrowsExceptionForMalformedURL() {
        assertThatThrownBy(() -> pm.getJson("htttp://ftp-chi.osuosl.org/pub/jenkins/updates/current/update-center.json"))
                .isInstanceOf(UpdateCenterInfoRetrievalException.class)
                .hasMessage("Malformed url for update center");
    }

    @Test
    void getJsonThrowsExceptionWhenUrlDoesNotExists() {
        assertThatThrownBy(() -> pm.getJson(new File("does/not/exist").toURI().toURL(), "update-center"))
                .isInstanceOf(UpdateCenterInfoRetrievalException.class)
                .hasMessage("Error getting update center json");
    }

    @Test
    void findPluginsToDownloadTest() {
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
    void getPluginVersionTest() {
        URL jpiURL = this.getClass().getResource("/delivery-pipeline-plugin.jpi");
        File testJpi = new File(jpiURL.getFile());

        assertThat(pm.getPluginVersion(testJpi)).isEqualTo("1.3.2");

        URL hpiURL = this.getClass().getResource("/ssh-credentials.hpi");
        File testHpi = new File(hpiURL.getFile());

        assertThat(pm.getPluginVersion(testHpi)).isEqualTo("1.10");
    }

    @Test
    void getLatestPluginVersionExceptionTest() {
        setTestUcJson();

        assertThatThrownBy(() -> pm.getLatestPluginVersion(null, "git"))
                .isInstanceOf(PluginNotFoundException.class);
    }

    @Test
    void getLatestPluginTest() {
        setTestUcJson();
        VersionNumber antLatestVersion = pm.getLatestPluginVersion(null, "ant");
        assertThat(antLatestVersion).hasToString("1.9");

        VersionNumber amazonEcsLatestVersion = pm.getLatestPluginVersion(null, "amazon-ecs");
        assertThat(amazonEcsLatestVersion).hasToString("1.20");
    }

    @Test
    void resolveDependenciesFromManifestExceptionTest() {
        Plugin testPlugin = new Plugin("test", "latest", null, null);
        setTestUcJson();
        assertThatThrownBy(() -> pm.resolveDependenciesFromManifest(testPlugin)).isInstanceOf(DownloadPluginException.class)
        .hasMessageContaining("Unable to resolve dependencies");
    }

    @Test
    void resolveDependenciesFromHudsonManifest() {
        PluginManager pluginManagerSpy = spy(pm);

        Plugin testPlugin = new Plugin("test", "1.0", null, null);
        doReturn(true).when(pluginManagerSpy).downloadPlugin(any(Plugin.class), any(File.class));

        doReturn(null).when(pluginManagerSpy).getAttributeFromManifest(any(File.class), any(String.class));
        doReturn("1.2.0").when(pluginManagerSpy).getAttributeFromManifest(any(File.class), eq("Hudson-Version"));
        pluginManagerSpy.resolveDependenciesFromManifest(testPlugin);

        assertThat(testPlugin.getJenkinsVersion()).hasToString("1.2.0");
    }

    @Test
    void resolveDependenciesFromInvalidManifest() {
        PluginManager pluginManagerSpy = spy(pm);

        Plugin testPlugin = new Plugin("test", "1.0", null, null);
        doReturn(true).when(pluginManagerSpy).downloadPlugin(any(Plugin.class), any(File.class));

        doReturn(null).when(pluginManagerSpy).getAttributeFromManifest(any(File.class), any(String.class));

        assertThatThrownBy(() -> pluginManagerSpy.resolveDependenciesFromManifest(testPlugin))
        .isInstanceOf(PluginDependencyException.class)
        .hasMessageContaining("does not contain a Jenkins-Version attribute");
    }

    @Test
    void resolveDependenciesFromManifestDownload() {
        PluginManager pluginManagerSpy = spy(pm);

        Plugin testPlugin = new Plugin("test", "latest", null, null);
        doReturn(true).when(pluginManagerSpy).downloadPlugin(any(Plugin.class), any(File.class));

        Path tempPath = mock(Path.class);
        File tempFile = mock(File.class);

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
                        new Plugin("parameterized-trigger", "2.33", null, null).setOptional(true),
                        new Plugin("promoted-builds", "2.27", null, null).setOptional(true),
                        new Plugin("scm-api", "2.6.3", null, null),
                        new Plugin("ssh-credentials", "1.13", null, null),
                        new Plugin("token-macro", "1.12.1", null, null).setOptional(true));
        assertThat(testPlugin.getVersion()).hasToString("1.0.0");
    }

    @Test
    void resolveDirectDependenciesManifestTest1() {
        PluginManager pluginManagerSpy = spy(pm);
        Plugin plugin = new Plugin("maven-invoker-plugin", "2.4", "url", null);

        doReturn(directDependencyExpectedPlugins).when(pluginManagerSpy).resolveDependenciesFromManifest(plugin);

        List<Plugin> actualPlugins = pluginManagerSpy.resolveDirectDependencies(plugin);

        assertThat(actualPlugins).isEqualTo(directDependencyExpectedPlugins);
    }

    @Test
    void resolveDirectDependenciesManifestTest2() {
        PluginManager pluginManagerSpy = spy(pm);
        Plugin plugin = new Plugin("maven-invoker-plugin", "2.19-rc289.d09828a05a74", null,
                "org.jenkins-ci.plugins.workflow");

        doReturn(directDependencyExpectedPlugins).when(pluginManagerSpy).resolveDependenciesFromManifest(plugin);

        List<Plugin> actualPlugins = pluginManagerSpy.resolveDirectDependencies(plugin);

        assertThat(actualPlugins).isEqualTo(directDependencyExpectedPlugins);
    }

    @Test
    void resolveDirectDependenciesManifestTest3() {
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
    void resolveDirectDependenciesLatest() {
        PluginManager pluginManagerSpy = spy(pm);
        Plugin plugin = new Plugin("maven-invoker-plugin", "latest", null, null);

        doReturn(directDependencyExpectedPlugins).when(pluginManagerSpy).resolveDependenciesFromJson(nullable(Plugin.class),
                nullable(JSONObject.class));

        List<Plugin> actualPlugins = pluginManagerSpy.resolveDirectDependencies(plugin);

        assertThat(actualPlugins).isEqualTo(directDependencyExpectedPlugins);
    }

    @Test
    void resolveDirectDependenciesExperimental() {
        PluginManager pluginManagerSpy = spy(pm);
        Plugin plugin = new Plugin("maven-invoker-plugin", "experimental", null, null);

        doReturn(directDependencyExpectedPlugins).when(pluginManagerSpy).resolveDependenciesFromJson(nullable(Plugin.class),
                nullable(JSONObject.class));

        List<Plugin> actualPlugins = pluginManagerSpy.resolveDirectDependencies(plugin);

        assertThat(actualPlugins).isEqualTo(directDependencyExpectedPlugins);
    }

    @Test
    void resolveDependenciesFromJsonTest() {
        JSONObject json = setTestUcJson();

        Plugin mavenInvoker = new Plugin("maven-invoker-plugin", "2.4", null, null);
        List<Plugin> actualPlugins = pm.resolveDependenciesFromJson(mavenInvoker, json);

        assertThat(actualPlugins).isEqualTo(directDependencyExpectedPlugins);
    }

    @Test
    void resolveRecursiveDependenciesTest() {
        PluginManager pluginManagerSpy = spy(pm);
        doReturn(new ArrayList<Plugin>()).when(pluginManagerSpy).resolveDirectDependencies(any(Plugin.class));

        Plugin grandParent = new Plugin("grandparent", "1.0", null, null);

        Plugin parent1 = new Plugin("parent1", "1.0", null, null);
        Plugin parent2 = new Plugin("replaced1", "1.0", null, null);
        Plugin parent3 = new Plugin("parent3", "1.2", null, null);

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
                .containsValues(
                        grandParent, parent1, child4, parent3, child2, child3,
                        child8, child9, child5, child6)
                .hasSize(10);
    }

    @Test
    void resolveRecursiveDependenciesLatestAllPinnedOlderThanRequired() {
        // Arrange
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(new File(folder, "plugins"))
                .withUseLatestAll(true)
                .build();
        PluginManager pluginManager = new PluginManager(config);
        PluginManager pluginManagerSpy = spy(pluginManager);
        doReturn(new ArrayList<Plugin>()).when(pluginManagerSpy).resolveDirectDependencies(any(Plugin.class));

        Plugin grandParent = new Plugin("grandparent", "1.0", null, null);
        Plugin parent = new Plugin("parent", "1.0", null, null);

        Plugin child1 = new Plugin("child1", "1.3", null, null);
        Plugin child1Latest = new Plugin("child1", "1.5", null, null);
        Plugin child1Pinned = new Plugin("child1", "1.2", null, null);

        Map<String, Plugin> topLevelDependencies = new HashMap<>();
        topLevelDependencies.put(child1Pinned.getName(), child1Pinned);

        grandParent.setDependencies(singletonList(parent));
        parent.setDependencies(singletonList(child1));

        doReturn(child1Latest.getVersion())
                .when(pluginManagerSpy).getLatestPluginVersion(parent, child1.getName());
        doReturn(new VersionNumber("1.0"))
                .when(pluginManagerSpy).getLatestPluginVersion(not(eq(parent)), not(eq(child1.getName())));

        String exceptionMessage = String.format("Plugin %s:%s depends on %s:%s, but there is an older version defined on the top level - %s:%s",
                parent.getName(), parent.getVersion(), child1.getName(), child1.getVersion(), child1Pinned.getName(), child1Pinned.getVersion());

        // Act, Assert
        assertThatThrownBy(() -> pluginManagerSpy.resolveRecursiveDependencies(grandParent, topLevelDependencies))
                .isInstanceOf(PluginDependencyException.class)
                .hasMessageContaining(exceptionMessage);
    }

    @Test
    void resolveRecursiveDependenciesLatestAllPinnedVsLatest() {
        // Arrange
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(new File(folder, "plugins"))
                .withUseLatestAll(true)
                .build();
        PluginManager pluginManager = new PluginManager(config);
        PluginManager pluginManagerSpy = spy(pluginManager);
        doReturn(new ArrayList<Plugin>()).when(pluginManagerSpy).resolveDirectDependencies(any(Plugin.class));

        Plugin grandParent = new Plugin("grandparent", "1.0", null, null);
        Plugin parent = new Plugin("parent", "1.0", null, null);

        Plugin child1 = new Plugin("child1", "1.3", null, null);
        Plugin child1Latest = new Plugin("child1", "1.5", null, null);
        Plugin child1Pinned = new Plugin("child1", "1.4", null, null);
        Plugin child2 = new Plugin("child2", "2.3", null, null);
        Plugin child2Latest = new Plugin("child2", "2.5", null, null);

        Map<String, Plugin> topLevelDependencies = new HashMap<>();
        topLevelDependencies.put(child1Pinned.getName(), child1Pinned);

        grandParent.setDependencies(singletonList(parent));
        parent.setDependencies(Arrays.asList(child1, child2));

        doReturn(child1Latest.getVersion())
                .when(pluginManagerSpy).getLatestPluginVersion(parent, child1.getName());
        doReturn(child2Latest.getVersion())
                .when(pluginManagerSpy).getLatestPluginVersion(parent, child2.getName());
        doReturn(parent.getVersion())
                .when(pluginManagerSpy).getLatestPluginVersion(grandParent, parent.getName());
        doReturn(grandParent.getVersion())
                .when(pluginManagerSpy).getLatestPluginVersion(null, grandParent.getName());

        // Act
        Map<String, Plugin> recursiveDependencies = pluginManagerSpy
                                .resolveRecursiveDependencies(grandParent, topLevelDependencies);

        // Assert
        assertThat(recursiveDependencies)
                .containsValues(grandParent, parent, child2Latest)
                .hasSize(3);
    }

    @Test
    void resolveRecursiveDependenciesPinnedPlugin() {
        // Arrange
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(new File(folder, "plugins"))
                .withUseLatestAll(false)
                .build();
        PluginManager pluginManager = new PluginManager(config);
        PluginManager pluginManagerSpy = spy(pluginManager);
        doReturn(new ArrayList<Plugin>()).when(pluginManagerSpy).resolveDirectDependencies(any(Plugin.class));

        Plugin grandParent = new Plugin("grandparent", "1.0", null, null);
        Plugin parent = new Plugin("parent", "1.0", null, null);
        Plugin child1 = new Plugin("child1", "1.3", null, null);
        Plugin child1Latest = new Plugin("child1", "1.5", null, null);
        Plugin child1Pinned = new Plugin("child1", "1.4", null, null);

        Map<String, Plugin> topLevelDependencies = new HashMap<>();
        topLevelDependencies.put(child1Pinned.getName(), child1Pinned);

        grandParent.setDependencies(singletonList(parent));
        parent.setDependencies(singletonList(child1));

        // Act
        Map<String, Plugin> recursiveDependencies = pluginManagerSpy
                                .resolveRecursiveDependencies(grandParent, topLevelDependencies);

        // Assert
        assertThat(recursiveDependencies)
                .containsValues(grandParent, parent)
                .hasSize(2);
    }

    @Test
    void installedPluginsTest() throws Exception {
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
    void getPluginDownloadUrlTest() {
        Plugin plugin = new Plugin("pluginName", "pluginVersion", "pluginURL", null);

        assertThat(pm.getPluginDownloadUrl(plugin)).isEqualTo("pluginURL");

        // Note: As of now (2019/12/18 lmm) there is no 'latest' folder in the cloudbees update center as a sibling of the "download" folder
        //  so this is only applicable on jenkins.io
        Plugin pluginNoUrl = new Plugin("pluginName", "latest", null, null);
        String latestUcUrl = "https://updates.jenkins.io/2.176";
        pm.setJenkinsUCLatest(latestUcUrl + "/update-center.json");
        String latestUrl = latestUcUrl + "/latest/pluginName.hpi";
        setTestUcJson();
        assertEquals(latestUrl, pm.getPluginDownloadUrl(pluginNoUrl));

        Plugin pluginNoVersion = new Plugin("pluginName", null, null, null);
        assertThat(pm.getPluginDownloadUrl(pluginNoVersion)).isEqualTo(latestUrl);

        Plugin pluginExperimentalVersion = new Plugin("pluginName", "experimental", null, null);
        String experimentalUrl = dirName(cfg.getJenkinsUcExperimental()) + "latest/pluginName.hpi";
        assertEquals(experimentalUrl, pm.getPluginDownloadUrl(pluginExperimentalVersion));

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
    void getDownloadPluginUrlTestComplexUpdateCenterUrl() throws IOException {
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(new File(folder, "plugins"))
                .withJenkinsUc(new URL("https://jenkins-updates.cloudbees.com/update-center/envelope-cje/update-center.json?version=2.176.4.3"))
                .build();

        PluginManager pluginManager = new PluginManager(config);

        Plugin plugin = new Plugin("the-plugin", "1.0", null, null);

        String result = pluginManager.getPluginDownloadUrl(plugin);

        assertThat(result).isEqualTo("https://jenkins-updates.cloudbees.com/download/plugins/the-plugin/1.0/the-plugin.hpi");
    }

    /**
     * Test configuring custom update center mirror configuration (i.e. Artifactory).
     */
    @Disabled("setting environment variables is not supported in Java 17")
    @Test
    void getDownloadPluginUrlCustomUcUrls() throws IOException {
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(new File(folder, "plugins"))
                .withJenkinsUc(new URL("https://private-mirror.com/jenkins-updated-center/dynamic-stable-2.319.1/update-center-actual.json?version=2.319.1"))
                .withJenkinsUcExperimental(new URL("https://private-mirror.com/jenkins-updated-center/experimental/update-center.actual.json"))
                .withJenkinsIncrementalsRepoMirror(new URL("https://private-mirror.com/jenkins-updated-center/incrementals"))
                .withJenkinsPluginInfo(new URL("https://private-mirror.com/jenkins-updated-center/current/plugin-versions.json"))
                .build();

        //environmentVariables.set("JENKINS_UC_DOWNLOAD_URL", "https://private-mirror.com/jenkins-updated-center/download/plugins");

        PluginManager pluginManager = new PluginManager(config);

        // set latest and experimental jsons. The plugin url configured here is NOT expected to be used during
        // download url resolution.
        JSONObject ucJson = new JSONObject();
        JSONObject plugins = new JSONObject();
        JSONObject somePlugin = new JSONObject();
        somePlugin.put("version", "1.0.0");
        somePlugin.put("url", "https://updates.jenkins.io/downloads/plugins/pluginName/1.0.0/pluginName.hpi");
        plugins.put("pluginName", somePlugin);
        ucJson.put("plugins", plugins);
        pluginManager.setLatestUcJson(ucJson);
        pluginManager.setExperimentalUcJson(ucJson);

        assertAll(
                () -> {
                    // with non-latest version
                    Plugin plugin = new Plugin("pluginName", "1.0.0", null, null);
                    assertThat(pluginManager.getPluginDownloadUrl(plugin))
                            .isEqualTo("https://private-mirror.com/jenkins-updated-center/download/plugins/pluginName/1.0.0/pluginName.hpi");
                },
                () -> {
                    // lastest version
                    Plugin plugin = new Plugin("pluginName", "latest", null, null);
                    assertThat(pluginManager.getPluginDownloadUrl(plugin))
                            .isEqualTo("https://private-mirror.com/jenkins-updated-center/download/plugins/pluginName/latest/pluginName.hpi");
                },
                () -> {
                    // latest version with resolved version
                    // when `--latest-specified` or `--latest` is enabled the plugin version string will be updated to a specific
                    // version and the latest flag set to true. The resolved download url should resolve to JENKINS_UC_DOWNLOAD_URL
                    Plugin plugin = new Plugin("pluginName", "1.0.0", null, null);
                    plugin.setLatest(true);
                    assertThat(plugin.isLatest()).isTrue();
                    assertThat(pluginManager.getPluginDownloadUrl(plugin))
                            .isEqualTo("https://private-mirror.com/jenkins-updated-center/download/plugins/pluginName/1.0.0/pluginName.hpi");
                },
                () -> {
                    // experimental version
                    Plugin plugin = new Plugin("pluginName", "experimental", null, null);
                    assertThat(pluginManager.getPluginDownloadUrl(plugin))
                            .isEqualTo("https://private-mirror.com/jenkins-updated-center/download/plugins/pluginName/experimental/pluginName.hpi");
                },
                () -> {
                    // experimental version with resolved version
                    // if an experimental plugin is resolved to specific version the "is experimental" property remains "true"
                    // but download url should resolve to configured JENKINS_UC_DOWNLOAD_URL
                    Plugin plugin = new Plugin("pluginName", "experimental", null, null);
                    plugin.setVersion(new VersionNumber("1.0.0"));
                    assertThat(plugin.isExperimental()).isTrue();
                    assertThat(pluginManager.getPluginDownloadUrl(plugin))
                            .isEqualTo("https://private-mirror.com/jenkins-updated-center/download/plugins/pluginName/1.0.0/pluginName.hpi");
                },
                () -> {
                    // incremental
                    Plugin plugin = new Plugin("pluginName", "1.0.0", null, "com.cloudbees.jenkins");
                    assertThat(pluginManager.getPluginDownloadUrl(plugin))
                            .isEqualTo("https://private-mirror.com/jenkins-updated-center/download/plugins/pluginName/1.0.0/pluginName.hpi");
                },
                () -> {
                    // explicit url
                    Plugin plugin = new Plugin("pluginName", "1.0.0", "https://other-mirror.com/plugins/custom-url.hpi", null);
                    assertThat(pluginManager.getPluginDownloadUrl(plugin))
                            .isEqualTo("https://private-mirror.com/jenkins-updated-center/download/plugins/pluginName/1.0.0/pluginName.hpi");
                }
        );
    }

    @Test
    void getAttributeFromManifestExceptionTest() {
        assertThatThrownBy(() -> ManifestTools.getAttributeFromManifest(new File("non-existing-file.txt"), "Plugin-Dependencies"))
                .isInstanceOf(DownloadPluginException.class);
    }

    @Test
    void getJenkinsVersionFromWarTest() {
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
    void getJenkinsVersionFromArg() {
        //the only time the file for a particular war string is created is in the PluginManager constructor
        Config config = Config.builder()
                .withJenkinsVersion(new VersionNumber("2.263.1"))
                .build();
        PluginManager pluginManager = new PluginManager(config);
        assertThat(pluginManager.getJenkinsVersion())
                .isEqualByComparingTo(new VersionNumber("2.263.1"));
    }

    @Test
    void bundledPluginsTest() {
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

    @Test
    void getPluginFromLocalFolderTest() {
        PluginManager pluginManagerSpy = spy(pm);
        Plugin testPlugin = new Plugin("test", "latest", "file:///tmp/test.jar", null);

        pluginManagerSpy.downloadToFile(testPlugin.getUrl(), testPlugin, new File("/tmp/test.jar"));

        verify(pluginManagerSpy, times(1)).copyLocalFile(any(String.class), any(Plugin.class), any(File.class));
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
        structsPlugin.put("optional", true);
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
        pm.setExperimentalUcJson(latestUcJson);
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

    private static File newFolder(File root, String... subDirs) {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        assertTrue(result.mkdirs(), "Couldn't create folders " + result);
        return result;
    }
}
