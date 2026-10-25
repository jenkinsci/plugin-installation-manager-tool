package io.jenkins.tools.pluginmanager.impl;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import hudson.util.VersionNumber;
import io.jenkins.tools.pluginmanager.config.Config;
import java.io.File;
import java.net.URL;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PluginManagerMetadataTest {

    @TempDir
    private File folder;

    private WireMockServer server;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        server.stubFor(get(urlPathEqualTo("/update-center.json")).willReturn(okJson("""
                {"plugins":{"example":{"version":"2.0","requiredCore":"2.400",
                  "sha256":"latest-checksum","dependencies":[]}},"warnings":[]}
                """)));
        server.stubFor(get(urlPathEqualTo("/plugin-versions.json")).willReturn(okJson("{\"plugins\":{}}")));
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    private Config config(Plugin... plugins) throws Exception {
        return Config.builder()
                .withJenkinsVersion(new VersionNumber("2.500"))
                .withJenkinsUc(new URL(server.baseUrl() + "/update-center.json"))
                .withJenkinsPluginInfo(new URL(server.baseUrl() + "/plugin-versions.json"))
                .withJenkinsUcExperimental(new URL(server.baseUrl() + "/experimental.json"))
                .withPluginDir(new File(folder, "plugins"))
                .withCachePath(new File(folder, "cache").toPath())
                .withPlugins(List.of(plugins))
                .withDoDownload(false)
                .build();
    }

    @Test
    void latestPluginDoesNotFetchVersionHistory() throws Exception {
        Plugin plugin = new Plugin("example", "latest", null, null);
        new PluginManager(config(plugin)).start();

        assertThat(plugin.getVersion()).isEqualTo(new VersionNumber("2.0"));
        assertThat(plugin.getChecksum()).isEqualTo("latest-checksum");
        server.verify(1, getRequestedFor(urlPathEqualTo("/update-center.json")));
        server.verify(0, getRequestedFor(urlPathEqualTo("/plugin-versions.json")));
    }

    @Test
    void versionHistoryIsLoadedOnceAndCached() throws Exception {
        server.stubFor(get(urlPathEqualTo("/plugin-versions.json")).willReturn(okJson("""
                {"plugins":{
                  "example":{"1.0":{"requiredCore":"2.300","sha256":"old-checksum",
                    "dependencies":[{"name":"helper","version":"1.0","optional":false}]}},
                  "helper":{"1.0":{"requiredCore":"2.300","sha256":"helper-checksum","dependencies":[]}}
                }}
                """)));
        Plugin plugin = new Plugin("example", "1.0", null, null);
        PluginManager manager = new PluginManager(config(plugin));
        manager.getUCJson(new VersionNumber("2.500"));
        server.verify(0, getRequestedFor(urlPathEqualTo("/plugin-versions.json")));

        manager.start();

        assertThat(plugin.getVersion()).isEqualTo(new VersionNumber("1.0"));
        assertThat(plugin.getChecksum()).isEqualTo("old-checksum");
        assertThat(plugin.getJenkinsVersion()).isEqualTo(new VersionNumber("2.300"));
        assertThat(plugin.getDependencies()).singleElement().satisfies(dependency -> {
            assertThat(dependency.getName()).isEqualTo("helper");
            assertThat(dependency.getVersion()).isEqualTo(new VersionNumber("1.0"));
            assertThat(dependency.getChecksum()).isEqualTo("helper-checksum");
        });
        server.verify(1, getRequestedFor(urlPathEqualTo("/plugin-versions.json")));

        server.stubFor(get(urlPathEqualTo("/plugin-versions.json")).willReturn(aResponse().withStatus(503)));
        Plugin cachedPlugin = new Plugin("example", "1.0", null, null);
        new PluginManager(config(cachedPlugin)).start();

        assertThat(cachedPlugin.getChecksum()).isEqualTo("old-checksum");
        server.verify(1, getRequestedFor(urlPathEqualTo("/plugin-versions.json")));
    }

    @Test
    void experimentalPluginDoesNotFetchVersionHistory() throws Exception {
        server.stubFor(get(urlPathEqualTo("/experimental.json")).willReturn(okJson("""
                {"plugins":{"example":{"version":"3.0-beta","requiredCore":"2.400","dependencies":[]}}}
                """)));
        Plugin plugin = new Plugin("example", "experimental", null, null);
        new PluginManager(config(plugin)).start();

        assertThat(plugin.getVersion()).isEqualTo(new VersionNumber("3.0-beta"));
        server.verify(1, getRequestedFor(urlPathEqualTo("/experimental.json")));
        server.verify(0, getRequestedFor(urlPathEqualTo("/plugin-versions.json")));
    }

    @Test
    void unavailableVersionHistoryOnlyFailsWhenNeeded() throws Exception {
        server.stubFor(get(urlPathEqualTo("/plugin-versions.json")).willReturn(aResponse().withStatus(503)));
        new PluginManager(config(new Plugin("example", "latest", null, null))).start();
        server.verify(0, getRequestedFor(urlPathEqualTo("/plugin-versions.json")));

        PluginManager manager = new PluginManager(config(
                new Plugin("example", "1.0", null, null),
                new Plugin("helper", "1.0", null, null)));
        assertThatThrownBy(manager::start)
                .isInstanceOf(AggregatePluginPrerequisitesNotMetException.class)
                .hasStackTraceContaining("Error getting update center json");
        server.verify(3, getRequestedFor(urlPathEqualTo("/plugin-versions.json")));

        server.stubFor(get(urlPathEqualTo("/plugin-versions.json")).willReturn(okJson("""
                {"plugins":{
                  "example":{"1.0":{"requiredCore":"2.300","sha256":"old-checksum","dependencies":[]}},
                  "helper":{"1.0":{"requiredCore":"2.300","sha256":"helper-checksum","dependencies":[]}}
                }}
                """)));
        manager.start();
        server.verify(4, getRequestedFor(urlPathEqualTo("/plugin-versions.json")));
    }
}
