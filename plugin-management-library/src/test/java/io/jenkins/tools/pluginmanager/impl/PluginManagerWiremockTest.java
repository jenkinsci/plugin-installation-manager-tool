package io.jenkins.tools.pluginmanager.impl;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import hudson.util.VersionNumber;
import io.jenkins.tools.pluginmanager.config.Config;
import io.jenkins.tools.pluginmanager.config.Settings;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static com.github.tomakehurst.wiremock.client.WireMock.proxyAllTo;
import static io.jenkins.tools.pluginmanager.util.PluginManagerUtils.dirName;
import static org.assertj.core.api.Assertions.assertThat;

public class PluginManagerWiremockTest {

    private PluginManager pm;
    private Config cfg;

    private WireMockServer archives;
    private WireMockServer updateCenter;

    private final boolean record = Boolean.parseBoolean(System.getProperty("pluginmanager.record", "false"));

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Before
    public void proxyToWireMock() throws MalformedURLException {
        archives = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        archives.start();
        updateCenter = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        updateCenter.start();

        cfg = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(new File(folder.getRoot(), "plugins"))
                .withJenkinsUc(new URL("http://localhost:" + updateCenter.port() + "/update-center.actual.json"))
                .build();

        pm = new PluginManager(cfg);

        if (record) {
            archives.stubFor(proxyAllTo("http://archives.jenkins-ci.org").atPriority(1));
            updateCenter.stubFor(proxyAllTo("https://updates.jenkins.io").atPriority(1));
        }
    }

    @After
    public void noMoreWireMock() {
        if (record) {
            archives.snapshotRecord();
        }
        archives.stop();
        archives = null;

        if (record) {
            updateCenter.snapshotRecord();
        }
        updateCenter.stop();
        updateCenter = null;
    }

    @Test
    public void downloadToFileTest() {
        Plugin plugin = new Plugin("pluginName", "pluginVersion", "pluginURL", null);
        int wireMockPort = archives.port();
        assertThat(pm.downloadToFile("http://localhost:" + wireMockPort + "/plugins/mailer/1.32/mailer.hpi", plugin, null)).isTrue();
    }

    @Test
    public void checkVersionSpecificUpdateCenterBadRequestTest() {
        pm.setJenkinsVersion(new VersionNumber("2.235"));

        pm.checkAndSetLatestUpdateCenter();
        String expected = "http://localhost:" + updateCenter.port() + "/2.235/update-center.actual.json";
        assertThat(pm.getJenkinsUCLatest()).isEqualTo(expected);
    }

    @Test
    public void checkVersionSpecificUpdateCenterTest() {
        //Test where version specific update center exists
        pm.setJenkinsVersion(new VersionNumber("2.235"));

        pm.checkAndSetLatestUpdateCenter();

        String expected = dirName(cfg.getJenkinsUc()) + pm.getJenkinsVersion() + Settings.DEFAULT_UPDATE_CENTER_FILENAME;
        assertThat(pm.getJenkinsUCLatest()).isEqualTo(expected);
    }
}
