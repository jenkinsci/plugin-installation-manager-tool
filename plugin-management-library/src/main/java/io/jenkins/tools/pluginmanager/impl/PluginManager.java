package io.jenkins.tools.pluginmanager.impl;

import hudson.util.VersionNumber;
import io.jenkins.tools.pluginmanager.config.Config;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONArray;
import org.json.JSONObject;


public class PluginManager {
    private static final Logger LOGGER = Logger.getLogger(PluginManager.class.getName());
    public static final String JENKINS_UC = "https://updates.jenkins.io";
    public static final String JENKINS_UC_EXPERIMENTAL = JENKINS_UC + "/experimental";
    public static final String JENKINS_UC_DOWNLOAD = JENKINS_UC + "/download";
    public static final String JENKINS_UC_JSON = JENKINS_UC + "/update-center.json";
    public static final String JENKINS_INCREMENTALS_REPO_MIRROR = "https://repo.jenkins-ci.org/incrementals";
    public static final String SEPARATOR = File.separator;
    private String JENKINS_UC_LATEST = "";
    private String jenkinsVersion;
    private List<Plugin> plugins;
    private List<Plugin> failedPlugins;
    private File refDir;
    private File jenkinsWarFile;
    private Map<String, VersionNumber> installedPluginVersions;
    private Map<String, VersionNumber> bundledPluginVersions;
    private List<SecurityWarning> allSecurityWarnings;
    Config cfg;


    public PluginManager(Config cfg) {
        if (cfg.isOutputVerbose()) {
            Handler consoleHandler = new ConsoleHandler();
            consoleHandler.setLevel(Level.FINE);
            LOGGER.setLevel(Level.FINE);
            LOGGER.addHandler(consoleHandler);
            LOGGER.setUseParentHandlers(false);
        }

        plugins = cfg.getPlugins();
        failedPlugins = new ArrayList();
        jenkinsWarFile = new File(cfg.getJenkinsWar());
        installedPluginVersions = new HashMap<>();
        bundledPluginVersions = new HashMap<>();
        refDir = cfg.getPluginDir();
        this.cfg = cfg;
        allSecurityWarnings = new ArrayList<>();
    }


    public void start() {
        if (!refDir.exists()) {
            try {
                Files.createDirectory(refDir.toPath());
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Unable to create plugin directory");
            }
        }

        jenkinsVersion = getJenkinsVersionFromWar();

        checkVersionSpecificUpdateCenter();

        getSecurityWarnings();

        if (cfg.isShowAllWarnings()) {
            LOGGER.log(Level.INFO, "All security warnings: {0}",
                    allSecurityWarnings.stream().map(p -> p.getName() + " - " + p.getMessage())
                            .collect(Collectors.joining(", ")));
        }

        bundledPlugins();
        installedPlugins();

        downloadPlugins(plugins);

        writeFailedPluginsToFile();

        LOGGER.log(Level.INFO, "Download complete");
    }


    public void getSecurityWarnings() {
        LOGGER.info("Getting security warnings");
        JSONObject updateCenterJson = getUpdateCenterJson();
        JSONArray warnings = updateCenterJson.getJSONArray("warnings");

        for (int i = 0; i < warnings.length(); i++) {
            JSONObject warning = warnings.getJSONObject(i);
            String warningType = warning.getString("type");
            if (!warningType.equals("plugin")) {
                continue;
            }
            String warningId = warning.getString("id");
            String warningMessage = warning.getString("message");
            String warningName = warning.getString("name");
            String warningUrl = warning.getString("url");

            SecurityWarning securityWarning = new SecurityWarning(warningId, warningMessage, warningName, warningUrl);
            JSONArray warningVersions = warning.getJSONArray("versions");
            for (int j = 0; j < warningVersions.length(); j++) {
                JSONObject warningVersion = warningVersions.getJSONObject(j);
                String lastVersion = "";
                if (warningVersion.has("lastVersion")) {
                    lastVersion = warningVersion.getString("lastVersion");
                }
                String pattern = warningVersion.getString("pattern");
                securityWarning.addSecurityVersion(lastVersion, pattern);
            }
            allSecurityWarnings.add(securityWarning);
        }
    }


