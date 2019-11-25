package io.jenkins.tools.pluginmanager.impl;

import org.json.JSONObject;
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class PluginManagerUtilsTest {

    @Test
    public void appendPathOntoUrlObjectsTest() throws MalformedURLException {
        String result = PluginManagerUtils.appendPathOntoUrl(new URL("http://bob.com:8080"), new StringBuilder("file.json"));

        assertThat(result, is("http://bob.com:8080/file.json"));
    }

    @Test
    public void appendPathOntoUrlObjectsMultiSegmentTest() throws MalformedURLException {
        String result = PluginManagerUtils.appendPathOntoUrl(new URL("http://bob.com:8080"), new StringBuilder("path/to"), new StringBuilder("file.json"));

        assertThat(result, is("http://bob.com:8080/path/to/file.json"));
    }

    @Test
    public void appendPathOntoUrlTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("http://bob.com:8080", "file.json");

        assertThat(result, is("http://bob.com:8080/file.json"));
    }

    @Test
    public void appendPathOntoUrlTestBlankUrl() {
        String result = PluginManagerUtils.appendPathOntoUrl("", "file.json");

        assertThat(result, is("file.json"));
    }

    @Test
    public void appendPathOntoUrlTestNoUrl() {
        String result = PluginManagerUtils.appendPathOntoUrl(null, "file.json");

        assertThat(result, is("file.json"));
    }

    @Test
    public void appendPathOntoUrlWithUrlTrailingSlashTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("http://bob.com:8080/", "file.json");

        assertThat(result, is("http://bob.com:8080/file.json"));
    }

    @Test
    public void appendPathOntoUrlWithPathLeadingSlashTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("http://bob.com:8080", "/file.json");

        assertThat(result, is("http://bob.com:8080/file.json"));
    }

    @Test
    public void appendPathOntoUrlWithPathTrailingAndLeadingSlashTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("http://bob.com:8080/", "/file.json");

        assertThat(result, is("http://bob.com:8080/file.json"));
    }

    @Test
    public void appendPathOntoUrlNoDomainNameTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("anything://", "bob.com/file.json");

        assertThat(result, is("anything://bob.com/file.json"));
    }

    @Test
    public void appendPathOntoUrlWithBlankPathTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("http://bob.com:8080", "");

        assertThat(result, is("http://bob.com:8080/"));
    }

    @Test
    public void appendPathOntoUrlWithNoPathTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("http://bob.com:8080", (String[]) null);

        assertThat(result, is("http://bob.com:8080/"));
    }

    @Test
    public void appendPathOntoUrlWithNullPathTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("http://bob.com:8080", (String) null);

        assertThat(result, is("http://bob.com:8080/"));
    }

    @Test
    public void appendPathOntoUrlMultiSegmentTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("http://bob.com:8080", "path/to", "file.json");

        assertThat(result, is("http://bob.com:8080/path/to/file.json"));
    }

    @Test
    public void appendPathOntoUrlMultiSegmentTrailingSlashTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("http://bob.com:8080/", "path/to/", "file.json");

        assertThat(result, is("http://bob.com:8080/path/to/file.json"));
    }

    @Test
    public void appendPathOntoUrlMultiSegmentLeadingSlashTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("http://bob.com:8080", "/path/to", "/file.json");

        assertThat(result, is("http://bob.com:8080/path/to/file.json"));
    }

    @Test
    public void appendPathOntoUrlMultiSegmentTrailingAndLeadingSlashTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("http://bob.com:8080/", "/path/to/", "/file.json");

        assertThat(result, is("http://bob.com:8080/path/to/file.json"));
    }

    @Test
    public void appendPathOntoUrlMultiSegmentOneBlankTest() {
        String result = PluginManagerUtils.appendPathOntoUrl("http://bob.com:8080/", "", "/file.json");

        assertThat(result, is("http://bob.com:8080/file.json"));
    }

    @Test
    public void removeWrapperTest() {
        String json = "{'json':'content', 'here':true}";
        String noActual = "updateCenter.post(" + json + ");";

        String result = PluginManagerUtils.removePossibleWrapperText(noActual);
        assertThat(json, is(result));

        new JSONObject(result); // This asserts the result is valid json
    }

    @Test
    public void removeWrapperWithNoWrapperTest() {
        String json = "{'json':'content', 'here':true}";

        String result = PluginManagerUtils.removePossibleWrapperText(json);
        assertThat(json, is(result));

        new JSONObject(result); // This asserts the result is valid json
    }

    @Test
    public void removeWrapperWithTrailingNewLineTest() {
        String json = "{'json':'content', 'here':true}";
        String noActual = "updateCenter.post(" + json + ");\n";

        String result = PluginManagerUtils.removePossibleWrapperText(noActual);
        assertThat(json, is(result));

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
        assertThat(json, is(result));
        new JSONObject(result); // This asserts the result is valid json
    }

    @Test
    public void dirnameObjectTest() throws MalformedURLException {
        String result = PluginManagerUtils.dirName(new URL("http://bob.com/path/to/file.json"));

        assertThat(result, is("http://bob.com/path/to/"));
    }

    @Test
    public void dirnameTest() {
        String result = PluginManagerUtils.dirName("http://bob.com/path/to/file.json");

        assertThat(result, is("http://bob.com/path/to/"));
    }

    @Test
    public void dirnameTestNoPath() {
        String result = PluginManagerUtils.dirName("http://bob.com");

        assertThat(result, is("http://"));
    }

    @Test
    public void dirnameTestNoSlashes() {
        String result = PluginManagerUtils.dirName("bob.com");

        assertThat(result, is("bob.com"));
    }

    @Test
    public void insertPathPreservingFilenameObjectTest() throws MalformedURLException {
        String result = PluginManagerUtils.insertPathPreservingFilename(new URL("http://bob.com/update-center.json"), new StringBuilder("2.190"));

        assertThat(result, is("http://bob.com/2.190/update-center.json"));
    }

    @Test
    public void insertPathPreservingFilenameTest() {
        String result = PluginManagerUtils.insertPathPreservingFilename("http://bob.com/update-center.json", "2.190");

        assertThat(result, is("http://bob.com/2.190/update-center.json"));
    }

    @Test
    public void insertPathPreservingFilenameLongTest() {
        String result = PluginManagerUtils.insertPathPreservingFilename("http://bob.com/updates/plugins/update-center.json", "2.190");

        assertThat(result, is("http://bob.com/updates/plugins/2.190/update-center.json"));
    }

}
