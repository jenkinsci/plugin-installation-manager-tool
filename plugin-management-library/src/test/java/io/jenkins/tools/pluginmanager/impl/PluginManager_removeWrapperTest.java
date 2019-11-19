package io.jenkins.tools.pluginmanager.impl;

import io.jenkins.tools.pluginmanager.config.Config;
import io.jenkins.tools.pluginmanager.config.Settings;
import org.json.JSONObject;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PluginManager_removeWrapperTest {

    @Test
    public void removeWrapperTest() {
        String json = "{'json':'content', 'here':true}";
        String noActual = "updateCenter.post(" + json + ");";

        Config config = prepareMockConfig();
        PluginManager objectUnderTest = new PluginManager(config);

        String result = objectUnderTest.removePossibleWrapperText(noActual);
        assert result.equals(json);
        new JSONObject(result); // This asserts the result is valid json
    }

    @Test
    public void removeWrapperWithNoWrapperTest() {
        String json = "{'json':'content', 'here':true}";

        Config config = prepareMockConfig();
        PluginManager objectUnderTest = new PluginManager(config);

        String result = objectUnderTest.removePossibleWrapperText(json);
        assert result.equals(json);
        new JSONObject(result); // This asserts the result is valid json
    }

    @Test
    public void removeWrapperWithTrailingNewLineTest() {
        String json = "{'json':'content', 'here':true}";
        String noActual = "updateCenter.post(" + json + ");\n";

        Config config = prepareMockConfig();
        PluginManager objectUnderTest = new PluginManager(config);

        String result = objectUnderTest.removePossibleWrapperText(noActual);
        assert result.equals(json);
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

        Config config = prepareMockConfig();
        PluginManager objectUnderTest = new PluginManager(config);

        String result = objectUnderTest.removePossibleWrapperText(noActual);
        assertThat(json, is(result));
        new JSONObject(result); // This asserts the result is valid json
    }

    private Config prepareMockConfig() {
        Config config = mock(Config.class);
        when(config.getJenkinsWar()).thenReturn(Settings.DEFAULT_WAR);
        when(config.getJenkinsUc()).thenReturn(Settings.DEFAULT_UPDATE_CENTER);
        return config;
    }

}
