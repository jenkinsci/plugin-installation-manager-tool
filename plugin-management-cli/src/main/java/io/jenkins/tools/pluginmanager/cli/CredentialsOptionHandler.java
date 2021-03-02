package io.jenkins.tools.pluginmanager.cli;

import io.jenkins.tools.pluginmanager.config.Credentials;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OneArgumentOptionHandler;
import org.kohsuke.args4j.spi.Setter;

public class CredentialsOptionHandler extends OneArgumentOptionHandler<Credentials> {

    public CredentialsOptionHandler(CmdLineParser parser, OptionDef option, Setter<? super Credentials> setter) {
        super(parser, option, setter);
    }

    @Override
    protected Credentials parse(String argument) throws CmdLineException {
        final Credentials option;
        // extract the 4 pieces: username, password, host, port
        final String[] parts = argument.split(":", 4);
        if (parts.length < 3) {
            throw new CmdLineException(owner, Messages.INVALID_CREDENTIALS_VALUE, argument);
        }
        if (parts.length == 3) {
            option = new Credentials(parts[1], parts[2], parts[0]);
        } else {
            int port = Integer.parseInt(parts[1]);
            option = new Credentials(parts[2], parts[3], parts[0], port);
        }
        return option;
    }

    @Override
    public String getDefaultMetaVariable() {
        return "CREDENTIALS";
    }
}
