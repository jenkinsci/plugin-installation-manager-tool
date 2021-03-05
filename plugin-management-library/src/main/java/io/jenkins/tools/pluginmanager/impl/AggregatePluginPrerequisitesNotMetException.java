package io.jenkins.tools.pluginmanager.impl;

import java.util.Collection;
import java.util.stream.Collectors;

public class AggregatePluginPrerequisitesNotMetException extends RuntimeException {

    public AggregatePluginPrerequisitesNotMetException(Collection<Exception> exceptions) {
        super((exceptions.size() > 1 ? "Multiple plugin prerequisites " : "Plugin prerequisite ") + "not met:\n" + exceptions.stream().map(Exception::getMessage).collect(Collectors.joining(",\n")));
        for (Exception e : exceptions) {
            this.addSuppressed(e);
        }
    }
}
