package io.jenkins.tools.pluginmanager.impl;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.util.VersionNumber;
import io.jenkins.tools.pluginmanager.config.Config;
import io.jenkins.tools.pluginmanager.config.Settings;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
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
    private List<Plugin> plugins;
    private List<Plugin> failedPlugins;
    private File refDir;
    private String jenkinsUcLatest;
    private VersionNumber jenkinsVersion;
    private File jenkinsWarFile;
    private Map<String, VersionNumber> installedPluginVersions;
    private Map<String, VersionNumber> bundledPluginVersions;
    private List<SecurityWarning> allSecurityWarnings;
    private Config cfg;
    private JSONObject latestUcJson;
    private JSONObject experimentalUcJson;
    private JSONObject pluginInfoJson;

    public static final String SEPARATOR = File.separator;

    public PluginManager(Config cfg) {
        this.cfg = cfg;
        plugins = cfg.getPlugins();
        refDir = cfg.getPluginDir();
        jenkinsWarFile = new File(cfg.getJenkinsWar());
        failedPlugins = new ArrayList();
        installedPluginVersions = new HashMap<>();
        bundledPluginVersions = new HashMap<>();
        allSecurityWarnings = new ArrayList<>();
        jenkinsUcLatest = cfg.getJenkinsUc().toString();
    }

    /**
     * Drives the process to download plugins. Calls methods to find installed plugins, download plugins, and output
     * the failed plugins
     */
    public void start() {
        if (!refDir.exists()) {
            try {
                Files.createDirectories(refDir.toPath());
            } catch (IOException e) {
                throw new DirectoryCreationException("Unable to create plugin directory", e);
            }
        }

        jenkinsVersion = getJenkinsVersionFromWar();
        checkAndSetLatestUpdateCenter();
        getUCJson();
        getSecurityWarnings();
        showAllSecurityWarnings();
        bundledPlugins();
        installedPlugins();
        downloadPlugins(plugins);
        outputFailedPlugins();
    }

    /**
     * Gets the security warnings for plugins from the update center json and creates a list of all the security
     * warnings
     */
    public void getSecurityWarnings() {
        if (latestUcJson == null) {
            System.out.println("Unable to get update center json");
            return;
        }
        JSONArray warnings = latestUcJson.getJSONArray("warnings");

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

    /**
     * Prints out all security warnings if isShowAllWarnings is set to true in the config file
     */
    public void showAllSecurityWarnings() {
        if (cfg.isShowAllWarnings()) {
            for (int i = 0; i < allSecurityWarnings.size(); i++) {
                SecurityWarning securityWarning = allSecurityWarnings.get(i);
                System.out.println(securityWarning.getName() + " - " + securityWarning.getMessage());
            }
        }
    }

    /**
     * Determines if there is an update center for the version of Jenkins in the war file. If so, sets jenkins update
     * center url String to include Jenkins Version. Otherwise, sets update center url to match the update center in
     * the configuration class
     */
    public void checkAndSetLatestUpdateCenter() {
        //check if version specific update center
        if (jenkinsVersion != null && !StringUtils.isEmpty(jenkinsVersion.toString())) {
            String jenkinsVersionUcLatest = cfg.getJenkinsUc() + "/" + jenkinsVersion;
            try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
                HttpGet httpget = new HttpGet(jenkinsVersionUcLatest);
                try (CloseableHttpResponse response = httpclient.execute(httpget)) {
                    if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                        System.out.println(
                                "Using version specific update center for latest plugins: " + jenkinsVersionUcLatest);
                        jenkinsUcLatest = jenkinsVersionUcLatest;
                    }
                } catch (IOException e) {
                    System.out.println("No version specific update center for Jenkins version " + jenkinsVersion);
                }
            } catch (IOException e) {
                System.out.println(
                        "Unable to check if version specific update center for Jenkins version " + jenkinsVersion);
            }
        }
    }

    /**
     * Prints out plugins that failed to download. Exits with status of 1 if any plugins failed to download.
     */
    @SuppressFBWarnings("DM_EXIT")
    public void outputFailedPlugins() {
        if (failedPlugins.size() > 0) {
            System.out.println("Some plugins failed to download: ");
            failedPlugins.stream().map(p -> p.getOriginalName() + " or " + p.getName()).forEach(System.out::println);
            throw new DownloadPluginException("Failed plugins");
        }
    }

    /**
     * Downloads a list of plugins
     *
     * @param plugins list of plugins to download
     */
    public void downloadPlugins(List<Plugin> plugins) {
        for (Plugin plugin : plugins) {
            boolean successfulDownload = downloadPlugin(plugin);
            if (!successfulDownload) {
                System.out.println("Unable to download " + plugin.getName() + ". Skipping...");
                failedPlugins.add(plugin);
            }
        }
    }

    /**
     * Gets the json object at the given url
     *
     * @param urlString string representing the url from which to get the json object
     * @return json object
     */
    public JSONObject getJson(String urlString) {
        URL url;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            throw new UpdateCenterInfoRetrievalException("Malformed url for update center", e);
        }
        try {
            String urlText = IOUtils.toString(url, Charset.forName("UTF-8"));
            JSONObject updateCenterJson = new JSONObject(urlText);
            return updateCenterJson;
        } catch (IOException e) {
            throw new UpdateCenterInfoRetrievalException("Error getting update center json", e);
        }
    }

    /**
     * Gets update center json, which is later used to determine plugin dependencies and security warnings
     */
    public void getUCJson() {
        System.out.println("\nRetrieving update center information");
        latestUcJson = getJson(jenkinsUcLatest + "/update-center.actual.json");
        experimentalUcJson = getJson(cfg.getJenkinsUcExperimental() + "/update-center.actual.json");
        pluginInfoJson = getJson(Settings.DEFAULT_PLUGIN_INFO_LOCATION);
    }

    /**
     * Gets the JSONArray containing plugin a
     *
     * @param plugin to get depedencies for
     * @param ucJson update center json from which to parse dependencies
     * @return JSONArray containing plugin dependencies
     */
    public JSONArray getPluginDependencyJsonArray(Plugin plugin, JSONObject ucJson) {
        JSONObject plugins = ucJson.getJSONObject("plugins");
        JSONObject pluginInfo = (JSONObject) plugins.get(plugin.getName());

        if (ucJson.equals(pluginInfoJson)) {
            //plugin-versions.json has a slightly different structure than other update center json
            if (pluginInfo.has(plugin.getVersion().toString())) {
                JSONObject specificVersionInfo = pluginInfo.getJSONObject(plugin.getVersion().toString());
                return (JSONArray) specificVersionInfo.get("dependencies");
            }
        } else {
            return (JSONArray) pluginInfo.get("dependencies");
        }
        return null;
    }

    /**
     * Resolves dependencies from downloaded plugin manifest. Done for plugins in which dependencies can't be determined
     * via easily via json, such as when a user downloads a plugin directly from url or incremental plugins
     *
     * @param plugin
     */
    public void resolveDependenciesFromManifest(Plugin plugin) {
        String dependencyString = getAttributefromManifest(plugin.getFile(), "Plugin-Dependencies");

        //not all plugin Manifests contain the Plugin-Dependencies field
        if (StringUtils.isEmpty(dependencyString)) {
            System.out.println(plugin.getName() + " has no dependencies");
            return;
        }

        String[] dependencies = dependencyString.split(",");
        List<Plugin> dependentPlugins = new ArrayList<>();

        System.out.println(plugin.getName() + " depends on: ");

        boolean isPluginOptional = false;

        for (String dependency : dependencies) {
            if (dependency.contains("resolution:=optional")) {
                dependency = dependency.split(";")[0];
                isPluginOptional = true;
            }
            String[] pluginInfo = dependency.split(":");
            String pluginName = pluginInfo[0];
            String pluginVersion = pluginInfo[1];
            Plugin dependentPlugin = new Plugin(pluginName, pluginVersion, isPluginOptional);
            dependentPlugins.add(dependentPlugin);

            System.out.println(pluginName + ": " + pluginVersion);
        }
        downloadDependencies(dependentPlugins);
    }


    /**
     * Finds the dependencies for a plugins by using the update center plugin-versions json.
     *
     * @param plugin for which to find and download dependencies
     */
    public void resolveDependencies(Plugin plugin) {
        JSONArray dependencies = null;
        String version = plugin.getVersion().toString();
        if (!StringUtils.isEmpty(plugin.getUrl()) || version.contains("incrementals")) {
            resolveDependenciesFromManifest(plugin);
        } else if (version.equals("latest")) {
            dependencies = getPluginDependencyJsonArray(plugin, latestUcJson);
        } else if (version.equals("experimental")) {
            dependencies = getPluginDependencyJsonArray(plugin, experimentalUcJson);
        } else {
            dependencies = getPluginDependencyJsonArray(plugin, pluginInfoJson);
        }
        if (dependencies == null) {
            resolveDependenciesFromManifest(plugin);
            return;
        }

        if (dependencies.length() == 0) {
            System.out.println(plugin.getName() + " has no dependencies");
            return;
        }

        List<Plugin> dependentPlugins = new ArrayList<>();

        System.out.println(plugin.getName() + " depends on: ");

        for (int i = 0; i < dependencies.length(); i++) {
            JSONObject dependency = dependencies.getJSONObject(i);
            String pluginName = dependency.getString("name");
            String pluginVersion = dependency.getString("version");
            boolean isPluginOptional = dependency.getBoolean("optional");
            Plugin dependentPlugin = new Plugin(pluginName, pluginVersion, isPluginOptional);
            dependentPlugins.add(dependentPlugin);

            System.out.println(pluginName + ": " + pluginVersion);
        }
        downloadDependencies(dependentPlugins);
    }


    /**
     * Downloads a list of dependencies. Skips downloading plugins that are optional or have already been installed. If
     * an installed version of a plugin is lower than the required version, will download the higher version of the
     * plugin to replace the currently installed version.
     *
     * @param dependentPlugins
     */
    public void downloadDependencies(List<Plugin> dependentPlugins) {
        for (Plugin dependency : dependentPlugins) {
            String dependencyName = dependency.getName();
            VersionNumber dependencyVersion = dependency.getVersion();
            if (dependency.getPluginOptional()) {
                System.out.println("Skipping optional dependency " + dependencyName);
                continue;
            }

            VersionNumber installedVersion = null;
            if (installedPluginVersions.containsKey(dependencyName)) {
                installedVersion = installedPluginVersions.get(dependencyName);
            } else if (bundledPluginVersions.containsKey(dependencyName)) {
                installedVersion = bundledPluginVersions.get(dependencyName);
            }

            if (installedVersion != null) {
                if (installedVersion.compareTo(dependencyVersion) < 0) {
                    System.out.println("Installed version (" + installedVersion + ") of " + dependencyName +
                            " is less than minimum " +
                            "required version of " + dependencyVersion + ", upgrading bundled dependency");
                    if (!downloadPlugin(dependency)) {
                        failedPlugins.add(dependency);
                        System.out.println("Unable to download " + dependency.getName() + ". Skipping...");
                    }
                } else {
                    System.out.println("Skipping already installed dependency " + dependencyName);
                }
            } else {
                if (!downloadPlugin(dependency)) {
                    failedPlugins.add(dependency);
                    System.out.println("Unable to download " + dependency.getName() + ". Skipping...");
                }
            }
        }
    }

    /**
     * Downloads a plugin, skipping if already installed or bundled in the war. A plugin's dependencies will be
     * resolved after the plugin is downloaded.
     *
     * @param plugin to download
     * @return boolean signifying if plugin was successful
     */
    public boolean downloadPlugin(Plugin plugin) {
        String pluginName = plugin.getName();
        VersionNumber pluginVersion = plugin.getVersion();

        if (installedPluginVersions.containsKey(pluginName) &&
                installedPluginVersions.get(pluginName).compareTo(pluginVersion) == 0) {
            System.out.println(pluginName + " already installed, skipping");
            return true;
        }
        String pluginDownloadUrl = getPluginDownloadUrl(plugin);
        boolean successfulDownload = downloadToFile(pluginDownloadUrl, plugin);
        if (!successfulDownload) {
            //some plugins don't follow the rules about artifact ID, i.e. docker-plugin
            String newPluginName = plugin.getName() + "-plugin";
            plugin.setName(newPluginName);
            pluginDownloadUrl = getPluginDownloadUrl(plugin);
            successfulDownload = downloadToFile(pluginDownloadUrl, plugin);
        }
        if (successfulDownload) {
            System.out.println("downloaded successful");
            installedPluginVersions.put(plugin.getName(), pluginVersion);
            resolveDependencies(plugin);
        }
        return successfulDownload;
    }

    /**
     * Determines the plugin download url. If a url is specified from the CLI or plugins file, that url will be used
     * and the plugin verison and Jenkins version will be ignored. If no url is specified, the url will be
     * determined from the Jenkins update center and plugin name.
     *
     * @param plugin to download
     * @return url to download plugin from
     */
    public String getPluginDownloadUrl(Plugin plugin) {
        String pluginName = plugin.getName();
        String pluginVersion = plugin.getVersion().toString();
        String pluginUrl = plugin.getUrl();

        String urlString = "";

        if (StringUtils.isEmpty(pluginVersion)) {
            pluginVersion = "latest";
        }

        if (!StringUtils.isEmpty(pluginUrl)) {
            System.out.println("Will use url: " + pluginUrl);
            urlString = pluginUrl;
        } else if (pluginVersion.equals("latest") && !StringUtils.isEmpty(jenkinsUcLatest)) {
            urlString = String.format("%s/latest/%s.hpi", jenkinsUcLatest, pluginName);
        } else if (pluginVersion.equals("experimental")) {
            urlString = String.format("%s/latest/%s.hpi", cfg.getJenkinsUcExperimental(), pluginName);
        } else if (!StringUtils.isEmpty(plugin.getGroupId())) {
            String groupId = plugin.getGroupId();
            groupId = groupId.replace(".", "/");
            String incrementalsVersionPath =
                    String.format("%s/%s/%s-%s.hpi", pluginName, pluginVersion, pluginName, pluginVersion);
            urlString =
                    String.format("%s/%s/%s", cfg.getJenkinsIncrementalsRepoMirror(), groupId, incrementalsVersionPath);
        } else {
            urlString = String.format("%s/download/plugins/%s/%s/%s.hpi", cfg.getJenkinsUc(), pluginName, pluginVersion,
                    pluginName);
        }

        return urlString;
    }

    /**
     * Downloads a plugin from a url
     *
     * @param urlString url to download the plugin from
     * @param plugin    Plugin object representing plugin to be downloaded
     * @return true if download is successful, false otherwise
     */
    public boolean downloadToFile(String urlString, Plugin plugin) {
        System.out.println("\nDownloading plugin " + plugin.getName() + " from url: " + urlString);

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
                System.out.println("Unable to resolve plugin URL or download plugin to file");
                return false;
            }
        } catch (IOException e) {
            System.out.println("Unable to create HTTP connection to download plugin");
            return false;
        }

        // Check integrity of plugin file
        try (JarFile pluginJpi = new JarFile(pluginFile)) {
            plugin.setFile(pluginFile);
        } catch (IOException e) {
            failedPlugins.add(plugin);
            System.out.println("Downloaded file is not a valid ZIP");
            return false;
        }

        return true;
    }

    public String getAttributefromManifest(File file, String key) {
        try (JarFile jarFile = new JarFile(file)) {
            Manifest manifest = jarFile.getManifest();
            Attributes attributes = manifest.getMainAttributes();
            return attributes.getValue(key);
        } catch (IOException e) {
            System.out.println("Unable to open " + file);
            if (key.equals("Plugin-Dependencies")) {
                throw new DownloadPluginException("Unable to determine plugin dependencies", e);
            }

        }
        return null;
    }

    /**
     * Gets the Jenkins version from the manifest in the Jenkins war specified in the Config class
     *
     * @return Jenkins version
     */
    public VersionNumber getJenkinsVersionFromWar() {
        String version = getAttributefromManifest(jenkinsWarFile, "Jenkins-Version");
        if (StringUtils.isEmpty(version)) {
            System.out.println("Unable to get version from war file");
            return null;
        }
        return new VersionNumber(version);
    }

    /**
     * Finds the plugin version by reading the manifest of a .hpi or .jpi file
     *
     * @param file plugin .hpi or .jpi of which to get the version
     * @return plugin version
     */
    public String getPluginVersion(File file) {
        String version = getAttributefromManifest(file, "Plugin-Version");
        if (StringUtils.isEmpty(version)) {
            System.out.println("Unable to get plugin version from " + file);
            return "";
        }
        return version;
    }

    /**
     * Finds all the plugins and their versions currently in the plugin directory specified in the Config class
     *
     * @return list of names of plugins that are installed in the plugin directory
     */
    public List<String> installedPlugins() {
        List<String> installedPlugins = new ArrayList<>();
        FileFilter fileFilter = new WildcardFileFilter("*.jpi");

        // Only lists files in same directory, does not list files recursively
        System.out.println("\nInstalled plugins: ");
        File[] files = refDir.listFiles(fileFilter);

        if (files != null) {
            for (File file : files) {
                String pluginName = FilenameUtils.getBaseName(file.getName());
                VersionNumber pluginVersion = new VersionNumber(getPluginVersion(file));
                installedPluginVersions.put(pluginName, pluginVersion);
                installedPlugins.add(pluginName);
                System.out.println(pluginName + ": " + pluginVersion);
            }
        }

        return installedPlugins;
    }

    /**
     * Finds the plugins and their versions bundled in the war file specified in the Config class. Does not include
     * detached plugins.
     *
     * @return list of names of plugins that are currently installed in the war
     */
    public List<String> bundledPlugins() {
        List<String> bundledPlugins = new ArrayList<>();

        if (jenkinsWarFile.exists()) {
            Path path = Paths.get(jenkinsWarFile.toString());
            URI jenkinsWarUri;
            try {
                jenkinsWarUri = new URI("jar:" + path.toUri());
            } catch (URISyntaxException e) {
                throw new WarBundledPluginException("Unable to open war file to extract bundled plugin information", e);
            }

            // Walk through war contents and find bundled plugins
            try (FileSystem warFS = FileSystems.newFileSystem(jenkinsWarUri, Collections.<String, Object>emptyMap())) {
                Path warPath = warFS.getPath("/").getRoot();
                PathMatcher matcher = warFS.getPathMatcher("regex:.*[^detached-]plugins.*\\.\\w+pi");
                Stream<Path> walk = Files.walk(warPath);
                System.out.println("\nWar bundled plugins: ");
                for (Iterator<Path> it = walk.iterator(); it.hasNext(); ) {
                    Path file = it.next();
                    if (matcher.matches(file)) {
                        Path fileName = file.getFileName();
                        if (fileName != null) {
                            bundledPlugins.add(fileName.toString());
                            System.out.println(fileName.toString());
                            // Because can't convert a ZipPath to a file with file.toFile()
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
                throw new WarBundledPluginException("Unable to open war file to extract bundled plugin information", e);
            }
        } else {
            System.out.println("War not found, installing all plugins: " + jenkinsWarFile.toString());
        }

        return bundledPlugins;
    }

    /**
     * Sets Jenkins Version. Jenkins version also set based on Jenkins war manifest
     *
     * @param jenkinsVersion version of Jenkins, used for checking/setting version specific update center
     */
    public void setJenkinsVersion(VersionNumber jenkinsVersion) {
        this.jenkinsVersion = jenkinsVersion;
    }

    /**
     * Gets the Jenkins version
     *
     * @return the Jenkins version
     */
    public VersionNumber getJenkinsVersion() {
        return jenkinsVersion;
    }

    /**
     * Gets the update center url string
     *
     * @return Jenkins update center url string
     */
    public String getJenkinsUCLatest() {
        return jenkinsUcLatest;
    }

    /**
     * Sets the update center url string
     *
     * @param updateCenterLatest String in which to set the update center url string
     */
    public void setJenkinsUCLatest(String updateCenterLatest) {
        jenkinsUcLatest = updateCenterLatest;
    }
}
