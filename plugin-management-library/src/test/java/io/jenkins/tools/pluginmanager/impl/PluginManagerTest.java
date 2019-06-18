package io.jenkins.tools.pluginmanager.impl;

import io.jenkins.tools.pluginmanager.config.Config;
import io.jenkins.tools.pluginmanager.config.Settings;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;


@RunWith(PowerMockRunner.class)
@PrepareForTest({HttpClients.class, PluginManager.class, HttpClientContext.class, URIUtils.class, HttpHost.class,
        URI.class, FileUtils.class, URL.class})
public class PluginManagerTest {
    PluginManager pm;
    Config cfg;

    @Before
    public void setUpPM() throws IOException {
        cfg = new Config();
        cfg.setJenkinsWar(Settings.DEFAULT_JENKINS_WAR);
        cfg.setPluginDir(Files.createTempDirectory("plugins").toFile());
        pm = new PluginManager(cfg);
    }


    @Test
    public void checkVersionSpecificUpdateCenterTest() throws Exception {
        //Test where version specific update center exists
        pm.setJenkinsVersion("2.176");

        PowerMockito.mockStatic(HttpClients.class);
        CloseableHttpClient httpclient = Mockito.mock(CloseableHttpClient.class);

        Mockito.when(HttpClients.createDefault()).thenReturn(httpclient);
        HttpGet httpget = Mockito.mock(HttpGet.class);

        PowerMockito.whenNew(HttpGet.class).withAnyArguments().thenReturn(httpget);
        CloseableHttpResponse response = Mockito.mock(CloseableHttpResponse.class);
        Mockito.when(httpclient.execute(httpget)).thenReturn(response);

        StatusLine statusLine = Mockito.mock(StatusLine.class);
        PowerMockito.when(response.getStatusLine()).thenReturn(statusLine);

        int statusCode = HttpStatus.SC_OK;
        Mockito.when(statusLine.getStatusCode()).thenReturn(statusCode);

        pm.checkVersionSpecificUpdateCenter();

        String expected = new StringBuilder(PluginManager.JENKINS_UC).append(pm.getJenkinsVersion()).toString();
        Assert.assertEquals(expected, pm.getJenkinsUCLatest());

        //Test where version specific update center doesn't exist
        statusCode = HttpStatus.SC_BAD_REQUEST;
        Mockito.when(statusLine.getStatusCode()).thenReturn(statusCode);

        pm.checkVersionSpecificUpdateCenter();

        expected = "";
        Assert.assertEquals(expected, pm.getJenkinsUCLatest());
    }


    @Test
    public void comparePluginVersionsTest() {
        int actual = pm.comparePluginVersions("1.1", "2.0.2");
        int expected = -1;
        Assert.assertEquals(expected, actual);

        actual = pm.comparePluginVersions("4.1.4", "4.11.4");
        expected = -1;
        Assert.assertEquals(expected, actual);

        actual = pm.comparePluginVersions("2.0.2", "1.1");
        expected = 1;
        Assert.assertEquals(expected, actual);

        actual = pm.comparePluginVersions("3.2.1.1", "3.2.1.1");
        expected = 0;
        Assert.assertEquals(expected, actual);
    }


    @Test
    public void getPluginVersionTest() {

        URL jpiURL = this.getClass().getResource("/delivery-pipeline-plugin.jpi");
        File testJpi = new File(jpiURL.getFile());

        Assert.assertEquals("1.3.2", pm.getPluginVersion(testJpi));

        URL hpiURL = this.getClass().getResource("/ssh-credentials.hpi");
        File testHpi = new File(hpiURL.getFile());

        Assert.assertEquals("1.10", pm.getPluginVersion(testHpi));
    }


    @Test
    public void installedPluginsTest() throws IOException {
        File pluginDir = cfg.getPluginDir();

        List<String> expectedPlugins = new ArrayList<>();

        File tmp1 = File.createTempFile("test", ".jpi", pluginDir);
        File tmp2 = File.createTempFile("test2", ".jpi", pluginDir);

        URL deliveryPipelineJpi = this.getClass().getResource("/delivery-pipeline-plugin.jpi");
        File deliveryPipelineFile = new File(deliveryPipelineJpi.getFile());


        URL githubJpi = this.getClass().getResource("/github-branch-source.jpi");
        File githubFile = new File(githubJpi.getFile());

        FileUtils.copyFile(deliveryPipelineFile, tmp1);
        FileUtils.copyFile(githubFile, tmp2);


        expectedPlugins.add(FilenameUtils.getBaseName(tmp1.getName()));
        expectedPlugins.add(FilenameUtils.getBaseName(tmp2.getName()));

        List<String> actualPlugins = pm.installedPlugins();

        Collections.sort(expectedPlugins);
        Collections.sort(actualPlugins);

        Assert.assertEquals(expectedPlugins, actualPlugins);
    }


