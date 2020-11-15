package io.jenkins.tools.pluginmanager.parsers;

import io.jenkins.tools.pluginmanager.impl.Plugin;
import java.util.List;
import org.junit.Test;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class TxtOutputConverterTest extends BaseParserTest {

    @Test
    public void convert() {
        List<Plugin> originalPlugins = singletonList(plugin("mailer", "1.31"));
        List<Plugin> latestVersionsOfPlugins = pm.getLatestVersionsOfPlugins(originalPlugins);

        String converted = new TxtOutputConverter().convert(latestVersionsOfPlugins);
        assertThat(converted)
                .isEqualTo("mailer:1.32.1");
    }

    @Test
    public void convertIncrementals() {
        List<Plugin> originalPlugins = singletonList(plugin("workflow-support", "2.19-rc289.d09828a05a74", "org.jenkins-ci.plugins.workflow"));
        List<Plugin> latestVersionsOfPlugins = pm.getLatestVersionsOfPlugins(originalPlugins);

        String converted = new TxtOutputConverter().convert(latestVersionsOfPlugins);
        assertThat(converted)
                .isEqualTo("workflow-support:incrementals;org.jenkins-ci.plugins.workflow;2.19-rc289.d09828a05a74");
    }

    @Test
    public void convertUrl() {
        List<Plugin> originalPlugins = singletonList(pluginWithUrl("script-security", "http://ftp-chi.osuosl.org/pub/jenkins/plugins/script-security/1.56/script-security.hpi"));
        List<Plugin> latestVersionsOfPlugins = pm.getLatestVersionsOfPlugins(originalPlugins);

        String converted = new TxtOutputConverter().convert(latestVersionsOfPlugins);
        assertThat(converted)
                .isEqualTo("script-security:latest:http://ftp-chi.osuosl.org/pub/jenkins/plugins/script-security/1.56/script-security.hpi");
    }

    @Test
    public void convertNoUpdated() {
        List<Plugin> originalPlugins = singletonList(plugin("mailer", "1.32.1"));
        List<Plugin> latestVersionsOfPlugins = pm.getLatestVersionsOfPlugins(originalPlugins);

        String converted = new TxtOutputConverter().convert(latestVersionsOfPlugins);
        assertThat(converted)
                .isEqualTo("mailer:1.32.1");
    }
}
