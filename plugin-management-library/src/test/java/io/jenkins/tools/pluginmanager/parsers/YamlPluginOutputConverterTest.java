package io.jenkins.tools.pluginmanager.parsers;

import io.jenkins.tools.pluginmanager.impl.Plugin;
import java.util.List;
import org.junit.jupiter.api.Test;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

class YamlPluginOutputConverterTest extends BaseParserTest {

    @Test
    void convert() {
        List<Plugin> originalPlugins = singletonList(plugin("mailer", "1.31"));
        List<Plugin> latestVersionsOfPlugins = pm.getLatestVersionsOfPlugins(originalPlugins);

        String converted = new YamlPluginOutputConverter().convert(latestVersionsOfPlugins);
        assertThat(converted)
                .isEqualTo("""
                    plugins:
                    - artifactId: "mailer"
                      source:
                        version: "1.32.1"
                    """);
    }

    @Test
    void convertIncrementals() {
        List<Plugin> originalPlugins = singletonList(plugin("workflow-support", "2.19-rc289.d09828a05a74", "org.jenkins-ci.plugins.workflow"));
        List<Plugin> latestVersionsOfPlugins = pm.getLatestVersionsOfPlugins(originalPlugins);

        String converted = new YamlPluginOutputConverter().convert(latestVersionsOfPlugins);
        assertThat(converted)
                .isEqualTo("""
                    plugins:
                    - artifactId: "workflow-support"
                      groupId: "org.jenkins-ci.plugins.workflow"
                      source:
                        version: "2.19-rc289.d09828a05a74"
                    """);
    }

    @Test
    void convertUrl() {
        List<Plugin> originalPlugins = singletonList(pluginWithUrl("script-security", "http://ftp-chi.osuosl.org/pub/jenkins/plugins/script-security/1.56/script-security.hpi"));
        List<Plugin> latestVersionsOfPlugins = pm.getLatestVersionsOfPlugins(originalPlugins);

        String converted = new YamlPluginOutputConverter().convert(latestVersionsOfPlugins);
        assertThat(converted)
                .isEqualTo("""
                    plugins:
                    - artifactId: "script-security"
                      source:
                        version: "latest"
                        url: "http://ftp-chi.osuosl.org/pub/jenkins/plugins/script-security/1.56/script-security.hpi"
                    """);
    }

    @Test
    void convertNoUpdates() {
        List<Plugin> originalPlugins = singletonList(plugin("mailer", "1.32.1"));
        List<Plugin> latestVersionsOfPlugins = pm.getLatestVersionsOfPlugins(originalPlugins);

        String converted = new YamlPluginOutputConverter().convert(latestVersionsOfPlugins);
        assertThat(converted)
                .isEqualTo("""
                    plugins:
                    - artifactId: "mailer"
                      source:
                        version: "1.32.1"
                    """);
    }
}
