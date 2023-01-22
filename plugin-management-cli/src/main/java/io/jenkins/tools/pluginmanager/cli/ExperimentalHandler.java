package io.jenkins.tools.pluginmanager.cli;

import hudson.util.VersionNumber;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OneArgumentOptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

public class ExperimentalHandler extends OneArgumentOptionHandler<VersionNumber> {
    public ExperimentalHandler(CmdLineParser parser, OptionDef option, Setter<? super VersionNumber> setter) {
        super(parser, option, setter);
    }

    @Override
    public String getDefaultMetaVariable() {
        return "Made by Mukund";
    }

    @Override
    public int parseArguments(Parameters params) throws CmdLineException {
        return super.parseArguments(params);
    }

    @Override
    protected VersionNumber parse(String s) throws NumberFormatException, CmdLineException {
        return null;
    }
}
