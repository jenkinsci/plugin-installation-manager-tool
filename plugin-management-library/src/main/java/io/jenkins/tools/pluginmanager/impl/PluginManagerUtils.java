package io.jenkins.tools.pluginmanager.impl;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PluginManagerUtils {

    private PluginManagerUtils() {
    }

    /**
     * Convert object arguments to strings and pass through to the String-ish method to append the path section
     * onto the base URL string
     *
     * @see #appendPathOntoUrl(String, String...)
     */
    static String appendPathOntoUrl(Object urlString, Object... pathSection) {
        String[] args = new String[pathSection.length];
        for (int i = 0; i < pathSection.length; ++i) {
            assert pathSection[i] != null;
            args[i] = pathSection[i].toString();
        }
        return appendPathOntoUrl(urlString.toString(), args);
    }

    /**
     * Given two strings, concatenate with a single slash at the point they are joined. Additional trailing and leading
     * slashes at that point will be adjusted so only one remains. If the first one ends in '://', it is preserved.
     *
     * @param urlString   the first part of a URL. (e.g. http://example.com) Trailing slashes are removed as appropriate.
     * @param pathSection the next part of a URL to be appended to the first part. (e.g. path/to/file.json) Leading
     *                    slashes are removed as appropriate.
     * @return the two parts concatenated with one slash at the point they are joined.
     */
    static String appendPathOntoUrl(String urlString, String... pathSection) {

        return new URIStringBuilder(urlString)
                .addPath(pathSection)
                .build();
    }

    /**
     * Given text loaded from a Jenkins update center, extract just the json content.<br/>
     * Jenkins update centers have meta-data in json form in a file "update-center.json" and, in the case of updates.jenkins.io,
     * another file "update-center.actual.json". The former file has wrapper text surrounding the json content. The latter
     * file has no wrapper and is totally json. This method checks for the wrapper and removes it if found.<br/>
     * Historically the wrapper is said to allow easier display of the json in browsers. The text to remove is
     * "updateCenter.post( ...json here... );" with all json content inside.<br/>
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

    /**
     * Convert object arguments to strings and pass through to the String-ish method to remove the last part of the path
     * from a URL
     *
     * @see #dirName(String)
     */
    static String dirName(Object urlString) {
        assert urlString != null;
        return dirName(urlString.toString());
    }

    /**
     * Extract the leading part of the URL up to but not including the last section (usually a filename). Parts in the
     * URL are separated by slashes. If there is no slash in the string, the string is returned unchanged.
     *
     * @param urlString a string containing a url from which to remove the trailing section.
     * @return
     */
    static String dirName(String urlString) {
        int lastSlashCol = urlString.lastIndexOf('/');
        if (lastSlashCol >= 0) {
            return urlString.substring(0, lastSlashCol + 1);
        }
        return urlString;
    }

    /**
     * Convert object arguments to strings and pass through to the String-ish method to insert the path into the url
     * before the last section of the url (probably the filename part)
     *
     * @see #insertPathPreservingFilename(String, String)
     */
    static String insertPathPreservingFilename(Object urlString, Object pathToInsert) {
        assert urlString != null;
        assert pathToInsert != null;
        return insertPathPreservingFilename(urlString.toString(), pathToInsert.toString());
    }

    /**
     * Insert the supplied pathToInsert into the URL preceeding the file name part (last part) of the URL. The parts, as
     * is usual for a URL, are separated by slashes.
     *
     * @param urlString    a complete URL string with domain name, path and filename, in that order.
     * @param pathToInsert another section of path to be inserted before the filename part of the URL
     * @return
     */
    static String insertPathPreservingFilename(String urlString, String pathToInsert) {
        int lastSlashCol = urlString.lastIndexOf('/');
        if (lastSlashCol >= 0) {
            return appendPathOntoUrl(urlString.substring(0, lastSlashCol + 1), pathToInsert, urlString.substring(lastSlashCol + 1));
        } else {
            return appendPathOntoUrl(pathToInsert, urlString);
        }
    }

}

