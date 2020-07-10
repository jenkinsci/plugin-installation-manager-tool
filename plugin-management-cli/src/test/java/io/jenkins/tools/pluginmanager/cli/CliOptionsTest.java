package io.jenkins.tools.pluginmanager.cli;

import io.jenkins.tools.pluginmanager.config.Config;
import io.jenkins.tools.pluginmanager.config.PluginInputException;
import io.jenkins.tools.pluginmanager.config.Settings;
import io.jenkins.tools.pluginmanager.impl.Plugin;
import io.jenkins.tools.pluginmanager.impl.PluginDependencyStrategyException;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOutNormalized;
import static com.github.stefanbirkner.systemlambda.SystemLambda.withEnvironmentVariable;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.powermock.api.mockito.PowerMockito.whenNew;
import static org.mockito.Mockito.when;


@RunWith(PowerMockRunner.class)
@PrepareForTest({CliOptions.class})
public class CliOptionsTest {
    private CliOptions options;
    private CmdLineParser parser;
    List<Plugin> txtRequestedPlugins;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void createParser() throws CmdLineException {
        options = new CliOptions();
        parser = new CmdLineParser(options);

        //corresponds to plugins in plugin.txt
        txtRequestedPlugins = asList(
            new Plugin("google-api-client-plugin",
                    "latest", "https://updates.jenkins.io/latest/google-api-client-plugin.hpi", null),
            new Plugin("git", "latest", null, null),
            new Plugin("job-import-plugin", "2.1", null, null),
            new Plugin("docker", "latest", null, null),
            new Plugin("cloudbees-bitbucket-branch-source", "2.4.4", null, null),
            new Plugin("script-security", "latest",
                    "http://ftp-chi.osuosl.org/pub/jenkins/plugins/script-security/1.56/script-security.hpi", null),
            new Plugin("workflow-step-api",
                    "2.19-rc289.d09828a05a74", null, "org.jenkins-ci.plugins.workflow"),
            new Plugin("matrix-project", "latest", null, null),
            new Plugin("junit", "experimental", null, null),
            new Plugin("credentials", "2.2.0",
                    "http://ftp-chi.osuosl.org/pub/jenkins/plugins/credentials/2.2.0/credentials.hpi", null),
            new Plugin("blueocean", "latest", null, null)
        );
    }

