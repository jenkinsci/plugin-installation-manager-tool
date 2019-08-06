package io.jenkins.tools.pluginmanager.cli;

import io.jenkins.tools.pluginmanager.config.Config;
import io.jenkins.tools.pluginmanager.config.Settings;
import io.jenkins.tools.pluginmanager.impl.Plugin;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.whenNew;
import static org.mockito.Mockito.when;


@RunWith(PowerMockRunner.class)
@PrepareForTest({CliOptions.class, System.class})
public class CliOptionsTest {
    private CliOptions options;
    private CmdLineParser parser;
    List<Plugin> txtRequestedPlugins;
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void createParser() throws CmdLineException {
        options = new CliOptions();
        parser = new CmdLineParser(options);

        //corresponds to plugins in plugin.txt
        txtRequestedPlugins = new ArrayList<>();
        txtRequestedPlugins.add(new Plugin("google-api-client-plugin",
                "latest", "https://updates.jenkins.io/latest/google-api-client-plugin.hpi", null));
        txtRequestedPlugins.add(new Plugin("git", "latest", null, null));
        txtRequestedPlugins.add(new Plugin("job-import-plugin", "2.1", null, null));
        txtRequestedPlugins.add(new Plugin("docker", "latest", null, null));
        txtRequestedPlugins.add(new Plugin("cloudbees-bitbucket-branch-source", "2.4.4", null, null));
        txtRequestedPlugins.add(new Plugin("script-security", "latest",
                "http://ftp-chi.osuosl.org/pub/jenkins/plugins/script-security/1.56/script-security.hpi", null));
        txtRequestedPlugins.add(new Plugin("workflow-step-api",
                "2.19-rc289.d09828a05a74", null, "org.jenkins-ci.plugins.workflow"));
        txtRequestedPlugins.add(new Plugin("matrix-project", "latest", null, null));
        txtRequestedPlugins.add(new Plugin("junit", "experimental", null, null));
        txtRequestedPlugins.add(new Plugin("credentials", "2.2.0",
                "http://ftp-chi.osuosl.org/pub/jenkins/plugins/credentials/2.2.0/credentials.hpi", null));
        txtRequestedPlugins.add(new Plugin("blueocean", "latest", null, null));

        System.setOut(new PrintStream(outContent));
    }



    @Test
    public void setupDefaultsTest() throws CmdLineException {
        parser.parseArgument();

        mockStatic(System.class);

        when(System.getenv("JENKINS_UC")).thenReturn("");
        when(System.getenv("JENKINS_UC_EXPERIMENTAL")).thenReturn("");
        when(System.getenv("JENKINS_INCREMENTALS_REPO_MIRROR")).thenReturn("");

        Config cfg = options.setup();

        assertEquals(Settings.DEFAULT_PLUGIN_DIR_LOCATION, cfg.getPluginDir().toString());
        assertEquals(Settings.DEFAULT_WAR, cfg.getJenkinsWar());
        assertEquals(false, cfg.isShowAllWarnings());
        assertEquals(false, cfg.isShowWarnings());
        assertEquals(Settings.DEFAULT_UPDATE_CENTER_LOCATION, cfg.getJenkinsUc().toString());
        assertEquals(Settings.DEFAULT_EXPERIMENTAL_UPDATE_CENTER_LOCATION,
                cfg.getJenkinsUcExperimental().toString());
        assertEquals(Settings.DEFAULT_INCREMENTALS_REPO_MIRROR_LOCATION,
                cfg.getJenkinsIncrementalsRepoMirror().toString());
    }


    @Test
    public void setupAliasTest() throws CmdLineException, IOException, URISyntaxException {
        File pluginTxtFile = new File(this.getClass().getResource("/emptyplugins.txt").toURI());
        File jenkinsWar = new File(this.getClass().getResource("/jenkinstest.war").toURI());
        File pluginDir = temporaryFolder.newFolder("plugins");

        parser.parseArgument("-f", pluginTxtFile.toString(),
                "-d", pluginDir.toString(),
                "-w", jenkinsWar.toString(),
                "-p",
                "display-url-api::https://updates.jenkins.io/download/plugins/display-url-api/1.0/display-url-api.hpi");

        Plugin displayUrlPlugin = new Plugin("display-url-api", "latest",
                "https://updates.jenkins.io/download/plugins/display-url-api/1.0/display-url-api.hpi", null);

        Config cfg = options.setup();

        assertEquals(pluginDir, cfg.getPluginDir());
        assertEquals(jenkinsWar.toString(), cfg.getJenkinsWar());
        assertEquals(1, cfg.getPlugins().size());
        assertEquals(displayUrlPlugin.toString(), cfg.getPlugins().get(0).toString());
    }

