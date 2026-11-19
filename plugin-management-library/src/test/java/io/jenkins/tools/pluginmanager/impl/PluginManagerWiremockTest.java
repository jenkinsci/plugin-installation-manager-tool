package io.jenkins.tools.pluginmanager.impl;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.proxyAllTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    void downloadHttpToFileNotFoundNotRetriedTest() {
        Plugin plugin = new Plugin("nonexistent", "1.0", null, null);
        int wireMockPort = archives.port();
        File targetFile = new File(folder, "test.hpi");
        String url = "http://localhost:" + wireMockPort + "/plugins/nonexistent/1.0/nonexistent.hpi";
        assertThat(pm.downloadHttpToFile(url, plugin, targetFile, 3)).isFalse();
        archives.verify(1, getRequestedFor(urlEqualTo("/plugins/nonexistent/1.0/nonexistent.hpi")));
    }

    @Test
    void getJsonWithBasicAuth() throws Exception {
        int wireMockPort = protectedArchives.port();
        assertThat(pm.getJson(new URL("http://localhost:" + wireMockPort + "/update-center.json"), "cache-key")).isNotNull();
    }

    @Test
    void downloadToFileHttpEmptyResponseFaultTest() {
        archives.stubFor(get("/fault/plugin.hpi")
                .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));

        Plugin plugin = new Plugin("faultyPlugin", "1.0", null, null);
        int wireMockPort = archives.port();
        String url = "http://localhost:" + wireMockPort + "/fault/plugin.hpi";

        assertThat(pm.downloadToFile(url, plugin, null)).isFalse();
        assertThat(pm.getFailedPlugins()).contains(plugin);
    }

    @Test
    void downloadPluginsThrowsOnHttpFaultTest() {
        archives.stubFor(get("/fault/plugin.hpi")
                .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));

        Plugin plugin = new Plugin("faultyPlugin", "1.0", "http://localhost:" + archives.port() + "/fault/plugin.hpi", null);
        assertThatThrownBy(() -> pm.downloadPlugins(Collections.singletonList(plugin)))
                .isInstanceOf(DownloadPluginException.class);
    }

    @Test
    void downloadPluginsSkipFailedPluginsOnHttpFaultTest() {
        archives.stubFor(get("/fault/plugin.hpi")
                .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));

        Config skipCfg = Config.builder()
                .withJenkinsWar(Settings.DEFAULT_WAR)
                .withPluginDir(new File(folder, "plugins-skip"))
                .withSkipFailedPlugins(true)
                .withCachePath(newFolder(folder, "junit-skip").toPath())
                .build();
        PluginManager skipPm = new PluginManager(skipCfg);

        Plugin plugin = new Plugin("faultyPlugin", "1.0", "http://localhost:" + archives.port() + "/fault/plugin.hpi", null);
        assertThatCode(() -> skipPm.downloadPlugins(Collections.singletonList(plugin)))
                .doesNotThrowAnyException();
        assertThat(skipPm.getFailedPlugins()).contains(plugin);
    }

    private static File newFolder(File root, String... subDirs) {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        assertTrue(result.mkdirs(), "Couldn't create folders " + result);
        return result;
    }
}
