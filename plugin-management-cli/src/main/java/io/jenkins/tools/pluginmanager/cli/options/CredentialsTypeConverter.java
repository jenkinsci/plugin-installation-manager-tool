package io.jenkins.tools.pluginmanager.cli.options;

import io.jenkins.tools.pluginmanager.cli.Messages;
import io.jenkins.tools.pluginmanager.config.Credentials;
import picocli.CommandLine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class CredentialsTypeConverter implements CommandLine.ITypeConverter<List<Credentials>> {

    @Override
    public List<Credentials> convert(String input) throws Exception {
        String[] credentials = input.split(",");
        List<Credentials> res = new ArrayList<>(credentials.length);

        for (String value : credentials) {
            final Credentials option;
            // extract the 4 pieces: username, password, host, port
            final String[] parts = value.split(":", 4);
            if (parts.length < 3) {
                throw new IOException(Messages.INVALID_CREDENTIALS_VALUE.format(value));
            }
            if (parts.length == 3) {
                option = new Credentials(parts[1], parts[2], parts[0]);
            } else {
                int port = Integer.parseInt(parts[1]);
                option = new Credentials(parts[2], parts[3], parts[0], port);
            }
            res.add(option);
        }
        return res;
    }

    public static String getDefaultMetaVariable() {
        return "CREDENTIALS";
    }
}
