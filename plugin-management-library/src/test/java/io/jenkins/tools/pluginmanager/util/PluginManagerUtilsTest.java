package io.jenkins.tools.pluginmanager.util;

import java.net.MalformedURLException;
import java.net.URL;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PluginManagerUtilsTest {

    @Test
    void appendPathOntoUrlObjectsTest() throws MalformedURLException {
        String result = PluginManagerUtils.appendPathOntoUrl(new URL("http://bob.com:8080"), new StringBuilder("file.json"));

        assertThat(result).isEqualTo("http://bob.com:8080/file.json");
    }

    @Test
    void appendPathOntoUrlObjectsMultiSegmentTest() throws MalformedURLException {
        String result = PluginManagerUtils.appendPathOntoUrl(new URL("http://bob.com:8080"), new StringBuilder("path/to"), new StringBuilder("file.json"));

        assertThat(result).isEqualTo("http://bob.com:8080/path/to/file.json");
    }

    @Test
    void appendPathOntoUrlTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("http://bob.com:8080", "file.json");

        assertThat(result).isEqualTo("http://bob.com:8080/file.json");
    }

    @Test
    void appendPathOntoUrlTestBlankUrl() {
        String result = PluginManagerUtils.appendPathOntoUrl("", "file.json");

        assertThat(result).isEqualTo("file.json");
    }

    @Test
    void appendPathOntoUrlTestNoUrl() {
        String result = PluginManagerUtils.appendPathOntoUrl(null, "file.json");

        assertThat(result).isEqualTo("file.json");
    }

    @Test
    void appendPathOntoUrlWithUrlTrailingSlashTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("http://bob.com:8080/", "file.json");

        assertThat(result).isEqualTo("http://bob.com:8080/file.json");
    }

    @Test
    void appendPathOntoUrlWithPathLeadingSlashTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("http://bob.com:8080", "/file.json");

        assertThat(result).isEqualTo("http://bob.com:8080/file.json");
    }

    @Test
    void appendPathOntoUrlWithPathTrailingAndLeadingSlashTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("http://bob.com:8080/", "/file.json");

        assertThat(result).isEqualTo("http://bob.com:8080/file.json");
    }

    @Test
    void appendPathOntoUrlNoDomainNameTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("anything://", "bob.com/file.json");

        assertThat(result).isEqualTo("anything://bob.com/file.json");
    }

    @Test
    void appendPathOntoUrlWithBlankPathTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("http://bob.com:8080", "");

        assertThat(result).isEqualTo("http://bob.com:8080");
    }

    @Test
    void appendPathOntoUrlWithNoPathTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("http://bob.com:8080", (String[]) null);

        assertThat(result).isEqualTo("http://bob.com:8080");
    }

    @Test
    void appendPathOntoUrlWithNullPathTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("http://bob.com:8080", (String) null);

        assertThat(result).isEqualTo("http://bob.com:8080");
    }

    @Test
    void appendPathOntoUrlMultiSegmentTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("http://bob.com:8080", "path/to", "file.json");

        assertThat(result).isEqualTo("http://bob.com:8080/path/to/file.json");
    }

    @Test
    void appendPathOntoUrlMultiSegmentTrailingSlashTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("http://bob.com:8080/", "path/to/", "file.json");

        assertThat(result).isEqualTo("http://bob.com:8080/path/to/file.json");
    }

    @Test
    void appendPathOntoUrlMultiSegmentLeadingSlashTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("http://bob.com:8080", "/path/to", "/file.json");

        assertThat(result).isEqualTo("http://bob.com:8080/path/to/file.json");
    }

    @Test
    void appendPathOntoUrlMultiSegmentTrailingAndLeadingSlashTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("http://bob.com:8080/", "/path/to/", "/file.json");

        assertThat(result).isEqualTo("http://bob.com:8080/path/to/file.json");
    }

    @Test
    void appendPathOntoUrlMultiSegmentOneBlankTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("http://bob.com:8080/", "", "/file.json");

        assertThat(result).isEqualTo("http://bob.com:8080/file.json");
    }

    @Test
    void removeWrapperTest() {
        String json = "{'json':'content', 'here':true}";
        String noActual = "updateCenter.post(" + json + ");";

        String result = PluginManagerUtils.removePossibleWrapperText(noActual);
        assertThat(result).isEqualTo(json);

        new JSONObject(result); // This asserts the result is valid json
    }

    @Test
    void removeWrapperWithNoWrapperTest() {
        String json = "{'json':'content', 'here':true}";

        String result = PluginManagerUtils.removePossibleWrapperText(json);
        assertThat(result).isEqualTo(json);

        new JSONObject(result); // This asserts the result is valid json
    }

    @Test
    void removeWrapperWithTrailingNewLineTest() {
        String json = "{'json':'content', 'here':true}";
        String noActual = "updateCenter.post(" + json + ");\n";

        String result = PluginManagerUtils.removePossibleWrapperText(noActual);
        assertThat(result).isEqualTo(json);

        new JSONObject(result); // This asserts the result is valid json
    }

    @Test
    void removeWrapperWithInternalNewLinesTest() {
        // CHECKSTYLE:OFF
        String json = """
            
            {
            'json':'content',
            'here':true
            }
            """;
        // CHECKSTYLE:ON
        String noActual = "updateCenter.post(" + json + ");";

        String result = PluginManagerUtils.removePossibleWrapperText(noActual);
        assertThat(result).isEqualTo(json);
        new JSONObject(result); // This asserts the result is valid json
    }

    @Test
    void dirnameObjectTest() throws MalformedURLException {
        String result = PluginManagerUtils.dirName(new URL("http://bob.com/path/to/file.json"));

        assertThat(result).isEqualTo("http://bob.com/path/to/");
    }

    @Test
    void dirnameTest() {
        String result = PluginManagerUtils.dirName("http://bob.com/path/to/file.json");

        assertThat(result).isEqualTo("http://bob.com/path/to/");
    }

    @Test
    void dirnameTestNoPath() {
        String result = PluginManagerUtils.dirName("http://bob.com");

        assertThat(result).isEqualTo("http://bob.com");
    }

    @Test
    void dirnameTestNoPathPlusPort() {
        String result = PluginManagerUtils.dirName("http://bob.com:8080");

        assertThat(result).isEqualTo("http://bob.com:8080");
    }

    @Test
    void dirnameTestNoSlashes() {
        String result = PluginManagerUtils.dirName("bob.com");

        assertThat(result).isEqualTo("bob.com");
    }

    @Test
    void removePathTest() throws MalformedURLException {
        String result = PluginManagerUtils.removePath(new URL("http://bob.com/path/to/file.json"));

        assertThat(result).isEqualTo("http://bob.com");
    }

    @Test
    void removePathTestWithPort() {
        String result = PluginManagerUtils.removePath("http://bob.com:8080/path/to/file.json");

        assertThat(result).isEqualTo("http://bob.com:8080");
    }

    @Test
    void removePathTestNoPath() {
        String result = PluginManagerUtils.removePath("http://bob.com");

        assertThat(result).isEqualTo("http://bob.com");
    }
}
