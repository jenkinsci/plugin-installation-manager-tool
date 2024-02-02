package io.jenkins.tools.pluginmanager.config;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

// see https://github.com/spotbugs/spotbugs/issues/1958#issuecomment-1056685201
@SuppressFBWarnings(value = "DMI_HARDCODED_ABSOLUTE_FILENAME", justification = "dumb rule that makes no sense")
public class Settings {
    public static final String DEFAULT_PLUGIN_DIR_LOCATION;
    public static final String DEFAULT_WAR;
    public static final URL DEFAULT_UPDATE_CENTER;
    public static final String DEFAULT_UPDATE_CENTER_FILENAME = "/update-center.json";
    public static final String DEFAULT_UPDATE_CENTER_LOCATION = "https://updates.jenkins.io" + DEFAULT_UPDATE_CENTER_FILENAME;
    public static final URL DEFAULT_EXPERIMENTAL_UPDATE_CENTER;
    public static final String DEFAULT_EXPERIMENTAL_UPDATE_CENTER_LOCATION = "https://updates.jenkins.io/experimental" + DEFAULT_UPDATE_CENTER_FILENAME;
    public static final URL DEFAULT_INCREMENTALS_REPO_MIRROR;
    public static final String DEFAULT_INCREMENTALS_REPO_MIRROR_LOCATION = "https://repo.jenkins-ci.org/incrementals";
    public static final URL DEFAULT_PLUGIN_INFO;
    public static final String DEFAULT_PLUGIN_INFO_LOCATION = "https://updates.jenkins.io/plugin-versions.json";
    public static final Path DEFAULT_CACHE_PATH;
    public static final HashFunction DEFAULT_HASH_FUNCTION = HashFunction.SHA256;

    private static final String DOCKER_IMAGE_WAR_LOCATION = "/usr/share/jenkins/jenkins.war";

    private static final String PACKAGING_WAR_LOCATION = "/usr/share/java/jenkins.war";

    static {
        String cacheBaseDir = System.getProperty("user.home");
        if (cacheBaseDir == null) {
            cacheBaseDir = System.getProperty("user.dir");
        }

        String cacheDirFromEnv = System.getenv("CACHE_DIR");
        if (cacheDirFromEnv == null) {
            DEFAULT_CACHE_PATH = Paths.get(cacheBaseDir, ".cache", "jenkins-plugin-management-cli");
        } else {
            DEFAULT_CACHE_PATH = Paths.get(cacheDirFromEnv);
        }

        try {
            DEFAULT_UPDATE_CENTER = new URL(DEFAULT_UPDATE_CENTER_LOCATION);
            DEFAULT_EXPERIMENTAL_UPDATE_CENTER = new URL(DEFAULT_EXPERIMENTAL_UPDATE_CENTER_LOCATION);
            DEFAULT_INCREMENTALS_REPO_MIRROR = new URL(DEFAULT_INCREMENTALS_REPO_MIRROR_LOCATION);
            DEFAULT_PLUGIN_INFO = new URL(DEFAULT_PLUGIN_INFO_LOCATION);
        } catch (MalformedURLException e) {
            /* Spotbugs 4.7.0 warns when throwing a runtime exception,
             * but the program cannot do anything with a malformed URL.
             * Spotbugs warning is ignored.
             */
            throw new RuntimeException(e);
        }

        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            DEFAULT_WAR = "C:\\ProgramData\\Jenkins\\jenkins.war";
            DEFAULT_PLUGIN_DIR_LOCATION = "C:\\ProgramData\\Jenkins\\Reference\\Plugins";
        } else {
            File file = new File(DOCKER_IMAGE_WAR_LOCATION);
            if (file.exists()) {
                DEFAULT_WAR = DOCKER_IMAGE_WAR_LOCATION;
            } else {
                DEFAULT_WAR = PACKAGING_WAR_LOCATION;
            }

            DEFAULT_PLUGIN_DIR_LOCATION = "/usr/share/jenkins/ref/plugins";
        }
    }
}
