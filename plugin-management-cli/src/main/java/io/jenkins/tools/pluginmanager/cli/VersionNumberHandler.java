package io.jenkins.tools.pluginmanager.cli;

import hudson.util.VersionNumber;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OneArgumentOptionHandler;
import org.kohsuke.args4j.spi.Setter;

// TODO(oleg_nenashev): Move to the VersionNumber lib?

public class VersionNumberHandler extends OneArgumentOptionHandler<VersionNumber> {

    public VersionNumberHandler(CmdLineParser parser, OptionDef option, Setter<VersionNumber> setter) {
        super(parser, option, setter);
    }

    @Override
    protected VersionNumber parse(String argument) throws NumberFormatException, CmdLineException {
        try {
            return new VersionNumber(argument);
        } catch (Exception ex) {
            throw new CmdLineException(owner, "Failed to parse the version number", ex);
        }
    }

    @Override
    public String getDefaultMetaVariable() {
        return null;
    }
}
