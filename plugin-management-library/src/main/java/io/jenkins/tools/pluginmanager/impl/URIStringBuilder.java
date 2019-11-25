package io.jenkins.tools.pluginmanager.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.removeEnd;
import static org.apache.commons.lang3.StringUtils.strip;
import static org.apache.commons.lang3.StringUtils.stripEnd;

public class URIStringBuilder {
    private String baseUrl;
    private List<String> pathSections = new ArrayList<String>();

    public URIStringBuilder(String uriString) {
        if (uriString == null) {
            uriString = "";
        }
        if (uriString.endsWith("://")) {
            baseUrl = removeEnd(uriString, "/"); // added back when joined
        } else {
            this.baseUrl = stripEnd(uriString, "/");
        }
    }

    public URIStringBuilder addPath(String... pathSectionArray) {
        if (pathSectionArray != null) {
            for (String pathSection : pathSectionArray) {
                this.pathSections.add(strip(pathSection, "/"));
            }
        }
        return this;
    }

    public String build() {
        return Stream.concat(Stream.of(baseUrl), pathSections.stream())
                .filter(item -> item != null && !item.isEmpty())
                .collect(Collectors.joining("/"));
    }
}
