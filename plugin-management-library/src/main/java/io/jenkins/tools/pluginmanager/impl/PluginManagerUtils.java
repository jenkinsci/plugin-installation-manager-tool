package io.jenkins.tools.pluginmanager.impl;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PluginManagerUtils {

    private PluginManagerUtils() {
    }

    /**
     * Given two strings, concatenate with a single slash at the point they are joined. Additional trailing and leading
     *  slashes at that point will be adjusted so only one remains. If the first one ends in '://', it is preserved.
     *
     * @param urlString the first part of a URL. (e.g. http://example.com) Trailing slashes are removed as appropriate.
     * @param pathSection the next part of a URL to be appended to the first part. (e.g. path/to/file.json) Leading
     *                    slashes are removed as appropriate.
     * @return the two parts concatenated with one slash at the point they are joined.
     */
    static String appendPathOntoUrl(String urlString, String pathSection) {
        if (urlString == null) {
            return pathSection;
        }
        if (pathSection== null) {
            return urlString;
        }
        int urlLength = urlString.length();
        while (urlString.charAt(urlLength - 1) == '/' &&
                (urlLength < 2 || urlString.charAt(urlLength - 2) != ':')
        ) {
            urlLength--;
        }
        int pathStartCol = 0;
        while (pathStartCol < pathSection.length() && pathSection.charAt(pathStartCol) == '/') {
            pathStartCol++;
        }
        return urlString.substring(0, urlLength) + "/" + pathSection.substring(pathStartCol);
    }

    /**
     * Given text loaded from a Jenkins update center, extract just the json content.<br/>
     * Jenkins update centers have meta-data in json form in a file "update-center.json" and, in the case of updates.jenkins.io,
     *  another file "update-center.actual.json". The former file has wrapper text surrounding the json content. The latter
     *  file has no wrapper and is totally json. This method checks for the wrapper and removes it if found.<br/>
     * Historically the wrapper is said to allow easier display of the json in browsers. The text to remove is
     *  "updateCenter.post( ...json here... );" with all json content inside.<br/>
     *
     * @param urlText text loaded from "update-center.json" or "update-center.actual.json"
     * @return the supplied text with any present wrapper removed
     */
    static String removePossibleWrapperText(String urlText) {
        if (urlText != null && urlText.startsWith("updateCenter.post(")) {
            Pattern pattern = Pattern.compile("updateCenter.post\\((.*)\\);", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(urlText);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return urlText;
    }

}

