package io.jenkins.tools.pluginmanager.impl;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.jenkins.tools.pluginmanager.config.Config;
import io.jenkins.tools.pluginmanager.config.Settings;
import java.io.File;
import java.net.MalformedURLException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static com.github.tomakehurst.wiremock.client.WireMock.proxyAllTo;
import static org.assertj.core.api.Assertions.assertThat;

public class PluginManagerWiremockTest {

    private PluginManager pm;
    private Config cfg;

    private WireMockServer archives;

    private final boolean record = Boolean.parseBoolean(System.getProperty("pluginmanager.record", "false"));

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Before
    public void proxyToWireMock() throws MalformedURLException {
        archives = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        archives.start();

        cfg = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(new File(folder.getRoot(), "plugins"))
                .build();

        pm = new PluginManager(cfg);

        if (record) {
            archives.stubFor(proxyAllTo("http://archives.jenkins-ci.org").atPriority(1));
        }
    }

    @After
    public void noMoreWireMock() {
        if (record) {
            archives.snapshotRecord();
        }
        archives.stop();
        archives = null;
    }

    @Test
    public void downloadToFileTest() {
        Plugin plugin = new Plugin("pluginName", "pluginVersion", "pluginURL", null);
        int wireMockPort = archives.port();
        assertThat(pm.downloadToFile("http://localhost:" + wireMockPort + "/plugins/mailer/1.32/mailer.hpi", plugin, null)).isTrue();
    }
}
