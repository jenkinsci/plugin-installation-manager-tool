package io.jenkins.tools.pluginmanager.cli;

import io.jenkins.tools.pluginmanager.util.PluginListParser;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class URLValidatorTest {

    static Stream<Arguments> data() {
        return Stream.of(
            arguments("https://domain.local/my-plugin.hpi"),
            arguments("https://updates.jenkins.io/latest/google-api-client-plugin.hpi"),
            arguments("ftp://jenkins.io")
        );
    }


    @ParameterizedTest(name = "{index}: isURL({0})")
    @MethodSource("data")
    void validURLs(String url) {
        assertThat(PluginListParser.isURL(url)).isTrue();
    }
}
