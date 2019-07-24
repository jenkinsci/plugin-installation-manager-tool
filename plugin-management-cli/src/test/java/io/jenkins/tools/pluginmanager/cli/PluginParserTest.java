package io.jenkins.tools.pluginmanager.cli;

import io.jenkins.tools.pluginmanager.impl.Plugin;
import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import src.main.java.io.jenkins.tools.pluginmanager.cli.PluginInputFormatException;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class)
@PrepareForTest({PluginParser.class})
public class PluginParserTest {
    PluginParser pluginParser;
    List<String> expectedPluginInfo;

    @Before
    public void setup() {
        pluginParser = new PluginParser();

        expectedPluginInfo = new ArrayList<>();
        expectedPluginInfo.add(new Plugin("git", "latest", null, null).toString());
        expectedPluginInfo.add(new Plugin("job-import-plugin", "2.1", null, null).toString());
        expectedPluginInfo.add(new Plugin("docker", "latest", null, null).toString());
        expectedPluginInfo.add(new Plugin("cloudbees-bitbucket-branch-source", "2.4.4", null, null).toString());
        expectedPluginInfo.add(new Plugin("script-security", "latest",
                "http://ftp-chi.osuosl.org/pub/jenkins/plugins/script-security/1.56/script-security.hpi", null)
                .toString());
        expectedPluginInfo.add(new Plugin("workflow-step-api",
                "2.19-rc289.d09828a05a74", null, "org.jenkins-ci.plugins.workflow").toString());
        expectedPluginInfo.add(new Plugin("matrix-project", "latest", null, null).toString());
        expectedPluginInfo.add(new Plugin("junit", "experimental", null, null).toString());
        expectedPluginInfo.add(new Plugin("credentials", "2.2.0",
                "http://ftp-chi.osuosl.org/pub/jenkins/plugins/credentials/2.2.0/credentials.hpi", null).toString());
        expectedPluginInfo.add(new Plugin("blueocean", "latest", null, null).toString());
        expectedPluginInfo.add(new Plugin("google-api-client-plugin", "latest",
                "https://updates.jenkins.io/latest/google-api-client-plugin.hpi", null).toString());

        Collections.sort(expectedPluginInfo);

    }

    @Test
    public void parsePluginsFromCliOptionTest() {
        String[] pluginInput = new String[]{"git", "job-import-plugin:2.1",
                "docker:latest",
                "cloudbees-bitbucket-branch-source:2.4.4",
                "script-security::http://ftp-chi.osuosl.org/pub/jenkins/plugins/script-security/1.56/" +
                        "script-security.hpi",
                "workflow-step-api:incrementals;org.jenkins-ci.plugins.workflow;2.19-rc289.d09828a05a74",
                "matrix-project:latest",
                "junit:experimental",
                "credentials:2.2.0:http://ftp-chi.osuosl.org/pub/jenkins/plugins/credentials/2.2.0/credentials.hpi",
                "google-api-client-plugin::https://updates.jenkins.io/latest/google-api-client-plugin.hpi",
                "blueocean:"};

        List<Plugin> plugins = pluginParser.parsePluginsFromCliOption(pluginInput);

        List<String> pluginInfo = new ArrayList<>();
        for (Plugin p : plugins) {
            pluginInfo.add(p.toString());
        }

        Collections.sort(pluginInfo);

        assertEquals(expectedPluginInfo, pluginInfo);

        List<Plugin> noPluginArrayList = pluginParser.parsePluginsFromCliOption(null);

        assertEquals(noPluginArrayList.size(), 0);

    }

    @Test
    public void parsePluginTxtFileTest() throws URISyntaxException {
        List<Plugin> noFilePluginList = pluginParser.parsePluginTxtFile(null);
        assertEquals(noFilePluginList.size(), 0);

        File pluginTxtFile = new File(this.getClass().getResource("/plugins.txt").toURI());

        List<Plugin> pluginsFromFile = pluginParser.parsePluginTxtFile(pluginTxtFile);

        List<String> pluginInfo = new ArrayList<>();
        for (Plugin p : pluginsFromFile) {
            pluginInfo.add(p.toString());
        }

        Collections.sort(pluginInfo);
        assertEquals(expectedPluginInfo, pluginInfo);
    }


    @Test
    public void parsePluginYamlFileTest() throws URISyntaxException {
        List<Plugin> noFilePluginList = pluginParser.parsePluginYamlFile(null);
        assertEquals(noFilePluginList.size(), 0);

        File pluginYmlFile = new File(this.getClass().getResource("/plugins.yaml").toURI());

        List<Plugin> pluginsFromYamlFile = pluginParser.parsePluginYamlFile(pluginYmlFile);

        List<String> pluginInfo = new ArrayList<>();
        for (Plugin p : pluginsFromYamlFile) {
            System.out.println(p.toString());
            pluginInfo.add(p.toString());
        }

        Collections.sort(pluginInfo);
        assertEquals(expectedPluginInfo, pluginInfo);
    }

    @Test(expected = PluginInputFormatException.class)
    public void badFormatYamlNoArtifactIdTest() throws URISyntaxException {
        File pluginYmlFile = new File(this.getClass().getResource("/badformat1.yaml").toURI());
        List<Plugin> pluginsFromYamlFile = pluginParser.parsePluginYamlFile(pluginYmlFile);
    }

    @Test(expected = PluginInputFormatException.class)
    public void badFormatYamlGroupIdNoVersion() throws URISyntaxException {
        File pluginYmlFile = new File(this.getClass().getResource("/badformat2.yaml").toURI());
        List<Plugin> pluginsFromYamlFile = pluginParser.parsePluginYamlFile(pluginYmlFile);
    }

    @Test(expected = PluginInputFormatException.class)
    public void badFormatYamlGroupIdNoVersion2() throws URISyntaxException {
        File pluginYmlFile = new File(this.getClass().getResource("/badformat3.yaml").toURI());
        List<Plugin> pluginsFromYamlFile = pluginParser.parsePluginYamlFile(pluginYmlFile);
    }

    @Test
    public void fileExistsTest() throws URISyntaxException {
        assertEquals(false, pluginParser.fileExists(null));

        File pluginFile = new File(this.getClass().getResource("/plugins.yaml").toURI());
        assertEquals(true, pluginParser.fileExists(pluginFile));

        mockStatic(Files.class);

        when(Files.exists(any(Path.class))).thenReturn(false);
        assertEquals(false, pluginParser.fileExists(pluginFile));
    }
}
