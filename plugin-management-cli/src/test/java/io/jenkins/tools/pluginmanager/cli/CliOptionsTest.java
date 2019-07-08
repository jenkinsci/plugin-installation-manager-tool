package io.jenkins.tools.pluginmanager.cli;

import io.jenkins.tools.pluginmanager.config.Config;
import io.jenkins.tools.pluginmanager.config.Settings;
import java.net.URL;
import org.junit.Assert;
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


    @Test
    public void setupTest() throws CmdLineException {
        CliOptions options = new CliOptions();
        CmdLineParser parser = new CmdLineParser(options);
        URL pluginTxtUrl = this.getClass().getResource("/plugins.txt");
        URL jenkinsWar = this.getClass().getResource("/jenkinstest.war");

        String testCliUC = "https://updates.jenkins.io.test";
        String testEnvVarUc = "https://updates.jenkins.io";
        String testEnvVarIncr = "https://repo.jenkins-ci.org/incrementals/test";


        parser.parseArgument("--plugin-file", pluginTxtUrl.toString(),
                "--war", jenkinsWar.toString(),
                "--plugins", "ssh-slaves:1.10 mailer cobertura:experimental",
                "--view-all-security-warnings",
                "--jenkins-update-center", testCliUC);

        PowerMockito.mockStatic(System.class);

        Mockito.when(System.getenv("JENKINS_UC")).thenReturn(testEnvVarUc);
        Mockito.when(System.getenv("JENKINS_UC_EXPERIMENTAL")).thenReturn("");
        Mockito.when(System.getenv("JENKINS_INCREMENTALS_REPO_MIRROR")).thenReturn(testEnvVarIncr);

        Config cfg = options.setup();

        Assert.assertEquals(Settings.DEFAULT_PLUGIN_DIR.toString(), cfg.getPluginDir().toString());
        Assert.assertEquals(jenkinsWar.toString(), cfg.getJenkinsWar().toString());
        Assert.assertEquals(true, cfg.isShowAllWarnings());
        Assert.assertEquals(false, cfg.isShowWarnings());
        // Cli option should override environment variable
        Assert.assertEquals(testCliUC, cfg.getJenkinsUc().toString());
        Assert.assertEquals(Settings.DEFAULT_EXPERIMENTAL_UPDATE_CENTER.toString(),
                cfg.getJenkinsUcExperimental().toString());
        Assert.assertEquals(testEnvVarIncr, cfg.getJenkinsIncrementalsRepoMirror().toString());

    }
}