    public void checkVersionSpecificUpdateCenter() {
        //check if version specific update center
        if (!StringUtils.isEmpty(jenkinsVersion)) {
            JENKINS_UC_LATEST = JENKINS_UC + "/" + jenkinsVersion;
            LOGGER.log(Level.INFO, "Check for version specific update center at url {0}", JENKINS_UC_LATEST);
            try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
                HttpGet httpget = new HttpGet(JENKINS_UC_LATEST);
                try (CloseableHttpResponse response = httpclient.execute(httpget)) {
                    if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                        JENKINS_UC_LATEST = "";
                    }
                } catch (IOException e) {
                    JENKINS_UC_LATEST = "";
                    LOGGER.log(Level.FINE, "No version specific update center for Jenkins version " + jenkinsVersion, e);
                }
            } catch (IOException e) {
                JENKINS_UC_LATEST = "";

                String logMessage = "Unable to check if version specific update center for Jenkins version " + jenkinsVersion;
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, logMessage, e);
                } else {
                    LOGGER.log(Level.WARNING, logMessage);
                }
            }
        }
    }


    public void writeFailedPluginsToFile() {
        try (
                FileOutputStream fileOutputStream = new FileOutputStream("failedplugins.txt");
                Writer fstream = new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8)
        ) {
            if (failedPlugins.size() > 0) {
                LOGGER.info("Writing failed plugins to file");
                for (Plugin plugin : failedPlugins) {
                    String failedPluginName = plugin.getName();
                    fstream.write(failedPluginName + "\n");
                }
                LOGGER.log(Level.INFO, "Some plugins failed to download: {0}",
                        failedPlugins.stream().map(p -> p.getName()).collect(Collectors.joining(", ")));
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error writing failed plugins to file");
        }
    }

    public void downloadPlugins(List<Plugin> plugins) {
        LOGGER.log(Level.INFO, "Downloading plugins");
        for (Plugin plugin : plugins) {
            downloadPlugin(plugin);
        }
    }

    public JSONObject getUpdateCenterJson() {
        URL updateCenter;

        try {
            updateCenter = new URL(JENKINS_UC_JSON);
        } catch (MalformedURLException e) {
            LOGGER.log(Level.WARNING, "Error setting Jenkins Update Center json URL");
            return null;
        }

        try {
            String updateCenterText = IOUtils.toString(updateCenter, Charset.forName("UTF-8"));
            updateCenterText = updateCenterText.replace("updateCenter.post(\n", "");
            updateCenterText = updateCenterText.replace(");", ""); //should probably make this more robust
            JSONObject updateCenterJson = new JSONObject(updateCenterText);
            return updateCenterJson;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error retrieving  Jenkins Update Center json");
            return null;
        }
    }


    public void resolveDependencies(Plugin plugin) {
        JSONObject updateCenterJson = getUpdateCenterJson();

        if (updateCenterJson == null) {
            LOGGER.log(Level.WARNING, "Unable to get update center json");
            return;
        }

        JSONObject plugins = updateCenterJson.getJSONObject("plugins");
        JSONObject pluginInfo = (JSONObject) plugins.get(plugin.getName());
        JSONArray dependencies = (JSONArray) pluginInfo.get("dependencies");

        if (dependencies == null || dependencies.length() == 0) {
            LOGGER.log(Level.FINE, "{0} has no dependencies", plugin.getName());
            return;
        }

        List<Plugin> dependentPlugins = new ArrayList<>();

        for (int i = 0; i < dependencies.length(); i++) {
            JSONObject dependency = dependencies.getJSONObject(i);
            String pluginName = dependency.getString("name");
            String pluginVersion = dependency.getString("version");
            boolean isPluginOptional = dependency.getBoolean("optional");
            Plugin dependentPlugin = new Plugin(pluginName, pluginVersion, isPluginOptional);
            dependentPlugins.add(dependentPlugin);
        }

        LOGGER.log(Level.FINE, "{0} depends on: {1}", new Object[]{plugin.getName(),
                dependentPlugins.stream().map(p -> p.getName() + ": " + p.getVersion()).collect(
                        Collectors.joining(", "))});


        for (Plugin dependency : dependentPlugins) {
            String dependencyName = dependency.getName();
            VersionNumber dependencyVersion = dependency.getVersion();
            if (dependency.getPluginOptional()) {
                LOGGER.log(Level.FINE, "Skipping optional dependency {0}", dependencyName);
                continue;
            }

            VersionNumber installedVersion = null;
            if (installedPluginVersions.containsKey(plugin.getName())) {
                installedVersion = installedPluginVersions.get(dependencyName);
            } else if (bundledPluginVersions.containsKey(plugin.getName())) {
                installedVersion = bundledPluginVersions.get(dependencyName);
            }

            if (installedVersion != null) {
                if (installedVersion.compareTo(dependencyVersion) < 0) {
                    LOGGER.log(Level.FINE, "Installed version of {0} is less than minimum required version of " +
                            "{1}, upgrading bundled dependency", new Object[]{dependencyName, dependencyVersion});
                    downloadPlugin(dependency);
                } else {
                    LOGGER.log(Level.FINE, "Skipping already installed dependency {0}", dependencyName);
                }
            } else {
                downloadPlugin(dependency);
            }
        }
    }


    public boolean downloadPlugin(Plugin plugin) {
        String pluginName = plugin.getName();
        VersionNumber pluginVersion = plugin.getVersion();

        if (installedPluginVersions.containsKey(pluginName) &&
                installedPluginVersions.get(pluginName).compareTo(pluginVersion) == 0) {
            LOGGER.log(Level.FINE, "{0} already installed, skipping", pluginName);
            return true;
        }
        String pluginDownloadUrl = getPluginDownloadUrl(plugin);
        boolean successfulDownload = downloadToFile(pluginDownloadUrl, plugin);
        if (!successfulDownload) {
            //some plugin don't follow the rules about artifact ID, i.e. docker-plugin
            String newPluginName = plugin.getName() + "-plugin";
            plugin.setName(newPluginName);
            pluginDownloadUrl = getPluginDownloadUrl(plugin);
            successfulDownload = downloadToFile(pluginDownloadUrl, plugin);
        }
        if (successfulDownload) {
            installedPluginVersions.put(plugin.getName(), pluginVersion);
            resolveDependencies(plugin);
        }

        if (!successfulDownload) {
            LOGGER.log(Level.FINE, "Unable to download {0}. Skipping....", plugin.getName());
            failedPlugins.add(plugin);
        }
        return successfulDownload;
    }


    public String getPluginDownloadUrl(Plugin plugin) {
        String pluginName = plugin.getName();
        String pluginVersion = plugin.getVersion().toString();
        String pluginUrl = plugin.getUrl();

        String urlString = "";

        if (StringUtils.isEmpty(pluginVersion)) {
            pluginVersion = "latest";
        }

        if (!StringUtils.isEmpty(pluginUrl)) {
            urlString = pluginUrl;
        } else if (pluginVersion.equals("latest") && !StringUtils.isEmpty(JENKINS_UC_LATEST)) {
            urlString = String.format("%s/latest/%s.hpi", JENKINS_UC_LATEST, pluginName);
        } else if (pluginVersion.equals("experimental")) {
            urlString = String.format("%s/latest/%s.hpi", JENKINS_UC_EXPERIMENTAL, pluginName);
        } else if (pluginVersion.contains("incrementals")) {
            String[] incrementalsVersionInfo = pluginVersion.split(";");
            String groupId = incrementalsVersionInfo[1];
            String incrementalsVersion = incrementalsVersionInfo[2];
            groupId = groupId.replace(".", "/");
            String incrementalsVersionPath =
                    String.format("%s/%s/%s-%s.hpi", pluginName, incrementalsVersion, pluginName, incrementalsVersion);
            urlString = String.format("%s/%s/%s", JENKINS_INCREMENTALS_REPO_MIRROR, groupId, incrementalsVersionPath);
        } else {
            urlString = String.format("%s/plugins/%s/%s/%s.hpi", JENKINS_UC_DOWNLOAD, pluginName, pluginVersion,
                    pluginName);
        }

        return urlString;
    }


    public boolean downloadToFile(String urlString, Plugin plugin) {
        LOGGER.log(Level.FINE, "Downloading plugin: {0} from url {1}", new Object[]{plugin.getName(), urlString});
        File pluginFile = new File(refDir + SEPARATOR + plugin.getArchiveFileName());
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpClientContext context = HttpClientContext.create();
            HttpGet httpget = new HttpGet(urlString);

            try (CloseableHttpResponse response = httpclient.execute(httpget, context)) {
                HttpHost target = context.getTargetHost();
                List<URI> redirectLocations = context.getRedirectLocations();
                // Expected to be an absolute URI
                URI location = URIUtils.resolve(httpget.getURI(), target, redirectLocations);
                FileUtils.copyURLToFile(location.toURL(), pluginFile);
            } catch (URISyntaxException | IOException e) {
                LOGGER.log(Level.WARNING, "Unable to resolve URL to download {0}", plugin.getName());
                return false;
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to create HTTP connection to download plugin");
            return false;
        }

        //check integrity of plugin file
        try (JarFile pluginJpi = new JarFile(pluginFile)) {
        } catch (IOException e) {
            failedPlugins.add(plugin);
            LOGGER.log(Level.WARNING, "Downloaded file is not a valid ZIP");
            return false;
        }

        return true;
    }


    public String getJenkinsVersionFromWar() {
        //java -jar $JENKINS_WAR --version
        if (jenkinsWarFile.exists()) {
            try (JarFile jenkinsWar = new JarFile(jenkinsWarFile)) {
                Manifest manifest = jenkinsWar.getManifest();
                Attributes attributes = manifest.getMainAttributes();
                String value = attributes.getValue("Jenkins-Version");
                LOGGER.info("Jenkins version is: " + value);
                return value;
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Unable to open war file", e);
            }
        }
        return "";
    }


    public String getPluginVersion(File file) {
        //this is also done in the existing plugin manager from core - should I do this a similar way to that instead?
        try (JarFile pluginJpi = new JarFile(file)) {
            Manifest manifest = pluginJpi.getManifest();
            Attributes attributes = manifest.getMainAttributes();
            return attributes.getValue("Plugin-Version");
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to get plugin version from {0}", file.toString());
        }
        return "";
    }

    public List<String> installedPlugins() {
        LOGGER.info("Fetching installed plugins");
        List<String> installedPlugins = new ArrayList<>();
        FileFilter fileFilter = new WildcardFileFilter("*.jpi");

        //only lists files in same directory, does not list files recursively
        File[] files = refDir.listFiles(fileFilter);

        if (files != null) {
            for (File file : files) {
                String pluginName = FilenameUtils.getBaseName(file.getName());
                VersionNumber pluginVersion = new VersionNumber(getPluginVersion(file));
                installedPluginVersions.put(pluginName, pluginVersion);
                installedPlugins.add(pluginName);
            }
        }

        LOGGER.log(Level.FINE, "Installed plugins: {0}", installedPlugins.stream().collect(Collectors.joining(", ")));

        return installedPlugins;
    }


    public List<String> bundledPlugins() {
        LOGGER.info("Fetching bundled plugins");

        List<String> bundledPlugins = new ArrayList<>();

        if (jenkinsWarFile.exists()) {
            //for i in $(jar tf $JENKINS_WAR | grep -E '[^detached-]plugins.*\..pi' | sort)
            Path path = Paths.get(jenkinsWarFile.toString());
            URI jenkinsWarUri;
            try {
                jenkinsWarUri = new URI("jar:" + path.toUri());
            } catch (URISyntaxException e) {
                LOGGER.log(Level.WARNING, "Error retrieving {0} to find bundled plugins", jenkinsWarFile.toString());
                return bundledPlugins;
            }

            //walk through war contents and find bundled plugins
            try (FileSystem warFS = FileSystems.newFileSystem(jenkinsWarUri, Collections.<String, Object>emptyMap())) {
                Path warPath = warFS.getPath("/").getRoot();
                PathMatcher matcher = warFS.getPathMatcher("regex:.*[^detached-]plugins.*\\.\\w+pi");
                Stream<Path> walk = Files.walk(warPath);
                for (Iterator<Path> it = walk.iterator(); it.hasNext(); ) {
                    Path file = it.next();
                    if (matcher.matches(file)) {
                        Path fileName = file.getFileName();
                        if (fileName != null) {
                            bundledPlugins.add(fileName.toString());
                            //because can't convert a ZipPath to a file with file.toFile();
                            InputStream in = Files.newInputStream(file);
                            final Path tempFile = Files.createTempFile("PREFIX", "SUFFIX");
                            try (FileOutputStream out = new FileOutputStream(tempFile.toFile())) {
                                IOUtils.copy(in, out);
                            }

                            VersionNumber pluginVersion = new VersionNumber(getPluginVersion(tempFile.toFile()));

                            Files.delete(tempFile);
                            bundledPluginVersions
                                    .put(FilenameUtils.getBaseName(fileName.toString()), pluginVersion);
                        }
                    }
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error finding bundled plugins from {0}", jenkinsWarFile.toString());
            }

        } else {
            LOGGER.log(Level.INFO, "War {0} not found, downloading all plugins", jenkinsWarFile.toString());
        }

        LOGGER.log(Level.FINE, "War bundled plugins: {0}", bundledPlugins.stream().collect(Collectors.joining(",")));

        return bundledPlugins;
    }

    public void setJenkinsVersion(String jenkinsVersion) {
        this.jenkinsVersion = jenkinsVersion;
    }

    public String getJenkinsVersion() {
        return jenkinsVersion;
    }

    public String getJenkinsUCLatest() {
        return JENKINS_UC_LATEST;
    }

    public void setJenkinsUCLatest(String updateCenterLatest) {
        JENKINS_UC_LATEST = updateCenterLatest;
    }
}
