package io.jenkins.tools.pluginmanager.parsers;

import io.jenkins.tools.pluginmanager.impl.Plugin;
import java.util.List;
import org.junit.jupiter.api.Test;

import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

class AvailableUpdatesStdOutPluginOutputConverterTest extends BaseParserTest {

    @Test
    void convert() {
        List<Plugin> originalPlugins = singletonList(plugin("mailer", "1.31"));
        List<Plugin> latestVersionsOfPlugins = pm.getLatestVersionsOfPlugins(originalPlugins);

        String converted = new AvailableUpdatesStdOutPluginOutputConverter(originalPlugins).convert(latestVersionsOfPlugins);
        assertThat(converted.trim().replaceAll("\r\n", "\n"))
                .isEqualTo(format("Available updates:%n" +
                        "mailer (1.31) has an available update: 1.32.1%n").trim().replaceAll("\r\n", "\n"));
    }

    @Test
    void convertNoUpdated() {
        List<Plugin> originalPlugins = singletonList(plugin("mailer", "1.32.1"));
        List<Plugin> latestVersionsOfPlugins = pm.getLatestVersionsOfPlugins(originalPlugins);

        String converted = new AvailableUpdatesStdOutPluginOutputConverter(originalPlugins).convert(latestVersionsOfPlugins);
        assertThat(converted)
                .isEqualTo("No available updates\n");
    }
}
