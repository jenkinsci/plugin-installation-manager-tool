package io.jenkins.tools.pluginmanager.cli;

import io.jenkins.tools.pluginmanager.util.PluginListParser;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class URLValidatorTest {

    private final String url;

    @Parameters(name = "{index}: isURL({0})")
    public static Object[] data() {
        return new Object[] {
            "https://domain.local/my-plugin.hpi",
            "https://updates.jenkins.io/latest/google-api-client-plugin.hpi",
            "ftp://jenkins.io"
        };
    }

    public URLValidatorTest(String url) {
        this.url = url;
    }

    @Test
    public void validURLs() {
        assertThat(PluginListParser.isURL(url)).isTrue();
    }
}
