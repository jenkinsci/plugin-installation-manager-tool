package io.jenkins.tools.pluginmanager.cli;

import io.jenkins.tools.pluginmanager.config.Credentials;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.DelimitedOptionHandler;
import org.kohsuke.args4j.spi.Setter;

public class MultiCredentialsOptionHandler extends DelimitedOptionHandler<Credentials> {

    public MultiCredentialsOptionHandler(CmdLineParser parser, OptionDef option, Setter<? super Credentials> setter) {
        super(parser, option, setter, ",", new CredentialsOptionHandler(parser, option, setter));
    }

}
