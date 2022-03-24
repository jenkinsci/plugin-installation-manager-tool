package io.jenkins.tools.pluginmanager.cli;

import hudson.util.VersionNumber;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OneArgumentOptionHandler;
import org.kohsuke.args4j.spi.Setter;



public class VersionNumberHandler extends OneArgumentOptionHandler<VersionNumber> {

    public static final String FAILED_TO_PARSE_THE_VERSION_NUMBER = "Failed to parse the version number";
    public VersionNumberHandler(CmdLineParser parser, OptionDef option, Setter<VersionNumber> setter) {
        super(parser, option, setter);
    }

    @Override
    protected VersionNumber parse(String argument) throws NumberFormatException, CmdLineException {
        return null;
    }


    @Override
    public String getDefaultMetaVariable() {
        return null;
    }
}
