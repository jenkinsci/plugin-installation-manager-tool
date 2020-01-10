package io.jenkins.tools.pluginmanager.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

public final class PluginManagerUtils {

    private PluginManagerUtils() {
    }

    /**
     * Convert object arguments to strings and pass through to the String-ish method to append the path section
     * onto the base URL string
     *
     * @param urlString   the first part of a URL as an Object that will be "toString()"ed. (e.g. result of toString() is
     *                    http://example.com) Trailing slashes are removed as appropriate. This could be, for example, a
     *                    URL object.
     * @param pathSection the next parts of a URL to be appended one after another to the first part. (e.g. path/to/file.json)
     *                    Leading and trailing slashes are removed as appropriate. Pass in one or more Objects for which
     *                    a call to toString() will return the usable String object.
     * @return the two parts concatenated with one slash at the point they are joined.
     * @see #appendPathOntoUrl(String, String...)
     */
    public static String appendPathOntoUrl(Object urlString, Object... pathSection) {
        String[] args = new String[pathSection.length];
        for (int i = 0; i < pathSection.length; ++i) {
            requireNonNull(pathSection[i]);
            args[i] = pathSection[i].toString();
        }
        return appendPathOntoUrl(urlString.toString(), args);
    }

    /**
     * Given two strings, concatenate with a single slash at the point they are joined. Additional trailing and leading
     * slashes at that point will be adjusted so only one remains. If the first one ends in '://', it is preserved.
     *
     * @param urlString   the first part of a URL. (e.g. http://example.com) Trailing slashes are removed as appropriate.
     * @param pathSection the next parts of a URL to be appended one after another to the first part.
     *                    (e.g. path/to/file.json) Leading and trailing slashes are removed as appropriate.
     * @return the two parts concatenated with one slash at the point they are joined.
     */
    public static String appendPathOntoUrl(String urlString, String... pathSection) {

        return new URIStringBuilder(urlString)
                .addPath(pathSection)
                .build();
    }

    /**
     * Given text loaded from a Jenkins update center, extract just the json content.<br>
     * Jenkins update centers have meta-data in json form in a file "update-center.json" and, in the case of updates.jenkins.io,
     * another file "update-center.actual.json". The former file has wrapper text surrounding the json content. The latter
     * file has no wrapper and is totally json. This method checks for the wrapper and removes it if found.<br>
     * Historically the wrapper is said to allow easier display of the json in browsers. The text to remove is
     * "updateCenter.post( ...json here... );" with all json content inside.<br>
     *
     * @param urlText text loaded from "update-center.json" or "update-center.actual.json"
     * @return the supplied text with any present wrapper removed
     */
    public static String removePossibleWrapperText(String urlText) {
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
     * @param urlString an Object for which toString produces a string containing a url from which to remove the
     *                  trailing section. The domain name and port section (that follows the double slash) will not
     *                  be removed. This could be, for example, a URL object.
     * @return the supplied string without the section after the last slash.
     * @see #dirName(String)
     */
    public static String dirName(Object urlString) {
        requireNonNull(urlString);
        return dirName(urlString.toString());
    }

    /**
     * Extract the leading part of the URL up to but not including the last section (usually a filename). The domain name
     * and port section (that follows the double slash) will not be removed. Parts in the URL are separated by slashes.
     * If there is no slash in the string, the string is returned unchanged.
     *
     * @param urlString a string containing a url from which to remove the trailing section.
     * @return the supplied string without the section after the last slash.
     */
    public static String dirName(String urlString) {
        int lastSlashCol = urlString.lastIndexOf('/');
        if (lastSlashCol > 0 && urlString.charAt(lastSlashCol - 1) != '/') {
            return urlString.substring(0, lastSlashCol + 1);
        }
        return urlString;
    }

    /**
     * Convert object arguments to strings and pass through to the String-ish method to remove the entire path part
     * from a URL
     *
     * @param urlString an Object for which toString() will be called to produce a string containing a url from which
     *                  to remove the entire path. This could be, for example, a URL object.
     * @return the string with no path. Only protocol, full domain name and port will remain.
     * @see #removePath(String)
     */
    public static String removePath(Object urlString) {
        requireNonNull(urlString);
        return removePath(urlString.toString());
    }

    /**
     * Remove the path part of a url, if present. For example, http://example.com/other/path/file.json will end up as
     * http://example.com without the path and filename portion.
     *
     * @param urlString a string containing a url from which to remove the path
     * @return the string with no path. Only protocol, full domain name and port will remain.
     */
    public static String removePath(String urlString) {
        String lastUrlString;
        do {
            lastUrlString = urlString;
            urlString = dirName(urlString);
            if (urlString.endsWith("/")) {
                urlString = urlString.substring(0, urlString.length() - 1);
            }
        } while (!urlString.equals(lastUrlString));

        return urlString;
    }

    /**
     * Convert object arguments to strings and pass through to the String-ish method to insert the path into the url
     * before the last section of the url (probably the filename part)
     *
     * @param urlString    an Object which will have toString() called to create a String containing a complete URL with
     *                     domain name, path and filename, in that order. This could be, for example, a URL object.
     * @param pathToInsert another section of path to be inserted before the filename part of the URL
     * @return the supplied URL string with the same domain name and port and the same filename but with the path
     * made longer to include the supplied pathToInsert just before the filename
     * @see #insertPathPreservingFilename(String, String)
     */
    public static String insertPathPreservingFilename(Object urlString, Object pathToInsert) {
        requireNonNull(urlString);
        requireNonNull(pathToInsert);
        return insertPathPreservingFilename(urlString.toString(), pathToInsert.toString());
    }

    /**
     * Insert the supplied pathToInsert into the URL preceeding the file name part (last part) of the URL. The parts, as
     * is usual for a URL, are separated by slashes.
     *
     * @param urlString    a complete URL string with domain name, path and filename, in that order.
     * @param pathToInsert another section of path to be inserted before the filename part of the URL
     * @return the supplied URL string with the same domain name and port and the same filename but with the path
     * made longer to include the supplied pathToInsert just before the filename
     */
    public static String insertPathPreservingFilename(String urlString, String pathToInsert) {
        int lastSlashCol = urlString.lastIndexOf('/');
        if (lastSlashCol >= 0) {
            return appendPathOntoUrl(urlString.substring(0, lastSlashCol + 1), pathToInsert, urlString.substring(lastSlashCol + 1));
        } else {
            return appendPathOntoUrl(pathToInsert, urlString);
        }
    }

}
