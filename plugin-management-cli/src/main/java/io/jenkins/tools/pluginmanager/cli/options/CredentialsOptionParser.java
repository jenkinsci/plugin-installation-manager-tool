package io.jenkins.tools.pluginmanager.cli.options;

import io.jenkins.tools.pluginmanager.cli.Messages;
import io.jenkins.tools.pluginmanager.config.Credentials;

import java.io.IOException;

// TODO()oleg_nenashev: Migrated from 2.x but unused even there

public final class CredentialsOptionParser {

    protected static Credentials parse(String argument) throws IOException {
        final Credentials option;
        // extract the 4 pieces: username, password, host, port
        final String[] parts = argument.split(":", 4);
        if (parts.length < 3) {
            throw new IOException(Messages.INVALID_CREDENTIALS_VALUE.format(argument));
        }
        if (parts.length == 3) {
            option = new Credentials(parts[1], parts[2], parts[0]);
        } else {
            int port = Integer.parseInt(parts[1]);
            option = new Credentials(parts[2], parts[3], parts[0], port);
        }
        return option;
    }

    public static String getDefaultMetaVariable() {
        return "CREDENTIALS";
    }
}
