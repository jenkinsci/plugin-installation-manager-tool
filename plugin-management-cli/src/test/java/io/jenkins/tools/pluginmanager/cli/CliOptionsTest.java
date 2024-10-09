package io.jenkins.tools.pluginmanager.cli;

import hudson.util.VersionNumber;
import io.jenkins.tools.pluginmanager.config.Config;
import io.jenkins.tools.pluginmanager.config.Credentials;
import io.jenkins.tools.pluginmanager.config.OutputFormat;
import io.jenkins.tools.pluginmanager.config.PluginInputException;
import io.jenkins.tools.pluginmanager.config.Settings;
import io.jenkins.tools.pluginmanager.impl.Plugin;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErrNormalized;
import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOutNormalized;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.apache.commons.io.IOUtils.toInputStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;


public class CliOptionsTest {
    private CliOptions options;
    private CmdLineParser parser;
    List<Plugin> txtRequestedPlugins;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void createParser() {
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
            new Plugin("credentials", "latest",
                    "http://ftp-chi.osuosl.org/pub/jenkins/plugins/credentials/2.2.0/credentials.hpi", null),
            new Plugin("blueocean", "latest", null, null)
        );
    }

    @Test
    public void setupDefaultsTest() throws Exception {
        parser.parseArgument();

        Config cfg = options.setup();

        assertThat(cfg.getPluginDir()).hasToString(Settings.DEFAULT_PLUGIN_DIR_LOCATION);
        assertThat(cfg.getJenkinsWar()).isEqualTo(Settings.DEFAULT_WAR);
        assertThat(cfg.isShowAllWarnings()).isFalse();
        assertThat(cfg.isShowWarnings()).isFalse();
        assertThat(cfg.isHideWarnings()).isFalse();
        assertThat(cfg.getJenkinsUc()).hasToString(Settings.DEFAULT_UPDATE_CENTER_LOCATION);
        assertThat(cfg.getJenkinsUcExperimental()).hasToString(Settings.DEFAULT_EXPERIMENTAL_UPDATE_CENTER_LOCATION);
        assertThat(cfg.getJenkinsIncrementalsRepoMirror()).hasToString(Settings.DEFAULT_INCREMENTALS_REPO_MIRROR_LOCATION);
        assertThat(cfg.getJenkinsPluginInfo()).hasToString(Settings.DEFAULT_PLUGIN_INFO_LOCATION);
        assertThat(cfg.getHashFunction()).isEqualTo(Settings.DEFAULT_HASH_FUNCTION);
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

        assertThat(cfg.getPluginDir()).isEqualTo(pluginDir);
        assertThat(cfg.getJenkinsWar()).isEqualTo(jenkinsWar.toString());
        assertThat(cfg.getPlugins()).containsExactly(displayUrlPlugin);
    }

    @Test
    public void setupPluginsTest() throws Exception {
        File pluginTxtFile = new File(this.getClass().getResource("/plugins.txt").toURI());

        File pluginFile = temporaryFolder.newFile("plugins.txt");
        FileUtils.copyFile(pluginTxtFile, pluginFile);

        parser.parseArgument("--plugin-file", pluginFile.toString(),
                "--plugins", "ssh-slaves:1.10 mailer cobertura:experimental");

        List<Plugin> requestedPlugins = new ArrayList<>(txtRequestedPlugins);
        requestedPlugins.add(new Plugin("ssh-slaves", "1.10", null, null));
        requestedPlugins.add(new Plugin("mailer", "latest", null, null));
        requestedPlugins.add(new Plugin("cobertura", "experimental", null, null));

        String stdOut = tapSystemOutNormalized(() -> {
            Config cfg = options.setup();

            assertConfigHasPlugins(cfg, requestedPlugins);
        });

        assertThat(stdOut).isEmpty();
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

        assertConfigHasPlugins(cfg, requestedPlugins);
    }

    @Test
    public void setupPluginsBadExtension() throws CmdLineException, IOException, URISyntaxException {
        File pluginTxtFile = new File(this.getClass().getResource("/plugins.t").toURI());

        File pluginFile = temporaryFolder.newFile("plugins.t");
        FileUtils.copyFile(pluginTxtFile, pluginFile);

        parser.parseArgument("--plugin-file", pluginFile.toString());

        assertThatThrownBy(options::setup)
                .isInstanceOf(PluginInputException.class);
    }

    @Test
    public void setupWarTest() throws CmdLineException {
        String jenkinsWar = this.getClass().getResource("/jenkinstest.war").toString();

        parser.parseArgument("--war", jenkinsWar);

        Config cfg = options.setup();
        assertThat(cfg.getJenkinsWar()).isEqualTo(jenkinsWar);
    }

    @Test
    public void setupJenkinsVersion() throws CmdLineException {
        parser.parseArgument("--jenkins-version", "2.263.1");
        Config cfg = options.setup();
        assertThat(cfg.getJenkinsVersion()).isEqualTo(new VersionNumber("2.263.1"));
    }

    @Test
    public void setupPluginDirTest() throws CmdLineException, IOException {
        String pluginDir = temporaryFolder.newFolder("plugins").toString();

        parser.parseArgument("--plugin-download-directory", pluginDir);

        Config cfg = options.setup();
        assertThat(cfg.getPluginDir()).hasToString(pluginDir);
    }

    @Test
    public void setupUpdateCenterCliTest() throws Exception {
        String ucEnvVar = "https://updates.jenkins.io/env";
        String experimentalUcEnvVar = "https://updates.jenkins.io/experimental/env";
        String incrementalsEnvVar = "https://repo.jenkins-ci.org/incrementals/env";
        String pluginInfoEnvVar = "https://updates.jenkins.io/current/plugin-versions/env";

        String ucCli = "https://updates.jenkins.io/cli";
        String experiementalCli = "https://updates.jenkins.io/experimental/cli";
        String incrementalsCli = "https://repo.jenkins-ci.org/incrementals/cli";
        String pluginInfoCli = "https://updates.jenkins.io/current/plugin-versions/cli";

        parser.parseArgument("--jenkins-update-center", ucCli,
                "--jenkins-experimental-update-center", experiementalCli,
                "--jenkins-incrementals-repo-mirror", incrementalsCli,
                "--jenkins-plugin-info", pluginInfoCli);

        Config cfg = options.setup();

        assertThat(cfg.getJenkinsUc()).hasToString(ucCli + "/update-center.json");
        assertThat(cfg.getJenkinsUcExperimental()).hasToString(experiementalCli + "/update-center.json");
        assertThat(cfg.getJenkinsIncrementalsRepoMirror()).hasToString(incrementalsCli);
        assertThat(cfg.getJenkinsPluginInfo()).hasToString(pluginInfoCli);
    }

    @Test
    public void setupSecurityWarningsTest() throws CmdLineException {
        parser.parseArgument("--view-all-security-warnings", "--view-security-warnings");
        Config cfg = options.setup();
        assertThat(cfg.isShowAllWarnings()).isTrue();
        assertThat(cfg.isShowWarnings()).isTrue();
    }

    @Test
    public void setupHideSecurityWarningsTest() throws CmdLineException {
        parser.parseArgument("--hide-security-warnings");
        Config cfg = options.setup();
        assertThat(cfg.isHideWarnings()).isTrue();
    }

    @Test
    public void showVersionTest() throws Exception {
        CliOptions optionsWithVersion = new CliOptions() {
            @Override
            public InputStream getPropertiesInputStream(String path) {
                return toInputStream("project.version = testVersion\n", UTF_8);
            }
        };
        CmdLineParser parserWithVersion = new CmdLineParser(optionsWithVersion);
        parserWithVersion.parseArgument("--version");

        String output = tapSystemOutNormalized(optionsWithVersion::showVersion);
        assertThat(output).isEqualTo("testVersion\n");

        parserWithVersion.parseArgument("-v");
        String aliasOutput = tapSystemOutNormalized(optionsWithVersion::showVersion);
        assertThat(aliasOutput).isEqualTo("testVersion\n");
    }

    @Test
    public void showVersionErrorTest() throws CmdLineException {
        CliOptions cliOptionsSpy = spy(options);
        parser.parseArgument("--version");
        doReturn(null).when(cliOptionsSpy).getPropertiesInputStream(any(String.class));

        assertThatThrownBy(cliOptionsSpy::showVersion)
                .isInstanceOf(VersionNotFoundException.class);
    }

    @Test
    public void noDownloadTest() throws CmdLineException {
        parser.parseArgument("--no-download");
        Config cfg = options.setup();
        assertThat(cfg.doDownload()).isFalse();
    }

    @Test
    public void downloadTest() throws CmdLineException {
        parser.parseArgument();
        Config cfg = options.setup();
        assertThat(cfg.doDownload()).isTrue();
    }

    @Test
    public void useLatestSpecifiedTest() throws CmdLineException {
        parser.parseArgument("--latest", "false", "--latest-specified");
        Config cfg = options.setup();
        assertThat(cfg.isUseLatestSpecified()).isTrue();
    }

    @Test
    public void useNotLatestSpecifiedTest() throws CmdLineException {
        parser.parseArgument();
        Config cfg = options.setup();
        assertThat(cfg.isUseLatestSpecified()).isFalse();
    }

    @Test
    public void useLatestTest() throws CmdLineException {
        parser.parseArgument("--latest");
        Config cfg = options.setup();
        assertThat(cfg.isUseLatestAll()).isTrue();
    }

    @Test
    public void useNotLatestTest() throws CmdLineException {
        parser.parseArgument("--latest", "false");
        Config cfg = options.setup();
        assertThat(cfg.isUseLatestAll()).isFalse();
    }

    @Test
    public void useLatestSpecifiedAndLatestAllTest() throws CmdLineException {
        parser.parseArgument("--latest", "--latest-specified");

        Config cfg = options.setup();
        assertThat(cfg.isUseLatestSpecified())
            .isTrue();

        assertThat(cfg.isUseLatestAll())
                .isFalse();
    }

    @Test
    public void credentialsTest() throws CmdLineException {
        parser.parseArgument("--credentials", "myhost:myuser:mypass,myhost2:1234:myuser2:mypass2");

        Config cfg = options.setup();
        assertThat(cfg.getCredentials()).containsExactly(
                new Credentials("myuser", "mypass", "myhost"),
                new Credentials("myuser2", "mypass2", "myhost2", 1234));
    }

    @Test
    public void invalidCredentialsTest() {
        assertThatThrownBy(() -> parser.parseArgument("--credentials", "myhost:myuser,myhost2:1234:myuser2:mypass2"))
        .isInstanceOf(CmdLineException.class).hasMessageContaining("Require at least a value containing 2 colons but found \"myhost:myuser\". The value must adhere to the grammar \"<host>[:port]:<username>:<password>\"");
    }

    @Test
    public void outputFormatDefaultTest() throws CmdLineException {
        parser.parseArgument("--plugins", "foo");

        assertThat(options.getOutputFormat()).isEqualTo(OutputFormat.STDOUT);
        Config cfg = options.setup();
        assertThat(cfg.getOutputFormat()).isEqualTo(OutputFormat.STDOUT);
    }

    @Test
    public void outputFormatTest() throws CmdLineException {
        parser.parseArgument("--plugins", "foo", "--output", "yaml");

        assertThat(options.getOutputFormat()).isEqualTo(OutputFormat.YAML);
        Config cfg = options.setup();
        assertThat(cfg.getOutputFormat()).isEqualTo(OutputFormat.YAML);
    }

    @Test
    public void verboseTest() throws Exception {
        parser.parseArgument("--plugins", "foo", "--verbose");

        String stdOut = tapSystemOutNormalized(() -> {
            options.setup();
        });
        assertThat(stdOut).isEmpty();

        String stdErr = tapSystemErrNormalized(() -> {
            options.setup();
        });
        assertThat(stdErr).isNotEmpty();
    }

    @Test
    public void verboseDisabledTest() throws Exception {
        parser.parseArgument("--plugins", "foo");

        String stdOut = tapSystemOutNormalized(() -> {
            options.setup();
        });
        assertThat(stdOut).isEmpty();

        String stdErr = tapSystemErrNormalized(() -> {
            options.setup();
        });
        assertThat(stdErr).isEmpty();
    }

    private void assertConfigHasPlugins(Config cfg, List<Plugin> expectedPlugins) {
        Plugin[] expectedPluginsAsArray = expectedPlugins.toArray(new Plugin[0]);
        assertThat(cfg.getPlugins()).containsExactlyInAnyOrder(expectedPluginsAsArray);
    }
}
