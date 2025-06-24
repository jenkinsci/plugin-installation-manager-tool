package io.jenkins.tools.pluginmanager.impl;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.jenkins.tools.pluginmanager.config.Config;
import io.jenkins.tools.pluginmanager.config.Credentials;
import io.jenkins.tools.pluginmanager.config.Settings;
import java.io.File;
import java.net.URL;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.github.tomakehurst.wiremock.client.WireMock.proxyAllTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginManagerWiremockTest {

    private PluginManager pm;
    private Config cfg;

    private WireMockServer archives;
    private WireMockServer protectedArchives;

    private final boolean record = Boolean.parseBoolean(System.getProperty("pluginmanager.record", "false"));

    @TempDir
    private File folder;

    @BeforeEach
    void proxyToWireMock() {
        archives = new WireMockServer(WireMockConfiguration.options().dynamicPort().notifier(new ConsoleNotifier(true)));
        archives.start();
        protectedArchives = new WireMockServer(WireMockConfiguration.options().dynamicPort().notifier(new ConsoleNotifier(true)));
        protectedArchives.start();
        cfg = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(new File(folder, "plugins"))
                .withCredentials(Collections.singletonList(new Credentials("myuser", "mypassword", "localhost", protectedArchives.port())))
                .withCachePath(newFolder(folder, "junit").toPath())
                .build();
        pm = new PluginManager(cfg);

        if (record) {
            archives.stubFor(proxyAllTo("http://archives.jenkins-ci.org").atPriority(1));
        }
        // the mappings from src/test/resources/mappings are used otherwise
    }

    @AfterEach
    void noMoreWireMock() {
        if (record) {
            archives.snapshotRecord();
        }
        archives.stop();
        archives = null;
    }

    @Test
    void downloadToFileTest() {
        Plugin plugin = new Plugin("pluginName", "pluginVersion", "pluginURL", null);
        int wireMockPort = archives.port();
        assertThat(pm.downloadToFile("http://localhost:" + wireMockPort + "/plugins/mailer/1.32/mailer.hpi", plugin, null)).isTrue();
    }

    @Test
    void downloadToFileWithBasicAuthTest() {
        Plugin plugin = new Plugin("pluginName", "pluginVersion", "pluginURL", null);
        int wireMockPort = protectedArchives.port();
        assertThat(pm.downloadToFile("http://localhost:" + wireMockPort + "/protectedplugins/mailer/1.32/mailer.hpi", plugin, null)).isTrue();
    }

    @Test
    void downloadToFileWithBasicAuthAndIncorrectCredentialsTest() {
        Plugin plugin = new Plugin("pluginName", "pluginVersion", "pluginURL", null);
        int wireMockPort = archives.port(); // no credentials configured for this port
        assertThat(pm.downloadToFile("http://localhost:" + wireMockPort + "/protectedplugins/mailer/1.32/mailer.hpi", plugin, null)).isFalse();
    }

    @Test
    void getJsonWithBasicAuth() throws Exception {
        int wireMockPort = protectedArchives.port();
        assertThat(pm.getJson(new URL("http://localhost:" + wireMockPort + "/update-center.json"), "cache-key")).isNotNull();
    }

    private static File newFolder(File root, String... subDirs) {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        assertTrue(result.mkdirs(), "Couldn't create folders " + result);
        return result;
    }
}
