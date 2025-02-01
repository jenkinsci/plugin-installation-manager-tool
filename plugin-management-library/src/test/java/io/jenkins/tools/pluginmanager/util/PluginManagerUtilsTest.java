package io.jenkins.tools.pluginmanager.util;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.jenkins.tools.pluginmanager.config.Settings;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static io.jenkins.tools.pluginmanager.util.PluginManagerUtils.constructUrl;
import static io.jenkins.tools.pluginmanager.util.PluginManagerUtils.isValidUrl;
import static io.jenkins.tools.pluginmanager.util.PluginManagerUtils.resolveArchiveUpdateCenterUrl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

public class PluginManagerUtilsTest {

    @Test
    public void appendPathOntoUrlObjectsTest() throws MalformedURLException {
        String result = PluginManagerUtils.appendPathOntoUrl(new URL("http://bob.com:8080"), new StringBuilder("file.json"));

        assertThat(result).isEqualTo("http://bob.com:8080/file.json");
    }

    @Test
    public void appendPathOntoUrlObjectsMultiSegmentTest() throws MalformedURLException {
        String result = PluginManagerUtils.appendPathOntoUrl(new URL("http://bob.com:8080"), new StringBuilder("path/to"), new StringBuilder("file.json"));

        assertThat(result).isEqualTo("http://bob.com:8080/path/to/file.json");
    }

    @Test
    public void appendPathOntoUrlTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("http://bob.com:8080", "file.json");