    @Test
    public void setupDefaultsTest() throws Exception {
        parser.parseArgument();

        withEnvironmentVariable("JENKINS_UC", "")
            .and("JENKINS_UC_EXPERIMENTAL", "")
            .and("JENKINS_INCREMENTALS_REPO_MIRROR", "")
            .and("JENKINS_PLUGIN_INFO", "")
            .execute(() -> {
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
                assertEquals(Settings.DEFAULT_PLUGIN_INFO_LOCATION, cfg.getJenkinsPluginInfo().toString());
            });
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
    public void setupPluginsTest2() throws CmdLineException, IOException, URISyntaxException {
        File pluginTxtFile = new File(this.getClass().getResource("/plugins.yaml").toURI());

        File pluginFile = temporaryFolder.newFile("plugins.yaml");
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
    public void setupPluginsBadExtension() throws CmdLineException, IOException, URISyntaxException {
        File pluginTxtFile = new File(this.getClass().getResource("/plugins.t").toURI());

        File pluginFile = temporaryFolder.newFile("plugins.t");
        FileUtils.copyFile(pluginTxtFile, pluginFile);

        parser.parseArgument("--plugin-file", pluginFile.toString());

        assertThrows(PluginInputException.class, options::setup);
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
    public void setupUpdateCenterCliTest() throws Exception {
        String ucEnvVar = "https://updates.jenkins.io/env";
        String experimentalUcEnvVar = "https://updates.jenkins.io/experimental/env";
        String incrementalsEnvVar = "https://repo.jenkins-ci.org/incrementals/env";
        String pluginInfoEnvVar = "https://updates.jenkins.io/current/plugin-versions/env";

        withEnvironmentVariable("JENKINS_UC", ucEnvVar)
            .and("JENKINS_UC_EXPERIMENTAL", experimentalUcEnvVar)
            .and("JENKINS_INCREMENTALS_REPO_MIRROR", incrementalsEnvVar)
            .and("JENKINS_PLUGIN_INFO", pluginInfoEnvVar)
            .execute(() -> {
                String ucCli = "https://updates.jenkins.io/cli";
                String experiementalCli = "https://updates.jenkins.io/experimental/cli";
                String incrementalsCli = "https://repo.jenkins-ci.org/incrementals/cli";
                String pluginInfoCli = "https://updates.jenkins.io/current/plugin-versions/cli";

                parser.parseArgument("--jenkins-update-center", ucCli,
                        "--jenkins-experimental-update-center", experiementalCli,
                        "--jenkins-incrementals-repo-mirror", incrementalsCli,
                        "--jenkins-plugin-info", pluginInfoCli);

                Config cfg = options.setup();

                // Cli options should override environment variables
                assertEquals(ucCli, cfg.getJenkinsUc().toString());
                assertEquals(experiementalCli, cfg.getJenkinsUcExperimental().toString());
                assertEquals(incrementalsCli, cfg.getJenkinsIncrementalsRepoMirror().toString());
                assertEquals(pluginInfoCli, cfg.getJenkinsPluginInfo().toString());
            });
    }

    @Test
    public void setupUpdateCenterEnvVarTest() throws Exception {
        String ucEnvVar = "https://updates.jenkins.io/env";
        String experimentalUcEnvVar = "https://updates.jenkins.io/experimental/env";
        String incrementalsEnvVar = "https://repo.jenkins-ci.org/incrementals/env";
        String pluginInfoEnvVar = "https://updates.jenkins.io/current/plugin-versions/env";

        withEnvironmentVariable("JENKINS_UC", ucEnvVar)
            .and("JENKINS_UC_EXPERIMENTAL", experimentalUcEnvVar)
            .and("JENKINS_INCREMENTALS_REPO_MIRROR", incrementalsEnvVar)
            .and("JENKINS_PLUGIN_INFO", pluginInfoEnvVar)
            .execute(() -> {
                Config cfg = options.setup();
                assertEquals(ucEnvVar, cfg.getJenkinsUc().toString());
                assertEquals(experimentalUcEnvVar, cfg.getJenkinsUcExperimental().toString());
                assertEquals(incrementalsEnvVar, cfg.getJenkinsIncrementalsRepoMirror().toString());
                assertEquals(pluginInfoEnvVar, cfg.getJenkinsPluginInfo().toString());
            });
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

        String output = tapSystemOutNormalized(options::showVersion);
        assertEquals("testVersion\n", output);

        parser.parseArgument("-v");
        String aliasOutput = tapSystemOutNormalized(options::showVersion);
        assertEquals("testVersion\n", aliasOutput);
    }

    @Test
    public void showVersionErrorTest() throws CmdLineException {
        CliOptions cliOptionsSpy = spy(options);
        parser.parseArgument("--version");
        doReturn(null).when(cliOptionsSpy).getPropertiesInputStream(any(String.class));

        assertThrows(VersionNotFoundException.class, cliOptionsSpy::showVersion);
    }

    @Test
    public void noDownloadTest() throws CmdLineException {
        parser.parseArgument("--no-download");
        Config cfg = options.setup();
        assertEquals(false, cfg.doDownload());
    }

    @Test
    public void downloadTest() throws CmdLineException {
        parser.parseArgument();
        Config cfg = options.setup();
        assertEquals(true, cfg.doDownload());
    }

    @Test
    public void useLatestSpecifiedTest() throws CmdLineException {
        parser.parseArgument("--latest-specified");
        Config cfg = options.setup();
        assertEquals(true, cfg.isUseLatestSpecified());
    }

    @Test
    public void useNotLatestSpecifiedTest() throws CmdLineException {
        parser.parseArgument();
        Config cfg = options.setup();
        assertEquals(false, cfg.isUseLatestSpecified());
    }

    @Test
    public void useLatestTest() throws CmdLineException {
        parser.parseArgument("--latest");
        Config cfg = options.setup();
        assertEquals(true, cfg.isUseLatestAll());
    }

    @Test
    public void useNotLatestTest() throws CmdLineException {
        parser.parseArgument();
        Config cfg = options.setup();
        assertEquals(false, cfg.isUseLatestAll());
    }

    @Test
    public void useLatestSpecifiedAndLatestAllTest() throws CmdLineException {
        parser.parseArgument("--latest", "--latest-specified");

        assertThrows(PluginDependencyStrategyException.class, options::setup);
    }
}