    @Test
    public void downloadToFileTest() throws Exception {
        PowerMockito.mockStatic(HttpClients.class);
        CloseableHttpClient httpclient = Mockito.mock(CloseableHttpClient.class);

        Mockito.when(HttpClients.createDefault()).thenReturn(httpclient);
        HttpGet httpget = Mockito.mock(HttpGet.class);

        PowerMockito.mockStatic(HttpClientContext.class);

        HttpClientContext context = Mockito.mock(HttpClientContext.class);
        Mockito.when(HttpClientContext.create()).thenReturn(context);


        PowerMockito.whenNew(HttpGet.class).withAnyArguments().thenReturn(httpget);
        CloseableHttpResponse response = Mockito.mock(CloseableHttpResponse.class);
        Mockito.when(httpclient.execute(httpget, context)).thenReturn(response);

        HttpHost target = PowerMockito.mock(HttpHost.class);
        Mockito.when(context.getTargetHost()).thenReturn(target);

        List<URI> redirectLocations = new ArrayList<>(); //Mockito.mock()
        redirectLocations.add(new URI("downloadURI"));

        Mockito.when(context.getRedirectLocations()).thenReturn(redirectLocations);

        PowerMockito.mockStatic(URIUtils.class);

        URI downloadLocation = PowerMockito.mock(URI.class);

        URI requestedLocation = PowerMockito.mock(URI.class);
        Mockito.when(httpget.getURI()).thenReturn(requestedLocation);

        Mockito.when(URIUtils.resolve(requestedLocation, target, redirectLocations)).thenReturn(downloadLocation);

        PowerMockito.mockStatic(FileUtils.class);
        URL url = PowerMockito.mock(URL.class);

        //File pluginDir = cfg.getPluginDir();
        //File tmp3 = File.createTempFile("test", ".jpi", pluginDir);

        Plugin plugin = new Plugin("pluginName", "pluginVersion", "pluginURL");

        JarFile pluginJpi = Mockito.mock(JarFile.class);

        PowerMockito.whenNew(JarFile.class).withAnyArguments().thenReturn(pluginJpi);
        Assert.assertTrue(pm.downloadToFile("downloadURL", plugin));
    }

    @Test
    public void getPluginDownloadUrlTest() {
        Plugin plugin = new Plugin("pluginName", "pluginVersion", "pluginURL");

        Assert.assertEquals( "pluginURL", pm.getPluginDownloadUrl(plugin));

        plugin.setUrl("");
        pm.setJenkinsUCLatest("https://updates.jenkins.io/2.176");

        plugin.setVersion("latest");

        String latestUrl = pm.getJenkinsUCLatest() + "/latest/pluginName.hpi";

        Assert.assertEquals(latestUrl, pm.getPluginDownloadUrl(plugin));

        plugin.setVersion("");
        Assert.assertEquals(latestUrl, pm.getPluginDownloadUrl(plugin));

        plugin.setVersion("experimental");

        String experimentalUrl = PluginManager.JENKINS_UC_EXPERIMENTAL + "/latest/pluginName.hpi";
        Assert.assertEquals(experimentalUrl, pm.getPluginDownloadUrl(plugin));

        plugin.setVersion("incrementals;org.jenkins-ci.plugins.workflow;2.19-rc289.d09828a05a74");

        String incrementalUrl = PluginManager.JENKINS_INCREMENTALS_REPO_MIRROR +
                "/org/jenkins-ci/plugins/workflow/2.19-rc289.d09828a05a74/pluginName-2.19-rc289.d09828a05a74.hpi";

        Assert.assertEquals(incrementalUrl, pm.getPluginDownloadUrl(plugin));

        plugin.setVersion("otherversion");

        String otherURL = PluginManager.JENKINS_UC_DOWNLOAD + "/plugins/pluginName/otherversion/pluginName.hpi";

        Assert.assertEquals(otherURL, pm.getPluginDownloadUrl(plugin));
    }


    @Test
    public void getJenkinsVersionFromWarTest() throws Exception {
        URL warURL = this.getClass().getResource("/plugin-manager-test-war.war");
        File testWar = new File(warURL.getFile());

        //the only time the file for a particular war string is created is in the PluginManager constructor
        Config config = new Config();
        config.setJenkinsWar(testWar.toString());
        PluginManager pluginManager = new PluginManager(config);
        Assert.assertEquals("2.164.1", pluginManager.getJenkinsVersionFromWar());
    }

    /*
    @Test
    public void bundledPluginsTest() {
        URL warURL = this.getClass().getResource("/plugin-manager-test-war.war");
        File testWar = new File(warURL.getFile());

        Config config = new Config();
        config.setJenkinsWar(testWar.toString());
        PluginManager pluginManager = new PluginManager(config);

        List<String> expectedPlugins = new ArrayList<>();
        expectedPlugins.add("credentials.jpi");
        expectedPlugins.add("display-url-api.jpi");
        expectedPlugins.add("github-branch-source.hpi");

        List<String> actualPlugins = pluginManager.bundledPlugins();

        Collections.sort(expectedPlugins);
        Collections.sort(actualPlugins);

        Assert.assertEquals(expectedPlugins, actualPlugins);
    }
    */

}

