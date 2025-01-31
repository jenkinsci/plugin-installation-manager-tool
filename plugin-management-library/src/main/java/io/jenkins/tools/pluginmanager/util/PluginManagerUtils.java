package io.jenkins.tools.pluginmanager.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import io.jenkins.tools.pluginmanager.config.Settings;

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
     * Explodes the plugin archive.
     * @param source Source file
     * @param destDir Destination
     * @return the directory of the exploded plugin
     * @throws IOException on input / output error
     */
    @SuppressFBWarnings("PATH_TRAVERSAL_IN")
    public static File explodePlugin(File source, File destDir) throws IOException {
        try (JarFile jarfile = new JarFile(source)) {
            Enumeration<JarEntry> entries = jarfile.entries();
            while (entries.hasMoreElements()) {
                JarEntry je = entries.nextElement();
                File file = new File(destDir, je.getName());
                if (!file.exists()) {
                    Files.createDirectories(file.getParentFile().toPath());
                    file = new File(destDir, je.getName());
                }
                if (je.isDirectory()) {
                    continue;
                }

                try (InputStream is = jarfile.getInputStream(je);
                     FileOutputStream fo = new FileOutputStream(file)) {
                    IOUtils.copy(is, fo);
                }
            }
            return destDir;
        }
    }

    /**
     * Resolves the URL for the update center based on the cache suffix and the provided archive mirror URL.
     *
     * @param cacheSuffix The suffix for the Jenkins version (e.g., "-2.414.3")
     * @param jenkinsArchiveRepoMirror The URL of the Jenkins archive repository mirror (optional)
     * @return The resolved URL for the update center or null if not found
     */
    public static URL resolveArchiveUpdateCenterUrl(String cacheSuffix, URL jenkinsArchiveRepoMirror) {
        if (cacheSuffix == null || !cacheSuffix.startsWith("-")) {
            throw new IllegalArgumentException("Cache suffix must not be null and must start with a dash (-)");
        }

        String baseUrl = (jenkinsArchiveRepoMirror != null)
                ? jenkinsArchiveRepoMirror + "updates/" : Settings.DEFAULT_ARCHIVE_REPO_MIRROR_LOCATION + "updates/";

        String ucFile = "/update-center.json";
        String[] possiblePaths = {
                "stable" + cacheSuffix + ucFile,
                "dynamic" + cacheSuffix + ucFile,
                "dynamic-stable" + cacheSuffix + ucFile
        };

        // Check each possible URL until we find a valid one
        for (String path : possiblePaths) {
            URL url = constructUrl(baseUrl, path);
            if (isValidUrl(url)) {
                return url;
            }
        }
        return null;
    }

    /**
     * Checks if a given URL is valid by making an HTTP request and verifying the response code.
     *
     * @param url The URL to check
     * @return true if the URL is valid (returns HTTP 200), false otherwise
     */
    private static boolean isValidUrl(URL url) {
        try {
            String protocol = url.getProtocol();
            if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
                return false;
            }
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000); // Timeout in case the server is slow to respond
            connection.setReadTimeout(5000);
            int responseCode = connection.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Constructs a URL by combining a base URL and a path.
     * If the resulting URL is invalid, an {@link IllegalArgumentException} is thrown.
     *
     * @param baseUrl the base URL to which the path will be appended (e.g., "https:// archives.jenkins.io/")
     * @param path the path to append to the base URL (e.g., "updates/update-center.json")
     * @return the constructed URL
     * @throws IllegalArgumentException if the resulting URL is invalid
     */
    private static URL constructUrl(String baseUrl, String path) {
        try {
            return new URL(baseUrl + path);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URL construction: " + baseUrl + path, e);
        }
    }

}
