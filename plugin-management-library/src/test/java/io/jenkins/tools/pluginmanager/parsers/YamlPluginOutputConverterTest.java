package io.jenkins.tools.pluginmanager.parsers;

import io.jenkins.tools.pluginmanager.impl.Plugin;
import java.util.List;
import org.junit.Test;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class YamlPluginOutputConverterTest extends BaseParserTest {

    @Test
    public void convert() {
        List<Plugin> originalPlugins = singletonList(plugin("mailer", "1.31"));
        List<Plugin> latestVersionsOfPlugins = pm.getLatestVersionsOfPlugins(originalPlugins);

        String converted = new YamlPluginOutputConverter().convert(latestVersionsOfPlugins);
        assertThat(converted)
                .isEqualTo("plugins:\n" +
                        "- artifactId: \"mailer\"\n" +
                        "  source:\n" +
                        "    version: \"1.32.1\"\n");
    }

    @Test
    public void convertIncrementals() {
        List<Plugin> originalPlugins = singletonList(plugin("workflow-support", "2.19-rc289.d09828a05a74", "org.jenkins-ci.plugins.workflow"));
        List<Plugin> latestVersionsOfPlugins = pm.getLatestVersionsOfPlugins(originalPlugins);

        String converted = new YamlPluginOutputConverter().convert(latestVersionsOfPlugins);
        assertThat(converted)
                .isEqualTo("plugins:\n" +
                        "- artifactId: \"workflow-support\"\n" +
                        "  groupId: \"org.jenkins-ci.plugins.workflow\"\n" +
                        "  source:\n" +
                        "    version: \"2.19-rc289.d09828a05a74\"\n");
    }

    @Test
    public void convertUrl() {
        List<Plugin> originalPlugins = singletonList(pluginWithUrl("script-security", "http://ftp-chi.osuosl.org/pub/jenkins/plugins/script-security/1.56/script-security.hpi"));
        List<Plugin> latestVersionsOfPlugins = pm.getLatestVersionsOfPlugins(originalPlugins);

        String converted = new YamlPluginOutputConverter().convert(latestVersionsOfPlugins);
        assertThat(converted)
                .isEqualTo("plugins:\n" +
                        "- artifactId: \"script-security\"\n" +
                        "  source:\n" +
                        "    version: \"latest\"\n" +
                        "    url: \"http://ftp-chi.osuosl.org/pub/jenkins/plugins/script-security/1.56/script-security.hpi\"\n");
    }

    @Test
    public void convertNoUpdates() {
        List<Plugin> originalPlugins = singletonList(plugin("mailer", "1.32.1"));
        List<Plugin> latestVersionsOfPlugins = pm.getLatestVersionsOfPlugins(originalPlugins);

        String converted = new YamlPluginOutputConverter().convert(latestVersionsOfPlugins);
        assertThat(converted)
                .isEqualTo("plugins:\n" +
                        "- artifactId: \"mailer\"\n" +
                        "  source:\n" +
                        "    version: \"1.32.1\"\n");
    }
}
