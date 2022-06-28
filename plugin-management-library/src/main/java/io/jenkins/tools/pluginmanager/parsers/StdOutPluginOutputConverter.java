package io.jenkins.tools.pluginmanager.parsers;

import io.jenkins.tools.pluginmanager.impl.Plugin;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static java.util.Comparator.comparing;

public class StdOutPluginOutputConverter implements PluginOutputConverter {

    private final String heading;

    public StdOutPluginOutputConverter(String heading) {
        this.heading = heading;
    }

    @Override
    public String convert(List<Plugin> plugins) {
        StringWriter writer = new StringWriter();
        PrintWriter pw = new PrintWriter(writer);
        pw.println(heading);
        if (plugins.isEmpty()) {
            pw.println("-none-");
        } else {
            plugins.stream()
                    .sorted(comparing(Plugin::getName).thenComparing(Plugin::getVersion))
                    .forEach(pw::println);
        }

        return writer.toString();
    }
}
