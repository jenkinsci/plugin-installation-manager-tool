package io.jenkins.tools.pluginmanager.impl;

import io.jenkins.tools.pluginmanager.config.Config;
import io.jenkins.tools.pluginmanager.config.Settings;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PluginManager_appendPathOntoUrlTest {

    @Test
    public void appendPathOntoUrlTest() {
        Config config = prepareMockConfig();
        PluginManager objectUnderTest = new PluginManager(config);

        String url = "http://bob.com:8080";
        String path = "file.json";

        String result = objectUnderTest.appendPathOntoUrl(url, path);

        assertThat(result, is(url + "/" + path));
    }

    @Test
    public void appendPathOntoUrlWithUrlTrailingSlashTest() {
        Config config = prepareMockConfig();
        PluginManager objectUnderTest = new PluginManager(config);

        String url = "http://bob.com:8080";
        String path = "file.json";

        String result = objectUnderTest.appendPathOntoUrl(url + "/", path);

        assertThat(result, is(url + "/" + path));
    }

    @Test
    public void appendPathOntoUrlWithPathLeadingSlashTest() {
        Config config = prepareMockConfig();
        PluginManager objectUnderTest = new PluginManager(config);

        String url = "http://bob.com:8080";
        String path = "file.json";

        String result = objectUnderTest.appendPathOntoUrl(url, "/" + path);

        assertThat(result, is(url + "/" + path));
    }

    @Test
    public void appendPathOntoUrlNoDomainNameTest() {
        Config config = prepareMockConfig();
        PluginManager objectUnderTest = new PluginManager(config);

        String url = "anything://";
        String path = "bob.com/file.json";

        String result = objectUnderTest.appendPathOntoUrl(url, path);

        assertThat(result, is(url + path));
    }

    private Config prepareMockConfig() {
        Config config = mock(Config.class);
        when(config.getJenkinsWar()).thenReturn(Settings.DEFAULT_WAR);
        when(config.getJenkinsUc()).thenReturn(Settings.DEFAULT_UPDATE_CENTER);
        return config;
    }

}