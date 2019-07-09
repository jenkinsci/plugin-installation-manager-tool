package io.jenkins.tools.pluginmanager.cli;

import io.jenkins.tools.pluginmanager.config.Config;
import io.jenkins.tools.pluginmanager.config.Settings;
import java.io.File;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;


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

        PowerMockito.mockStatic(System.class);

        Mockito.when(System.getenv("JENKINS_UC")).thenReturn("");
        Mockito.when(System.getenv("JENKINS_UC_EXPERIMENTAL")).thenReturn("");
        Mockito.when(System.getenv("JENKINS_INCREMENTALS_REPO_MIRROR")).thenReturn("");

        Config cfg = options.setup();

        Assert.assertEquals(Settings.DEFAULT_PLUGIN_DIR.toString(), cfg.getPluginDir().toString());
        Assert.assertEquals(Settings.DEFAULT_WAR, cfg.getJenkinsWar());
        Assert.assertEquals(false, cfg.isShowAllWarnings());
        Assert.assertEquals(false, cfg.isShowWarnings());
        Assert.assertEquals(Settings.DEFAULT_UPDATE_CENTER_LOCATION, cfg.getJenkinsUc().toString());
        Assert.assertEquals(Settings.DEFAULT_EXPERIMENTAL_UPDATE_CENTER_LOCATION,
                cfg.getJenkinsUcExperimental().toString());
        Assert.assertEquals(Settings.DEFAULT_INCREMENTALS_REPO_MIRROR_LOCATION,
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

        Assert.assertEquals(pluginDir, cfg.getPluginDir().toString());
        Assert.assertEquals(jenkinsWar, cfg.getJenkinsWar());
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
        Assert.assertEquals(jenkinsWar, cfg.getJenkinsWar());
    }

    @Test
    public void setupPluginDirTest() throws CmdLineException {
        File file = new File(this.getClass().getResource("/plugins.txt").toString());
        String pluginDir = file.getParent().toString() + "/plugins";

        parser.parseArgument("--plugin-download-directory", pluginDir);

        Config cfg = options.setup();
        Assert.assertEquals(pluginDir, cfg.getPluginDir().toString());
    }

    @Test
    public void setupUpdateCenterCliTest() throws CmdLineException {
        String ucEnvVar = "https://updates.jenkins.io/env";
        String experimentalUcEnvVar = "https://updates.jenkins.io/experimental/env";
        String incrementalsEnvVar = "https://repo.jenkins-ci.org/incrementals/env";

        PowerMockito.mockStatic(System.class);
        Mockito.when(System.getenv("JENKINS_UC")).thenReturn(ucEnvVar);
        Mockito.when(System.getenv("JENKINS_UC_EXPERIMENTAL")).thenReturn(experimentalUcEnvVar);
        Mockito.when(System.getenv("JENKINS_INCREMENTALS_REPO_MIRROR")).thenReturn(incrementalsEnvVar);

        String ucCli = "https://updates.jenkins.io/cli";
        String experiementalCli = "https://updates.jenkins.io/experimental/cli";
        String incrementalsCli = "https://repo.jenkins-ci.org/incrementals/cli";

        parser.parseArgument("--jenkins-update-center", ucCli,
                "--jenkins-experimental-update-center", experiementalCli,
                "--jenkins-incrementals-repo-mirror", incrementalsCli);

        Config cfg = options.setup();

        // Cli options should override environment variables
        Assert.assertEquals(ucCli, cfg.getJenkinsUc().toString());
        Assert.assertEquals(experiementalCli, cfg.getJenkinsUcExperimental().toString());
        Assert.assertEquals(incrementalsCli, cfg.getJenkinsIncrementalsRepoMirror().toString());
    }

    @Test
    public void setupUpdateCenterEnvVarTest() throws CmdLineException {
        String ucEnvVar = "https://updates.jenkins.io/env";
        String experimentalUcEnvVar = "https://updates.jenkins.io/experimental/env";
        String incrementalsEnvVar = "https://repo.jenkins-ci.org/incrementals/env";

        PowerMockito.mockStatic(System.class);
        Mockito.when(System.getenv("JENKINS_UC")).thenReturn(ucEnvVar);
        Mockito.when(System.getenv("JENKINS_UC_EXPERIMENTAL")).thenReturn(experimentalUcEnvVar);
        Mockito.when(System.getenv("JENKINS_INCREMENTALS_REPO_MIRROR")).thenReturn(incrementalsEnvVar);

        Config cfg = options.setup();
        Assert.assertEquals(ucEnvVar, cfg.getJenkinsUc().toString());
        Assert.assertEquals(experimentalUcEnvVar, cfg.getJenkinsUcExperimental().toString());
        Assert.assertEquals(incrementalsEnvVar, cfg.getJenkinsIncrementalsRepoMirror().toString());
    }

    @Test
    public void setupSecurityWarningsTest() throws CmdLineException {
        parser.parseArgument("--view-all-security-warnings", "--view-security-warnings");
        Config cfg = options.setup();
        Assert.assertEquals(true, cfg.isShowAllWarnings());
        Assert.assertEquals(true, cfg.isShowWarnings());
    }
}


