package io.jenkins.tools.pluginmanager.util;

import java.net.MalformedURLException;
import java.net.URL;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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

}
