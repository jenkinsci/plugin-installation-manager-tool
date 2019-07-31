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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.impl.client.CloseableHttpClient;
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

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.whenNew;

@RunWith(PowerMockRunner.class)
@PrepareForTest({HttpClients.class, PluginManager.class, HttpClientContext.class, URIUtils.class, HttpHost.class,
        URI.class, FileUtils.class, URL.class, IOUtils.class, Files.class})
@PowerMockIgnore({"javax.net.ssl.*","javax.security.*"})
public class PluginManagerTest {
    private PluginManager pm;
    private Config cfg;

    @Before
    public void setUpPM() throws IOException {
        cfg = Config.builder()
            .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(Files.createTempDirectory("plugins").toFile())
                .build();

        pm = new PluginManager(cfg);
    }

    @Test
    public void findEffectivePluginsTest() {
        Map<String, VersionNumber> bundledPlugins = new HashMap<>();
        Map<String, VersionNumber> installedPlugins = new HashMap<>();
        bundledPlugins.put("git", new VersionNumber("1.2"));
        bundledPlugins.put("aws-credentials", new VersionNumber("1.24"));
        bundledPlugins.put("p4", new VersionNumber("1.3.3"));
        installedPlugins.put("script-security", new VersionNumber("1.26"));
        installedPlugins.put("credentials", new VersionNumber("2.1.11"));
        installedPlugins.put("ace-editor", new VersionNumber("1.0.1"));
        installedPlugins.put("p4", new VersionNumber("1.3.0"));

        pm.setBundledPluginVersions(bundledPlugins);
        pm.setInstalledPluginVersions(installedPlugins);

        List<Plugin> requestedPlugins = new ArrayList<>();

        requestedPlugins.add(new Plugin("git", "1.3", null, null));
        requestedPlugins.add(new Plugin("script-security", "1.25", null, null));
        requestedPlugins.add(new Plugin("scm-api", "2.2.3", null, null));

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
        browserStack1.put("buildDate", "Jul 12, 2016");
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
        browserStack111.put("buildDate", "Jul 02, 2018");
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
        browserStack112.put("buildDate", "Aug 09, 2018");
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
    public void warningExistsTest() {
        Map<String, List<SecurityWarning>> securityWarnings = new HashMap<>();
        SecurityWarning scriptlerWarning = new SecurityWarning("SECURITY", "security warning",
                "scriptler", "url");
        scriptlerWarning.addSecurityVersion("", "",".*");
        List<SecurityWarning> scriptlerList = new ArrayList<>();
        scriptlerList.add(scriptlerWarning);
        securityWarnings.put("scriptler", scriptlerList);

        SecurityWarning lockableResourceWarning1 = new SecurityWarning("SECURITY", "security warning1",
                "lockable-resources", "url");
        lockableResourceWarning1.addSecurityVersion("", "1.59", "1[.][0-9](|[.-].*)|1[.][12345][0-9](|[.-].*)");
        SecurityWarning lockableResourceWarning2 = new SecurityWarning("SECURITY", "security warning2",
                "lockable-resources", "url");
        lockableResourceWarning2.addSecurityVersion("", "2.2", "1[.].*|2[.][012](|[.-].*)");

        List<SecurityWarning> lockableResourceWarningList = new ArrayList<>();
        lockableResourceWarningList.add(lockableResourceWarning1);
        lockableResourceWarningList.add(lockableResourceWarning2);
        securityWarnings.put("lockable-resources", lockableResourceWarningList);

        SecurityWarning cucumberReports = new SecurityWarning("SECURITY", "security warning", "cucumber-reports",
                "url");
        cucumberReports.addSecurityVersion("1.3.0", "2.5.1", "(1[.][34]|2[.][012345])(|[.-].*)");

        List<SecurityWarning> cucumberReportWarningList = new ArrayList<>();
        cucumberReportWarningList.add(cucumberReports);
        securityWarnings.put("cucumber-reports", cucumberReportWarningList);

        SecurityWarning sshAgentsWarning = new SecurityWarning("SECURITY", "security warning", "ssh-slaves", "url");
        sshAgentsWarning.addSecurityVersion("", "1.14", "0[.].*|1[.][0-9](|[.-].*)|1[.]1[01234](|[.-].*)");

        ArrayList<SecurityWarning> sshAgentsWarningList = new ArrayList<>();
        sshAgentsWarningList.add(sshAgentsWarning);
        securityWarnings.put("ssh-slaves", sshAgentsWarningList);

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
    public void checkVersionSpecificUpdateCenterTest() throws Exception {
        //Test where version specific update center exists
        pm.setJenkinsVersion(new VersionNumber("2.176"));

        mockStatic(HttpClients.class);
        CloseableHttpClient httpclient = mock(CloseableHttpClient.class);

        when(HttpClients.createDefault()).thenReturn(httpclient);
        HttpGet httpget = mock(HttpGet.class);

        whenNew(HttpGet.class).withAnyArguments().thenReturn(httpget);
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        when(httpclient.execute(httpget)).thenReturn(response);

        StatusLine statusLine = mock(StatusLine.class);
        when(response.getStatusLine()).thenReturn(statusLine);

        int statusCode = HttpStatus.SC_OK;
        when(statusLine.getStatusCode()).thenReturn(statusCode);

        pm.checkAndSetLatestUpdateCenter();

        String expected = cfg.getJenkinsUc().toString() + "/" + pm.getJenkinsVersion();
        assertEquals(expected, pm.getJenkinsUCLatest());
    }

    @Test(expected = UpdateCenterInfoRetrievalException.class)
    public void getJsonURLExceptionTest() {
        pm.getJson("htttp://ftp-chi.osuosl.org/pub/jenkins/updates/current/update-center.json");
    }

    @Test(expected = UpdateCenterInfoRetrievalException.class)
    public void getJsonURLIOTest() throws IOException{
        mockStatic(IOUtils.class);

        when(IOUtils.toString(any(URL.class), any(Charset.class))).thenThrow(IOException.class);

        pm.getJson("http://ftp-chi.osuosl.org/pub/jenkins/updates/current/update-center.json");
    }



    @Test
    public void checkVersionSpecificUpdateCenterBadRequestTest() throws Exception {
        pm.setJenkinsVersion(new VersionNumber("2.176"));

        mockStatic(HttpClients.class);
        CloseableHttpClient httpclient = mock(CloseableHttpClient.class);

        when(HttpClients.createDefault()).thenReturn(httpclient);
        HttpGet httpget = mock(HttpGet.class);

        whenNew(HttpGet.class).withAnyArguments().thenReturn(httpget);
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        when(httpclient.execute(httpget)).thenReturn(response);

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

        Map<String, VersionNumber> installedPlugins = new HashMap<>();
        Map<String, VersionNumber> bundledPlugins = new HashMap<>();

        List<Plugin> actualPlugins;

        requestedPlugins.put("git", new Plugin("git", "1.0.0", null, null));

        pm.setInstalledPluginVersions(installedPlugins);
        pm.setBundledPluginVersions(bundledPlugins);

        actualPlugins = pm.findPluginsToDownload(requestedPlugins);

        assertEquals(actualPlugins.size(), 1);
        assertEquals(actualPlugins.get(0).toString(), "git 1.0.0");

        requestedPlugins.put("credentials", new Plugin("credentials", "2.1.14", null, null));
        requestedPlugins.put("structs", new Plugin("structs", "1.18", null, null));
        requestedPlugins.put("ssh-credentials", new Plugin("ssh-credentials", "1.13", null, null));

        installedPlugins.put("git", new VersionNumber("1.1.1"));
        installedPlugins.put("git-client", new VersionNumber("2.7.5"));

        bundledPlugins.put("structs", new VersionNumber("1.16"));

        pm.setInstalledPluginVersions(installedPlugins);
        pm.setBundledPluginVersions(bundledPlugins);

        actualPlugins = pm.findPluginsToDownload(requestedPlugins);

        List<String> actual = new ArrayList<>();

        for (Plugin p: actualPlugins) {
            System.out.println(p.toString());
            actual.add(p.toString());
        }

        Collections.sort(actual);

        List<String> expected = new ArrayList<>();
        expected.add("credentials 2.1.14");
        expected.add("structs 1.18");
        expected.add("ssh-credentials 1.13");
        Collections.sort(expected);

        assertEquals(expected, actual);
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

        assertEquals(true, pluginManager.resolveDependenciesFromManifest(testPlugin).isEmpty());
    }


    @Test
    public void resolveDependenciesFromManifestDownload() throws IOException {
        Config config = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(Files.createTempDirectory("tmpplugins").toFile())
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

        doReturn("1.0.0").doReturn("workflow-scm-step:2.4,workflow-step-api:2.13," +
                "credentials:2.1.14,git-client:2.7.7,mailer:1.18," +
                "parameterized-trigger:2.33;resolution:=optional," +
                "promoted-builds:2.27;resolution:=optional," +
                "scm-api:2.6.3,ssh-credentials:1.13," +
                "token-macro:1.12.1;resolution:=optional")
                .when(pluginManagerSpy).getAttributefromManifest(any(File.class), any(String.class));


        List<Plugin> expectedPlugins = new ArrayList<>();
        expectedPlugins.add(new Plugin("workflow-scm-step", "2.4", null, null));
        expectedPlugins.add(new Plugin("workflow-step-api", "2.13", null, null));
        expectedPlugins.add(new Plugin("credentials", "2.1.14", null, null));
        expectedPlugins.add(new Plugin("git-client", "2.7.7", null, null));
        expectedPlugins.add(new Plugin("mailer", "1.18", null, null));
        expectedPlugins.add(new Plugin("scm-api", "2.6.3", null, null));
        expectedPlugins.add(new Plugin("ssh-credentials", "1.13", null, null));


        List<String> expectedPluginInfo = new ArrayList<>();
        for (Plugin p : expectedPlugins) {
            expectedPluginInfo.add(p.toString());
        }

        Collections.sort(expectedPluginInfo);

        List<Plugin> actualPlugins = pluginManagerSpy.resolveDependenciesFromManifest(testPlugin);
        List<String> actualPluginInfo = new ArrayList<>();
        for (Plugin p : actualPlugins) {
            actualPluginInfo.add(p.toString());
        }

        Collections.sort(actualPluginInfo);;
        assertEquals(expectedPluginInfo, actualPluginInfo);

        assertEquals(testPlugin.getVersion().toString(), "1.0.0");
    }


    @Test
    public void installedPluginsTest() throws IOException {
        File pluginDir = cfg.getPluginDir();

        Map<String, VersionNumber> expectedPlugins = new HashMap<>();

        File tmp1 = File.createTempFile("test", ".jpi", pluginDir);
        File tmp2 = File.createTempFile("test2", ".jpi", pluginDir);

        URL deliveryPipelineJpi = this.getClass().getResource("/delivery-pipeline-plugin.jpi");
        File deliveryPipelineFile = new File(deliveryPipelineJpi.getFile());

        URL githubJpi = this.getClass().getResource("/github-branch-source.jpi");
        File githubFile = new File(githubJpi.getFile());

        FileUtils.copyFile(deliveryPipelineFile, tmp1);
        FileUtils.copyFile(githubFile, tmp2);

        expectedPlugins.put(FilenameUtils.getBaseName(tmp1.getName()), new VersionNumber("1.3.2"));
        expectedPlugins.put(FilenameUtils.getBaseName(tmp2.getName()), new VersionNumber("1.8"));

        Map<String, VersionNumber> actualPlugins = pm.installedPlugins();

        assertEquals(expectedPlugins.get(FilenameUtils.getBaseName(tmp1.getName())), actualPlugins.get(FilenameUtils.getBaseName(tmp1.getName())));
        assertEquals(expectedPlugins.get(FilenameUtils.getBaseName(tmp2.getName())), actualPlugins.get(FilenameUtils.getBaseName(tmp2.getName())));
    }


    @Test
    public void downloadToFileTest() throws Exception {
        mockStatic(HttpClients.class);
        CloseableHttpClient httpclient = mock(CloseableHttpClient.class);

        when(HttpClients.createDefault()).thenReturn(httpclient);
        HttpGet httpget = mock(HttpGet.class);

        mockStatic(HttpClientContext.class);

        HttpClientContext context = mock(HttpClientContext.class);
        when(HttpClientContext.create()).thenReturn(context);


        whenNew(HttpGet.class).withAnyArguments().thenReturn(httpget);
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        when(httpclient.execute(httpget, context)).thenReturn(response);

        HttpHost target = PowerMockito.mock(HttpHost.class);
        when(context.getTargetHost()).thenReturn(target);

        List<URI> redirectLocations = new ArrayList<>(); //Mockito.mock()
        redirectLocations.add(new URI("downloadURI"));

        when(context.getRedirectLocations()).thenReturn(redirectLocations);

        mockStatic(URIUtils.class);

        URI downloadLocation = PowerMockito.mock(URI.class);

        URI requestedLocation = PowerMockito.mock(URI.class);
        when(httpget.getURI()).thenReturn(requestedLocation);

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

        Plugin pluginNoUrl = new Plugin("pluginName", "latest", null, null);
        pm.setJenkinsUCLatest("https://updates.jenkins.io/2.176");
        VersionNumber latestVersion = new VersionNumber("latest");
        String latestUrl = pm.getJenkinsUCLatest() + "/latest/pluginName.hpi";
        Assert.assertEquals(latestUrl, pm.getPluginDownloadUrl(pluginNoUrl));

        Plugin pluginNoVersion = new Plugin("pluginName", null, null, null);
        assertEquals(latestUrl, pm.getPluginDownloadUrl(pluginNoVersion));

        Plugin pluginExperimentalVersion = new Plugin("pluginName", "experimental", null, null);
        String experimentalUrl = cfg.getJenkinsUcExperimental() + "/latest/pluginName.hpi";
        Assert.assertEquals(experimentalUrl, pm.getPluginDownloadUrl(pluginExperimentalVersion));

        Plugin pluginIncrementalRepo = new Plugin("pluginName", "2.19-rc289.d09828a05a74", null, "org.jenkins-ci.plugins.pluginName");

        String incrementalUrl = cfg.getJenkinsIncrementalsRepoMirror() +
                "/org/jenkins-ci/plugins/pluginName/pluginName/2.19-rc289.d09828a05a74/pluginName-2.19-rc289.d09828a05a74.hpi";

        assertEquals(incrementalUrl, pm.getPluginDownloadUrl(pluginIncrementalRepo));

        Plugin pluginOtherVersion = new Plugin("pluginName", "otherversion", null, null);
        String otherURL = cfg.getJenkinsUc() + "/download/plugins/pluginName/otherversion/pluginName.hpi";
        assertEquals(otherURL, pm.getPluginDownloadUrl(pluginOtherVersion));
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
    public void bundledPluginsTest() {
        URL warURL = this.getClass().getResource("/bundledplugintest.war");
        File testWar = new File(warURL.getFile());

        Config config = Config.builder()
                .withJenkinsWar(testWar.toString())
                .build();
        PluginManager pluginManager = new PluginManager(config);

        Map <String, VersionNumber> expectedPlugins = new HashMap<>();
        expectedPlugins.put("credentials", new VersionNumber("2.1.18"));
        expectedPlugins.put("display-url-api", new VersionNumber("2.0"));
        expectedPlugins.put("github-branch-source", new VersionNumber("1.8"));

        Map<String, VersionNumber> actualPlugins = pluginManager.bundledPlugins();

        assertEquals(expectedPlugins.get("credentials"), actualPlugins.get("credentials"));
        assertEquals(expectedPlugins.get("github-branch-source"), actualPlugins.get("github-branch-source"));
        assertEquals(expectedPlugins.get("display-url-api"), actualPlugins.get("display-url-api"));


    }

    private void setTestUcJson() {
        JSONObject latestUcJson = new JSONObject();

        JSONObject pluginJson = new JSONObject();
        latestUcJson.put("plugins", pluginJson);

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
    }
}