    @Test
    public void setupPluginsTest() throws CmdLineException, IOException, URISyntaxException {
        File pluginTxtFile = new File(this.getClass().getResource("/plugins.txt").toURI());

        File pluginFile = temporaryFolder.newFile("plugins.txt");
        FileUtils.copyFile(pluginTxtFile, pluginFile);

        parser.parseArgument("--plugin-file", pluginFile.toString(),
                "--plugins", "ssh-slaves:1.10 mailer cobertura:experimental");

        List<Plugin> requestedPlugins = new ArrayList<>(txtRequestedPlugins);
        requestedPlugins.add(new Plugin("ssh-slaves", "1.10", null, null));
        requestedPlugins.add(new Plugin("mailer", "latest", null, null));
        requestedPlugins.add(new Plugin("cobertura", "experimental", null, null));

        Config cfg = options.setup();

        assertEquals(requestedPlugins.size(), cfg.getPlugins().size());

        List<String> cfgPluginInfo = new ArrayList<>();
        List<String> requestedPluginInfo = new ArrayList<>();

        for (Plugin p : cfg.getPlugins()) {
            cfgPluginInfo.add(p.toString());
        }
        for (Plugin p : requestedPlugins) {
            requestedPluginInfo.add(p.toString());
        }

        Collections.sort(cfgPluginInfo);
        Collections.sort(requestedPluginInfo);

        assertEquals(requestedPluginInfo, cfgPluginInfo);
    }


    @Test
    public void setupWarTest() throws CmdLineException {
        String jenkinsWar = this.getClass().getResource("/jenkinstest.war").toString();

        parser.parseArgument("--war", jenkinsWar);

        Config cfg = options.setup();
        assertEquals(jenkinsWar, cfg.getJenkinsWar());
    }


    @Test
    public void setupPluginDirTest() throws CmdLineException, IOException {
        String pluginDir = temporaryFolder.newFolder("plugins").toString();

        parser.parseArgument("--plugin-download-directory", pluginDir);

        Config cfg = options.setup();
        assertEquals(pluginDir, cfg.getPluginDir().toString());
    }

    @Test
    public void setupUpdateCenterCliTest() throws CmdLineException {
        String ucEnvVar = "https://updates.jenkins.io/env";
        String experimentalUcEnvVar = "https://updates.jenkins.io/experimental/env";
        String incrementalsEnvVar = "https://repo.jenkins-ci.org/incrementals/env";

        mockStatic(System.class);
        when(System.getenv("JENKINS_UC")).thenReturn(ucEnvVar);
        when(System.getenv("JENKINS_UC_EXPERIMENTAL")).thenReturn(experimentalUcEnvVar);
        when(System.getenv("JENKINS_INCREMENTALS_REPO_MIRROR")).thenReturn(incrementalsEnvVar);

        String ucCli = "https://updates.jenkins.io/cli";
        String experiementalCli = "https://updates.jenkins.io/experimental/cli";
        String incrementalsCli = "https://repo.jenkins-ci.org/incrementals/cli";

        parser.parseArgument("--jenkins-update-center", ucCli,
                "--jenkins-experimental-update-center", experiementalCli,
                "--jenkins-incrementals-repo-mirror", incrementalsCli);

        Config cfg = options.setup();

        // Cli options should override environment variables
        assertEquals(ucCli, cfg.getJenkinsUc().toString());
        assertEquals(experiementalCli, cfg.getJenkinsUcExperimental().toString());
        assertEquals(incrementalsCli, cfg.getJenkinsIncrementalsRepoMirror().toString());
    }

    @Test
    public void setupUpdateCenterEnvVarTest() throws CmdLineException {
        String ucEnvVar = "https://updates.jenkins.io/env";
        String experimentalUcEnvVar = "https://updates.jenkins.io/experimental/env";
        String incrementalsEnvVar = "https://repo.jenkins-ci.org/incrementals/env";

        mockStatic(System.class);
        when(System.getenv("JENKINS_UC")).thenReturn(ucEnvVar);
        when(System.getenv("JENKINS_UC_EXPERIMENTAL")).thenReturn(experimentalUcEnvVar);
        when(System.getenv("JENKINS_INCREMENTALS_REPO_MIRROR")).thenReturn(incrementalsEnvVar);

        Config cfg = options.setup();
        assertEquals(ucEnvVar, cfg.getJenkinsUc().toString());
        assertEquals(experimentalUcEnvVar, cfg.getJenkinsUcExperimental().toString());
        assertEquals(incrementalsEnvVar, cfg.getJenkinsIncrementalsRepoMirror().toString());
    }

    @Test
    public void setupSecurityWarningsTest() throws CmdLineException {
        parser.parseArgument("--view-all-security-warnings", "--view-security-warnings");
        Config cfg = options.setup();
        assertEquals(true, cfg.isShowAllWarnings());
        assertEquals(true, cfg.isShowWarnings());
    }


    @Test
    public void showVersionTest() throws Exception {
        parser.parseArgument("--version");

        String version = "testVersion";

        Properties properties = mock(Properties.class);
        whenNew(Properties.class).withNoArguments().thenReturn(properties);
        when(properties.getProperty(any(String.class))).thenReturn(version);

        options.showVersion();
        assertEquals(version, outContent.toString().trim());

        ByteArrayOutputStream aliasVersionOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(aliasVersionOut));

        parser.parseArgument("-v");
        options.showVersion();
        assertEquals(version, aliasVersionOut.toString().trim());
    }


    @Test(expected = VersionNotFoundException.class)
    public void showVersionErrorTest() throws CmdLineException {
        ByteArrayOutputStream nullPropertiesOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(nullPropertiesOut));
        CliOptions cliOptionsSpy = spy(options);
        parser.parseArgument("--version");
        doReturn(null).when(cliOptionsSpy).getPropertiesInputStream(any(String.class));
        cliOptionsSpy.showVersion();
    }

    @After
    public void restoreStream() {
        System.setOut(originalOut);
    }
}
