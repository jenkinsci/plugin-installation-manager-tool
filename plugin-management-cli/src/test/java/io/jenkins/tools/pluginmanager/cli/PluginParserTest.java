package io.jenkins.tools.pluginmanager.cli;

import io.jenkins.tools.pluginmanager.impl.Plugin;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class PluginParserTest {
    PluginParser pluginParser;
    List<Plugin> expectedPlugins;

    @Before
    public void setup() {
         pluginParser = new PluginParser();

        expectedPlugins = new ArrayList<>();
        expectedPlugins.add(new Plugin("git", "latest", null));
        expectedPlugins.add(new Plugin("job-import-plugin", "2.1", null));
        expectedPlugins.add(new Plugin("docker", "latest", null));
        expectedPlugins.add(new Plugin("cloudbees-bitbucket-branch-source", "2.4.4", null));
        expectedPlugins.add(new Plugin("script-security", "latest",
                "http://ftp-chi.osuosl.org/pub/jenkins/plugins/script-security/1.56/script-security.hpi"));
        expectedPlugins.add(new Plugin("workflow-step-api",
                "incrementals;org.jenkins-ci.plugins.workflow;2.19-rc289.d09828a05a74", null));
        expectedPlugins.add(new Plugin("matrix-project", "latest", null));
        expectedPlugins.add(new Plugin("junit", "experimental", null));
        expectedPlugins.add(new Plugin("credentials", "2.2.0",
                "http://ftp-chi.osuosl.org/pub/jenkins/plugins/credentials/2.2.0/credentials.hpi"));

    }

    @Test
    public void parsePluginsFromCliOptionTest() {
        String[] pluginInput = new String[]{"git", "job-import-plugin:2.1",
                "docker:latest",
                "script-security::http://ftp-chi.osuosl.org/pub/jenkins/plugins/script-security/1.56/" +
                        "script-security.hpi",
                "workflow-step-api:incrementals;org.jenkins-ci.plugins.workflow;2.19-rc289.d09828a05a74",
                "matrix-project:latest",
                "junit:experimental",
                "credentials:2.2.0:http://ftp-chi.osuosl.org/pub/jenkins/plugins/credentials/2.2.0/credentials.hpi"};

        List<Plugin> plugins = pluginParser.parsePluginsFromCliOption(pluginInput);

        List<String> expectedPluginInfo = new ArrayList<>();
        List<String> pluginInfo = new ArrayList<>();
        for (Plugin p : plugins) {
            pluginInfo.add(p.toString());
        }
        for (Plugin p : expectedPlugins) {
            expectedPluginInfo.add(p.toString());
        }

        Collections.sort(expectedPluginInfo);
        Collections.sort(pluginInfo);

        assertEquals(expectedPluginInfo, pluginInfo);

        List<Plugin> noPluginArrayList = pluginParser.parsePluginsFromCliOption(null);

        assertEquals(noPluginArrayList.size(), 0);

    }

    public void parsePluginTxtFileTest() throws URISyntaxException {
        List<Plugin>  noFilePluginList = pluginParser.parsePluginTxtFile(null);
        assertEquals(noFilePluginList.size(), 0);

        File pluginTxtFile = new File(this.getClass().getResource("/plugins.txt").toURI());

        List<Plugin> pluginsFromFile = pluginParser.parsePluginTxtFile(pluginTxtFile);




    }

}
