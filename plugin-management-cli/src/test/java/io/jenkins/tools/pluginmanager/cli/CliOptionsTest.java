package io.jenkins.tools.pluginmanager.cli;

import io.jenkins.tools.pluginmanager.config.Config;
import io.jenkins.tools.pluginmanager.config.Settings;
import java.io.File;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.assertEquals;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.mockito.Mockito.when;




@RunWith(PowerMockRunner.class)
@PrepareForTest({CliOptions.class, System.class})
public class CliOptionsTest {
    private CliOptions options;
    private CmdLineParser parser;

    @Before
    public void createParser() throws CmdLineException {
        options = new CliOptions();
        parser = new CmdLineParser(options);
    }


    @Test
    public void setupDefaultsTest() throws CmdLineException {
        parser.parseArgument();

        mockStatic(System.class);

        when(System.getenv("JENKINS_UC")).thenReturn("");
        when(System.getenv("JENKINS_UC_EXPERIMENTAL")).thenReturn("");
        when(System.getenv("JENKINS_INCREMENTALS_REPO_MIRROR")).thenReturn("");

        Config cfg = options.setup();

        assertEquals(Settings.DEFAULT_PLUGIN_DIR.toString(), cfg.getPluginDir().toString());
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
    public void setupAliasTest() throws CmdLineException {
        String pluginTxtFile = this.getClass().getResource("/plugins.txt").toString();
        String jenkinsWar = this.getClass().getResource("/jenkinstest.war").toString();
        File file = new File(pluginTxtFile);
        String pluginDir = file.getParent() + "/plugins";

        parser.parseArgument("-f", pluginTxtFile,
                "-d", pluginDir,
                "-w", jenkinsWar,
                "-p", "ssh-slaves:1.10 mailer cobertura:experimental");

        Config cfg = options.setup();

        assertEquals(pluginDir, cfg.getPluginDir().toString());
        assertEquals(jenkinsWar, cfg.getJenkinsWar());
    }


    @Test
    public void setupPluginsTest() throws CmdLineException {
        String pluginTxtFile = this.getClass().getResource("/plugins.txt").toString();

        parser.parseArgument("--plugin-file", pluginTxtFile,
                "--plugins", "ssh-slaves:1.10 mailer cobertura:experimental");

        Config cfg = options.setup();

        //TODO check plugin input
    }


    @Test
    public void setupWarTest() throws CmdLineException {
        String jenkinsWar = this.getClass().getResource("/jenkinstest.war").toString();

        parser.parseArgument("--war", jenkinsWar);

        Config cfg = options.setup();
        assertEquals(jenkinsWar, cfg.getJenkinsWar());
    }

    @Test
    public void setupPluginDirTest() throws CmdLineException {
        File file = new File(this.getClass().getResource("/plugins.txt").toString());
        String pluginDir = file.getParent().toString() + "/plugins";

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
}


