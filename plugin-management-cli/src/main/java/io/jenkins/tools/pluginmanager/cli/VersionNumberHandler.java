package io.jenkins.tools.pluginmanager.cli;

import hudson.util.VersionNumber;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

// TODO(oleg_nenashev): Move to the VersionNumber lib?

public class VersionNumberHandler extends OptionHandler<VersionNumber> {

    private final CmdLineParser parser;
    private final OptionDef option;

    public VersionNumberHandler(CmdLineParser parser, OptionDef option, Setter<VersionNumber> setter) {
        super(parser, option, setter);
        this.parser = parser;
        this.option = option;
    }

    @Override
    public int parseArguments(Parameters params) throws CmdLineException {
        if (option.isArgument()) {
            String valueStr = params.getParameter(0);
            final VersionNumber version;
            try {
                version = new VersionNumber(valueStr);
            } catch (Exception ex) {
                throw new CmdLineException("Failed to parse the version number", ex);
            }
            super.setter.addValue(version);
            return 1;
        } else {
            // TODO: Localizable APi is quite bad
            throw new CmdLineException(parser, "Version must be specified");
        }
    }

    @Override
    public String getDefaultMetaVariable() {
        return null;
    }
}
