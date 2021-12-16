package io.jenkins.tools.pluginmanager.util;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.removeEnd;
import static org.apache.commons.lang3.StringUtils.strip;
import static org.apache.commons.lang3.StringUtils.stripEnd;

public class URIStringBuilder {
    private String baseUrl;
    private List<String> pathSections = new ArrayList<>();

    /**
     * Create the builder with the first part of a URL. For example, http://example.com and https://example.com/path/parts
     * are acceptable.
     *
     * @param uriString the first part of the URL to be built
     */
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

    /**
     * Add the supplied path section strings to the end of the path for this URL. Note that a path section can contain
     * internal slashes of its own optionally. Although these are called pathSections, the final one can be a filename.
     *
     * @param pathSectionArray one or more strings to be added to the path of this URL
     * @return returns itself so additional modifications to the URL can be stacked
     */
    public URIStringBuilder addPath(String... pathSectionArray) {
        if (pathSectionArray != null) {
            for (String pathSection : pathSectionArray) {
                this.pathSections.add(strip(pathSection, "/"));
            }
        }
        return this;
    }

    /**
     * Create a string from the properties of this builder containing the base URL at the front and all the path sections
     * following. It is fixed so only a single slash separates each path section. Any empty path sections are ignored.
     *
     * @return the resulting URL with path
     */
    public String build() {
        return Stream.concat(Stream.of(baseUrl), pathSections.stream())
                .filter(item -> item != null && !item.isEmpty())
                .collect(Collectors.joining("/"));
    }
}
