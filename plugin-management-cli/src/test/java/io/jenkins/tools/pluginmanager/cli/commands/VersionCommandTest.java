package io.jenkins.tools.pluginmanager.cli.commands;


/**
 * Tests for {@link VersionCommand}
 * @author Oleg Nenashev
 * @since TODO
 */
public class VersionCommandTest {

    //TODO: replace with equivalent
    /*
    @Test
    public void showVersionTest() throws Exception {
        BaseCliOptions optionsWithVersion = new BaseCliOptions() {
            @Override
            public InputStream getPropertiesInputStream(String path) {
                return toInputStream("project.version = testVersion\n", UTF_8);
            }
        };
        BaseBaseCliOptionsTest.CmdLineParser parserWithVersion = new BaseBaseCliOptionsTest.CmdLineParser(optionsWithVersion);
        parserWithVersion.parseArgument("--version");

        String output = tapSystemOutNormalized(optionsWithVersion::showVersion);
        assertThat(output).isEqualTo("testVersion\n");

        parserWithVersion.parseArgument("-v");
        String aliasOutput = tapSystemOutNormalized(optionsWithVersion::showVersion);
        assertThat(aliasOutput).isEqualTo("testVersion\n");
    }

    @Test
    public void showVersionErrorTest() throws CmdLineException {
        BaseCliOptions BaseCliOptionsSpy = spy(options);
        parser.parseArgument("--version");
        doReturn(null).when(BaseCliOptionsSpy).getPropertiesInputStream(any(String.class));

        assertThatThrownBy(BaseCliOptionsSpy::showVersion)
                .isInstanceOf(VersionNotFoundException.class);
    }*/
}
