package io.jenkins.tools.pluginmanager.impl;

import io.jenkins.tools.pluginmanager.config.Config;
import io.jenkins.tools.pluginmanager.config.Settings;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
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
@PrepareForTest({HttpClients.class, PluginManager.class})
public class PluginManagerTest {
    PluginManager pm;
    Config cfg;

    @Before
    public void setUpPM() {
        cfg = new Config();
        cfg.setJenkinsWar(Settings.DEFAULT_JENKINS_WAR);
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


}