        assertThat(result).isEqualTo("http://bob.com:8080/file.json");
    }

    @Test
    public void appendPathOntoUrlTestBlankUrl() {
        String result = PluginManagerUtils.appendPathOntoUrl("", "file.json");

        assertThat(result).isEqualTo("file.json");
    }

    @Test
    public void appendPathOntoUrlTestNoUrl() {
        String result = PluginManagerUtils.appendPathOntoUrl(null, "file.json");

        assertThat(result).isEqualTo("file.json");
    }

    @Test
    public void appendPathOntoUrlWithUrlTrailingSlashTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("http://bob.com:8080/", "file.json");

        assertThat(result).isEqualTo("http://bob.com:8080/file.json");
    }

    @Test
    public void appendPathOntoUrlWithPathLeadingSlashTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("http://bob.com:8080", "/file.json");

        assertThat(result).isEqualTo("http://bob.com:8080/file.json");
    }

    @Test
    public void appendPathOntoUrlWithPathTrailingAndLeadingSlashTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("http://bob.com:8080/", "/file.json");

        assertThat(result).isEqualTo("http://bob.com:8080/file.json");
    }

    @Test
    public void appendPathOntoUrlNoDomainNameTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("anything://", "bob.com/file.json");

        assertThat(result).isEqualTo("anything://bob.com/file.json");
    }

    @Test
    public void appendPathOntoUrlWithBlankPathTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("http://bob.com:8080", "");

        assertThat(result).isEqualTo("http://bob.com:8080");
    }

    @Test
    public void appendPathOntoUrlWithNoPathTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("http://bob.com:8080", (String[]) null);

        assertThat(result).isEqualTo("http://bob.com:8080");
    }

    @Test
    public void appendPathOntoUrlWithNullPathTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("http://bob.com:8080", (String) null);

        assertThat(result).isEqualTo("http://bob.com:8080");
    }

    @Test
    public void appendPathOntoUrlMultiSegmentTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("http://bob.com:8080", "path/to", "file.json");

        assertThat(result).isEqualTo("http://bob.com:8080/path/to/file.json");
    }

    @Test
    public void appendPathOntoUrlMultiSegmentTrailingSlashTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("http://bob.com:8080/", "path/to/", "file.json");

        assertThat(result).isEqualTo("http://bob.com:8080/path/to/file.json");
    }

    @Test
    public void appendPathOntoUrlMultiSegmentLeadingSlashTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("http://bob.com:8080", "/path/to", "/file.json");

        assertThat(result).isEqualTo("http://bob.com:8080/path/to/file.json");
    }

    @Test
    public void appendPathOntoUrlMultiSegmentTrailingAndLeadingSlashTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("http://bob.com:8080/", "/path/to/", "/file.json");

        assertThat(result).isEqualTo("http://bob.com:8080/path/to/file.json");
    }

    @Test
    public void appendPathOntoUrlMultiSegmentOneBlankTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("http://bob.com:8080/", "", "/file.json");

        assertThat(result).isEqualTo("http://bob.com:8080/file.json");
    }

    @Test
    public void removeWrapperTest() {
        String json = "{'json':'content', 'here':true}";
        String noActual = "updateCenter.post(" + json + ");";

        String result = PluginManagerUtils.removePossibleWrapperText(noActual);
        assertThat(result).isEqualTo(json);

        new JSONObject(result); // This asserts the result is valid json
    }

    @Test
    public void removeWrapperWithNoWrapperTest() {
        String json = "{'json':'content', 'here':true}";

        String result = PluginManagerUtils.removePossibleWrapperText(json);
        assertThat(result).isEqualTo(json);

        new JSONObject(result); // This asserts the result is valid json
    }

    @Test
    public void removeWrapperWithTrailingNewLineTest() {
        String json = "{'json':'content', 'here':true}";
        String noActual = "updateCenter.post(" + json + ");\n";

        String result = PluginManagerUtils.removePossibleWrapperText(noActual);
        assertThat(result).isEqualTo(json);

        new JSONObject(result); // This asserts the result is valid json
    }

    @Test
    public void removeWrapperWithInternalNewLinesTest() {
        String json = "\n" +
                "{\n" +
                "'json':'content',\n" +
                "'here':true\n" +
                "}\n";
        String noActual = "updateCenter.post(" + json + ");";

        String result = PluginManagerUtils.removePossibleWrapperText(noActual);
        assertThat(result).isEqualTo(json);
        new JSONObject(result); // This asserts the result is valid json
    }

    @Test
    public void dirnameObjectTest() throws MalformedURLException {
        String result = PluginManagerUtils.dirName(new URL("http://bob.com/path/to/file.json"));

        assertThat(result).isEqualTo("http://bob.com/path/to/");
    }

    @Test
    public void dirnameTest() {
        String result = PluginManagerUtils.dirName("http://bob.com/path/to/file.json");

        assertThat(result).isEqualTo("http://bob.com/path/to/");
    }

    @Test
    public void dirnameTestNoPath() {
        String result = PluginManagerUtils.dirName("http://bob.com");

        assertThat(result).isEqualTo("http://bob.com");
    }

    @Test
    public void dirnameTestNoPathPlusPort() {
        String result = PluginManagerUtils.dirName("http://bob.com:8080");

        assertThat(result).isEqualTo("http://bob.com:8080");
    }

    @Test
    public void dirnameTestNoSlashes() {
        String result = PluginManagerUtils.dirName("bob.com");

        assertThat(result).isEqualTo("bob.com");
    }

    @Test
    public void removePathTest() throws MalformedURLException {
        String result = PluginManagerUtils.removePath(new URL("http://bob.com/path/to/file.json"));

        assertThat(result).isEqualTo("http://bob.com");
    }

    @Test
    public void removePathTestWithPort() {
        String result = PluginManagerUtils.removePath("http://bob.com:8080/path/to/file.json");

        assertThat(result).isEqualTo("http://bob.com:8080");
    }

    @Test
    public void removePathTestNoPath() {
        String result = PluginManagerUtils.removePath("http://bob.com");

        assertThat(result).isEqualTo("http://bob.com");
    }

    @Test
    public void resolveArchiveUpdateCenterUrlTestWithSuccessResult() {
        String jenkinsCacheSuffix = "-2.414.3";
        URL archiveRepoUrl = Settings.DEFAULT_ARCHIVE_REPO_MIRROR;
        URL result = resolveArchiveUpdateCenterUrl(jenkinsCacheSuffix, archiveRepoUrl);

        assertThat(result).isNotNull();
        assertThat(result.toString()).endsWith("updates/dynamic-stable-2.414.3/update-center.json");
    }

    @Test
    public void resolveArchiveUpdateCenterUrlTestWithInvalidCacheSuffix() {
        assertThrows(IllegalArgumentException.class, () -> {
            resolveArchiveUpdateCenterUrl("invalidSuffix", new URL("https://example.jenkins.org/"));
        });
    }

    @Test
    public void resolveArchiveUpdateCenterUrlTestWithNullCacheSuffix() {
        assertThrows(IllegalArgumentException.class, () -> {
            resolveArchiveUpdateCenterUrl(null, new URL("https://example.jenkins.org/"));
        });
    }

    @SuppressFBWarnings("NAB_NEEDLESS_BOOLEAN_CONSTANT_CONVERSION")
    @Test
    public void resolveArchiveUpdateCenterUrlTestWithNoValidUrl() throws Exception {
        try (MockedStatic<PluginManagerUtils> mockedUtils = Mockito.mockStatic(PluginManagerUtils.class)) {
            mockedUtils.when(() -> PluginManagerUtils.constructUrl(anyString(), anyString())).thenReturn(null);
            mockedUtils.when(() -> PluginManagerUtils.isValidUrl(any(URL.class))).thenReturn(false);
            URL result = PluginManagerUtils.resolveArchiveUpdateCenterUrl("-2.414.3", new URL("https://example.jenkins.org/"));
            assertThat(result).isNull();
        }
    }

    @Test
    public void resolveArchiveUpdateCenterUrlTestWithNullUrl() {
        String jenkinsCacheSuffix = "-2.216";
        URL result = resolveArchiveUpdateCenterUrl(jenkinsCacheSuffix, null);

        assertThat(result).isNull();
    }

    @Test
    public void resolveArchiveUpdateCenterUrlTestWithInvalidBaseUrl() {
        assertThrows(InvalidUrlException.class, () -> {
            resolveArchiveUpdateCenterUrl("-2.414.3", new URL("ftp://invalid.url/"));
        });
    }

    @Test
    public void isValidUrlTestWithNullUrl() {
        assertThrows(IllegalArgumentException.class, () -> {
                isValidUrl(null);
        });
    }

    @Test
    public void isValidUrlTestWithInvalidProtocol() throws Exception {
        URL url = new URL("ftp://example.jenkins.com");
        assertThrows(IllegalArgumentException.class, () -> {
            isValidUrl(url);
        });
    }

    @Test
    public void isValidUrlTestWithNullHost() throws Exception {
        URL url = new URL("http://");
        assertThrows(IllegalArgumentException.class, () -> {
            isValidUrl(url);
        });
    }

    @SuppressFBWarnings("URLCONNECTION_SSRF_FD")
    @Test
    public void isValidUrlTestWithSuccessResult() throws Exception {
        URL url = new URL("https://example.jenkins.org");
        URL spyUrl = Mockito.spy(url);
        HttpURLConnection mockConnection = Mockito.mock(HttpURLConnection.class);
        Mockito.when(spyUrl.openConnection()).thenReturn(mockConnection);
        Mockito.when(mockConnection.getResponseCode()).thenReturn(200);
        assertThat(isValidUrl(spyUrl)).isTrue();
    }

    @SuppressFBWarnings("URLCONNECTION_SSRF_FD")
    @Test
    public void isValidUrlTestWithIOException() throws Exception {
        URL url = new URL("https://example.jenkins.org");
        URL spyUrl = Mockito.spy(url);
        HttpURLConnection mockConnection = Mockito.mock(HttpURLConnection.class);
        Mockito.when(spyUrl.openConnection()).thenReturn(mockConnection);
        Mockito.when(mockConnection.getResponseCode()).thenThrow(new IOException("Connection failed"));
        assertThat(isValidUrl(spyUrl)).isFalse();
    }

    @SuppressFBWarnings("URLCONNECTION_SSRF_FD")
    @Test
    public void isValidUrlTestWithErrorResponseCode() throws Exception {
        URL url = new URL("https://example.jenkins.org");
        URL spyUrl = Mockito.spy(url);
        HttpURLConnection mockConnection = Mockito.mock(HttpURLConnection.class);
        Mockito.when(spyUrl.openConnection()).thenReturn(mockConnection);
        Mockito.when(mockConnection.getResponseCode()).thenReturn(404);
        assertThat(isValidUrl(url)).isFalse();
    }

    @Test
    public void constructUrlTestWithNullBaseUrl() {
        assertThatThrownBy(() -> constructUrl(null, "/path"))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessage("Base URL must not be null or empty");
    }

    @Test
    public void constructUrlTestWithEmptyBaseUrl() {
        assertThatThrownBy(() -> constructUrl("", "/path"))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessage("Base URL must not be null or empty");
    }

    @Test
    public void constructUrlTestWithNullPath() {
        assertThatThrownBy(() -> constructUrl("https://example.jenkins.org", null))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessage("Path must not be null");
    }

    @Test
    public void constructUrlTestWithInvalidBaseUrlScheme() {
        assertThatThrownBy(() -> constructUrl("ftp://example.jenkins.org", "/path"))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessage("Base URL must start with http:// or https://");
    }

    @Test
    public void constructUrlTestWithMissingHost() {
        assertThatThrownBy(() -> constructUrl("https://", "/path"))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessage("Invalid URL: host is missing");
    }

    @Test
    public void constructUrlTestWithSuccessResult() {
        URL url = constructUrl("https://example.jenkins.org", "/path");
        assertThat(url).isNotNull();
        assertThat(url.toString()).hasToString("https://example.jenkins.org/path");
    }

    @Test
    public void constructUrlTestWithInvalidUrlSyntax() {
        assertThatThrownBy(() -> constructUrl("https://example.jenkins.org", "/path with spaces"))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageStartingWith("Invalid URL construction:");
    }

    @Test
    public void constructUrlTestWithMalformedUrl() {
        assertThatThrownBy(() -> constructUrl("https://example.jenkins.org", "/path#invalid#fragment"))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageStartingWith("Invalid URL construction:");
    }
}
