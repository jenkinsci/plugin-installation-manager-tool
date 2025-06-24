package io.jenkins.tools.pluginmanager.util;

import io.jenkins.tools.pluginmanager.config.PluginInputException;
import io.jenkins.tools.pluginmanager.impl.Plugin;
import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PluginListParserTest {
    private PluginListParser pluginList;
    private String[] expectedPluginInfo;

    @BeforeEach
    void setup() {
        pluginList = new PluginListParser(true);

        expectedPluginInfo = new String[]{
            new Plugin("git", "latest", null, null).toString(),
            new Plugin("job-import-plugin", "2.1", null, null).toString(),
            new Plugin("docker", "latest", null, null).toString(),
            new Plugin("cloudbees-bitbucket-branch-source", "2.4.4", null, null).toString(),
            new Plugin("script-security", "latest",
                    "http://ftp-chi.osuosl.org/pub/jenkins/plugins/script-security/1.56/script-security.hpi", null)
                    .toString(),
            new Plugin("workflow-step-api",
                    "2.19-rc289.d09828a05a74", null, "org.jenkins-ci.plugins.workflow").toString(),
            new Plugin("matrix-project", "latest", null, null).toString(),
            new Plugin("junit", "experimental", null, null).toString(),
            new Plugin("credentials", "latest",
                    "http://ftp-chi.osuosl.org/pub/jenkins/plugins/credentials/2.2.0/credentials.hpi", null).toString(),
            new Plugin("blueocean", "latest", null, null).toString(),
            new Plugin("google-api-client-plugin", "latest",
                    "https://updates.jenkins.io/latest/google-api-client-plugin.hpi", null).toString(),
            new Plugin("build-timeout", "1.20",
                    null, null).toString()
        };
    }

    @Test
    void parsePluginsFromCliOptionTest() {
        String[] pluginInput = new String[]{"git", "job-import-plugin:2.1",
                "docker:latest",
                "cloudbees-bitbucket-branch-source:2.4.4",
                "script-security::http://ftp-chi.osuosl.org/pub/jenkins/plugins/script-security/1.56/" +
                        "script-security.hpi",
                "workflow-step-api:incrementals;org.jenkins-ci.plugins.workflow;2.19-rc289.d09828a05a74",
                "matrix-project:latest",
                "junit:experimental",
                "credentials:latest:http://ftp-chi.osuosl.org/pub/jenkins/plugins/credentials/2.2.0/credentials.hpi",
                "google-api-client-plugin::https://updates.jenkins.io/latest/google-api-client-plugin.hpi",
                "blueocean:",
                "build-timeout:1.20"};

        List<Plugin> plugins = pluginList.parsePluginsFromCliOption(pluginInput);

        List<String> pluginInfo = new ArrayList<>();
        for (Plugin p : plugins) {
            pluginInfo.add(p.toString());
        }

        assertThat(pluginInfo).containsExactlyInAnyOrder(expectedPluginInfo);

        List<Plugin> noPluginArrayList = pluginList.parsePluginsFromCliOption(null);

        assertThat(noPluginArrayList).isEmpty();
    }

    @Test
    void parsePluginTxtFileTest() throws URISyntaxException {
        List<Plugin> noFilePluginList = pluginList.parsePluginTxtFile(null);
        assertThat(noFilePluginList).isEmpty();

        File pluginTxtFile = new File(this.getClass().getResource("PluginListParserTest/plugins.txt").toURI());

        List<Plugin> pluginsFromFile = pluginList.parsePluginTxtFile(pluginTxtFile);

        List<String> pluginInfo = new ArrayList<>();
        for (Plugin p : pluginsFromFile) {
            pluginInfo.add(p.toString());
        }

        assertThat(pluginInfo).containsExactlyInAnyOrder(expectedPluginInfo);
    }


    @Test
    void parsePluginYamlFileTest() throws URISyntaxException {
        List<Plugin> noFilePluginList = pluginList.parsePluginYamlFile(null);
        assertThat(noFilePluginList).isEmpty();

        File pluginYmlFile = new File(this.getClass().getResource("PluginListParserTest/plugins.yaml").toURI());

        List<Plugin> pluginsFromYamlFile = pluginList.parsePluginYamlFile(pluginYmlFile);

        List<String> pluginInfo = new ArrayList<>();
        for (Plugin p : pluginsFromYamlFile) {
            System.out.println(p.toString());
            pluginInfo.add(p.toString());
        }

        assertThat(pluginInfo).containsExactly(expectedPluginInfo);
    }

    @Test
    void badFormatYamlNoArtifactIdTest() throws URISyntaxException {
        File pluginYmlFile = new File(this.getClass().getResource("PluginListParserTest/badformat1.yaml").toURI());
        assertThatThrownBy(() -> pluginList.parsePluginYamlFile(pluginYmlFile))
                .isInstanceOf(PluginInputException.class);
    }

    @Test
    void badFormatYamlGroupIdNoVersion() throws URISyntaxException {
        File pluginYmlFile = new File(this.getClass().getResource("PluginListParserTest/badformat2.yaml").toURI());
        assertThatThrownBy(() -> pluginList.parsePluginYamlFile(pluginYmlFile))
                .isInstanceOf(PluginInputException.class);
    }

    @Test
    void badFormatYamlGroupIdNoVersion2() throws URISyntaxException {
        File pluginYmlFile = new File(this.getClass().getResource("PluginListParserTest/badformat3.yaml").toURI());
        assertThatThrownBy(() -> pluginList.parsePluginYamlFile(pluginYmlFile))
                .isInstanceOf(PluginInputException.class);
    }

    @Test
    void fileExistsTest() throws URISyntaxException {
        assertThat(pluginList.fileExists(null)).isFalse();

        File pluginFile = new File(this.getClass().getResource("PluginListParserTest/plugins.yaml").toURI());
        assertThat(pluginList.fileExists(pluginFile)).isTrue();

        File notExistingFile = new File("/file/that/does/not/exist.yaml");
        assertThat(pluginList.fileExists(notExistingFile)).isFalse();
    }
}
