package io.jenkins.tools.pluginmanager.impl;

import io.jenkins.tools.pluginmanager.config.Config;
import org.json.JSONObject;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class PluginManagerUtilsTest {

    @Test
    public void appendPathOntoUrlTest() {
        String url = "http://bob.com:8080";
        String path = "file.json";

        String result = PluginManagerUtils.appendPathOntoUrl(url, path);

        assertThat(result, is("http://bob.com:8080/file.json"));
    }

    @Test
    public void appendPathOntoUrlWithUrlTrailingSlashTest() {
        String url = "http://bob.com:8080";
        String path = "file.json";

        String result = PluginManagerUtils.appendPathOntoUrl(url + "/", path);

        assertThat(result, is("http://bob.com:8080/file.json"));
    }

    @Test
    public void appendPathOntoUrlWithPathLeadingSlashTest() {
        String url = "http://bob.com:8080";
        String path = "file.json";

        String result = PluginManagerUtils.appendPathOntoUrl(url, "/" + path);

        assertThat(result, is("http://bob.com:8080/file.json"));
    }

    @Test
    public void appendPathOntoUrlNoDomainNameTest() {
        String url = "anything://";
        String path = "bob.com/file.json";

        String result = PluginManagerUtils.appendPathOntoUrl(url, path);

        assertThat(result, is("anything://bob.com/file.json"));
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

}
