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
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONArray;
import org.json.JSONObject;

import static io.jenkins.tools.pluginmanager.util.PluginManagerUtils.appendPathOntoUrl;
import static io.jenkins.tools.pluginmanager.util.PluginManagerUtils.dirName;
import static io.jenkins.tools.pluginmanager.util.PluginManagerUtils.insertPathPreservingFilename;
import static io.jenkins.tools.pluginmanager.util.PluginManagerUtils.removePath;
import static io.jenkins.tools.pluginmanager.util.PluginManagerUtils.removePossibleWrapperText;

public class PluginManager {
    private List<Plugin> failedPlugins;
    private File refDir;
    private String jenkinsUcLatest;
    private VersionNumber jenkinsVersion;
    private File jenkinsWarFile;
    private Map<String, Plugin> installedPluginVersions;
    private Map<String, Plugin> bundledPluginVersions;
    private Map<String, List<SecurityWarning>> allSecurityWarnings;
    private Map<String, Plugin> allPluginsAndDependencies;
    private Map<String, Plugin> effectivePlugins;
    private List<Plugin> pluginsToBeDownloaded;
    private Config cfg;
    private JSONObject latestUcJson;
    private JSONObject experimentalUcJson;
    private JSONObject pluginInfoJson;
    private JSONObject latestPlugins;
    private boolean verbose;
    private boolean useLatestSpecified;
    private boolean useLatestAll;

    public static final String SEPARATOR = File.separator;

    private static final int DEFAULT_MAX_RETRIES = 3;

    public PluginManager(Config cfg) {
        this.cfg = cfg;
        refDir = cfg.getPluginDir();
        jenkinsWarFile = new File(cfg.getJenkinsWar());
        failedPlugins = new ArrayList<>();
        installedPluginVersions = new HashMap<>();
        bundledPluginVersions = new HashMap<>();
        allSecurityWarnings = new HashMap<>();
        allPluginsAndDependencies = new HashMap<>();
        verbose = cfg.isVerbose();
        jenkinsUcLatest = cfg.getJenkinsUc().toString();
        useLatestSpecified = cfg.isUseLatestSpecified();
        useLatestAll = cfg.isUseLatestAll();
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

        if (useLatestSpecified && useLatestAll) {
            throw new PluginDependencyStrategyException("Only one plugin dependency version strategy can be selected " +
                    "at a time");
        }

        jenkinsVersion = getJenkinsVersionFromWar();
        checkAndSetLatestUpdateCenter();
        getUCJson();
        getSecurityWarnings();
        showAllSecurityWarnings();
        bundledPluginVersions = bundledPlugins();
        installedPluginVersions = installedPlugins();

        allPluginsAndDependencies = findPluginsAndDependencies(cfg.getPlugins());
        pluginsToBeDownloaded = findPluginsToDownload(allPluginsAndDependencies);
        effectivePlugins = findEffectivePlugins(pluginsToBeDownloaded);

        listPlugins();
        showSpecificSecurityWarnings(pluginsToBeDownloaded);
        showAvailableUpdates(pluginsToBeDownloaded);
        checkVersionCompatibility(pluginsToBeDownloaded);

        if (cfg.doDownload()) {
            downloadPlugins(pluginsToBeDownloaded);
        }
        System.out.println("Done");
    }

    /**
     * Compares the list of all requested plugins to the currently installed plugins to determine the final list of
     * plugins that will be downloaded
     *
     * @param requestedPlugins list of all requested plugins, determined from the highest required versions of the
     *                         initial user requested plugins and their recursive dependencies
     * @return list of plugins that will be downloaded when taking into account the already installed plugins and the
     * highest required versions of the same plugin
     */
    public List<Plugin> findPluginsToDownload(Map<String, Plugin> requestedPlugins) {
        List<Plugin> pluginsToDownload = new ArrayList<>();
        for (Map.Entry<String, Plugin> requestedPlugin : requestedPlugins.entrySet()) {
            String pluginName = requestedPlugin.getKey();
            Plugin plugin = requestedPlugin.getValue();
            VersionNumber installedVersion = null;
            if (installedPluginVersions.containsKey(pluginName)) {
                installedVersion = installedPluginVersions.get(pluginName).getVersion();
            } else if (bundledPluginVersions.containsKey(pluginName)) {
                installedVersion = bundledPluginVersions.get(pluginName).getVersion();
            } else if (bundledPluginVersions.containsKey(pluginName) &&
                    installedPluginVersions.containsKey(pluginName)) {
                installedVersion = bundledPluginVersions.get(pluginName).getVersion().
                        isNewerThan(installedPluginVersions.get(pluginName).getVersion()) ?
                        bundledPluginVersions.get(pluginName).getVersion() :
                        installedPluginVersions.get(pluginName).getVersion();
            }
            if (installedVersion == null) {
                pluginsToDownload.add(plugin);
            } else if (installedVersion.isOlderThan(plugin.getVersion())) {
                logVerbose(String.format(
                        "Installed version (%s) of %s is less than minimum required version of %s, bundled " +
                                "plugin will be upgraded", installedVersion, pluginName, plugin.getVersion()));
                pluginsToDownload.add(plugin);
            }
        }
        return pluginsToDownload;
    }

    /**
     * Finds the final set of plugins that have been either been installed or will be downloaded. If a plugin is in
     * more than one set of the already installed plugins, bundled plugins, or plugins that will be installed, the
     * highest version of the plugin is taken.
     *
     * @param pluginsToBeDownloaded list of plugins and recursive dependencies requested by user
     * @return set of plugins that is downloaded or will be downloaded
     */
    public Map<String, Plugin> findEffectivePlugins(List<Plugin> pluginsToBeDownloaded) {
        Map<String, Plugin> effectivePlugins = new HashMap<>();
        for (Plugin plugin : pluginsToBeDownloaded) {
            effectivePlugins.put(plugin.getName(), plugin);
        }

        sortEffectivePlugins(effectivePlugins, installedPluginVersions);
        sortEffectivePlugins(effectivePlugins, bundledPluginVersions);
        return effectivePlugins;
    }

    private void sortEffectivePlugins(Map<String, Plugin> effectivePlugins,
                                      Map<String, Plugin> installedPluginVersions) {
        for (Map.Entry<String, Plugin> installedEntry : installedPluginVersions.entrySet()) {
            if (!effectivePlugins.containsKey(installedEntry.getKey())) {
                effectivePlugins.put(installedEntry.getKey(), installedEntry.getValue());
            } else if ((effectivePlugins.get(installedEntry.getKey()).getVersion())
                    .isOlderThan(installedEntry.getValue().getVersion())) {
                effectivePlugins.replace(installedEntry.getKey(), installedEntry.getValue());
            }
        }
    }

    /**
     * Lists installed plugins, bundled plugins, set of all recurively determined requested plugins, which plugins will
     * actually be downloaded based on the requested plugins and currently installed plugins, and the effective plugin
     * set, which includes all currently installed plugins and plugins that will be downloaded by the tool
     */
    public void listPlugins() {
        if (cfg.isShowPluginsToBeDownloaded()) {
            logPlugins("Installed plugins:", new ArrayList<>(installedPluginVersions.values()));
            logPlugins("Bundled plugins:", new ArrayList<>(bundledPluginVersions.values()));
            logPlugins("Set of all requested plugins:", new ArrayList<>(allPluginsAndDependencies.values()));
            logPlugins("Set of all requested plugins that will be downloaded:", pluginsToBeDownloaded);
            logPlugins("Set of all existing plugins and plugins that will be downloaded:",
                    new ArrayList<>(effectivePlugins.values()));
        }
    }

    /**
     * Given a list of plugins and a description, prints them out
     *
     * @param description string describing plugins to be printed
     * @param plugins     list of plugins to be output
     */
    public void logPlugins(String description, List<Plugin> plugins) {
        System.out.println("\n" + description);
        plugins.stream().sorted()
                .forEach(System.out::println);
    }

    /**
     * Gets the security warnings for plugins from the update center json and creates a list of all the security
     * warnings
     *
     * @return map of plugins and their security warnings
     */
    public Map<String, List<SecurityWarning>> getSecurityWarnings() {
        if (latestUcJson == null) {
            System.out.println("Unable to get update center json");
            return allSecurityWarnings;
        }
        if (!latestUcJson.has("warnings")) {
            System.out.println("update center json has no warnings: ignoring");
            return allSecurityWarnings;
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
                String firstVersion = "";
                if (warningVersion.has("firstVersion")) {
                    firstVersion = warningVersion.getString("firstVersion");
                }
                String lastVersion = "";
                if (warningVersion.has("lastVersion")) {
                    lastVersion = warningVersion.getString("lastVersion");
                }
                String pattern = warningVersion.getString("pattern");
                securityWarning.addSecurityVersion(firstVersion, lastVersion, pattern);
            }

            allSecurityWarnings.computeIfAbsent(warningName, k -> new ArrayList<>()).add(securityWarning);
        }
        return allSecurityWarnings;
    }

    /**
     * Prints out all security warnings if isShowAllWarnings is set to true in the config file
     */
    public void showAllSecurityWarnings() {
        if (cfg.isShowAllWarnings()) {
            allSecurityWarnings.values().stream().sorted().
                    forEach(p -> p.stream().sorted().
                            map(w -> w.getName() + " - " + w.getMessage()).forEach(System.out::println));
        }
    }

    /**
     * Prints out security warning information for a list of plugins if isShowWarnings is set to true in the config
     * file
     *
     * @param plugins list of plugins for which to see security warnings
     */

    public void showSpecificSecurityWarnings(List<Plugin> plugins) {
        if (cfg.isShowWarnings()) {
            System.out.println("\nSecurity warnings:");
            for (Plugin plugin : plugins) {
                if (warningExists(plugin)) {
                    String pluginName = plugin.getName();
                    System.out.println(plugin.getSecurityWarnings().stream()
                            .map(warning -> String.format("%s (%s): %s %s %s", pluginName,
                                    plugin.getVersion(), warning.getId(), warning.getMessage(), warning.getUrl())).
                                    collect(Collectors.joining("\n")));
                }
            }
        }
    }


    /**
     * Prints out if any plugins of a given list have available updates in the latest update center
     *
     * @param plugins List of plugins to check versions against latest versions in update center
     */
    public void showAvailableUpdates(List<Plugin> plugins) {
        if (cfg.isShowAvailableUpdates()) {
            System.out.println("\nAvailable updates:");
            for (Plugin plugin : plugins) {
                VersionNumber latestVersion = getLatestPluginVersion(plugin.getName());
                if (plugin.getVersion().isOlderThan(latestVersion)) {
                    System.out.println(String.format("%s (%s) has an available update: %s", plugin.getName(),
                            plugin.getVersion(), latestVersion));
                }
            }
        }
    }

    /**
     * Checks if a security warning exists for a plugin and its version. If that plugin version is affected by a
     * security warning, adds the security warning to the list of security warnings for plugin
     *
     * @param plugin to check for security warning
     * @return true if security warning for plugin exists, false otherwise
     */
    public boolean warningExists(Plugin plugin) {
        String pluginName = plugin.getName();
        List<SecurityWarning> securityWarnings = new ArrayList<>();
        if (allSecurityWarnings.containsKey(pluginName)) {
            for (SecurityWarning securityWarning : allSecurityWarnings.get(pluginName)) {
                for (SecurityWarning.SecurityVersion effectedVersion : securityWarning.getSecurityVersions()) {
                    Matcher m = effectedVersion.getPattern().matcher(plugin.getVersion().toString());
                    if (m.matches()) {
                        securityWarnings.add(securityWarning);
                    }
                }
            }
        }
        plugin.setSecurityWarnings(securityWarnings);
        return !securityWarnings.isEmpty();
    }

    /**
     * Checks that required Jenkins version of all plugins to be downloaded is less than the Jenkins version in the
     * user specified Jenkins war file
     *
     * @param pluginsToBeDownloaded list of plugins to check version compatibility with the Jenkins version
     */
    public void checkVersionCompatibility(List<Plugin> pluginsToBeDownloaded) {
        if (jenkinsVersion != null && !StringUtils.isEmpty(jenkinsVersion.toString())) {
            for (Plugin p : pluginsToBeDownloaded) {
                if (p.getJenkinsVersion() != null) {
                    if (p.getJenkinsVersion().isNewerThan(jenkinsVersion)) {
                        throw new VersionCompatibilityException(
                                String.format("%n%s (%s) requires a greater version of Jenkins (%s) than %s in %s",
                                        p.getName(), p.getVersion().toString(), p.getJenkinsVersion().toString(),
                                        jenkinsVersion.toString(), jenkinsWarFile.toString()));
                    }
                }
            }
        }
    }

    /**
     * Determines if there is an update center for the version of Jenkins in the war file. If so, sets jenkins update
     * center url String to include Jenkins Version. Otherwise, sets update center url to match the update center in
     * the configuration class
     *
     * Rules:
     * jenkins version  | use if readable (http HEAD verb)          | use prior value anyway
     *      YES         | http://update-center.jenkins.io/(version) | http://update-center.jenkins.io
     *      NO          |                                           | http://update-center.jenkins.io
     */
    @SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE")
    public void checkAndSetLatestUpdateCenter() {
        //check if version specific update center
        if (jenkinsVersion != null && !StringUtils.isEmpty(jenkinsVersion.toString())) {
            String jenkinsVersionUcLatest = insertPathPreservingFilename(cfg.getJenkinsUc(), jenkinsVersion);
            try (CloseableHttpClient httpclient = HttpClients.createSystem()) {
                HttpHead httphead = new HttpHead(jenkinsVersionUcLatest);
                try (CloseableHttpResponse response = httpclient.execute(httphead)) {
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
     * Downloads a list of plugins
     *
     * @param plugins list of plugins to download
     */
    public void downloadPlugins(List<Plugin> plugins) {
        ForkJoinPool ioThreadPool = new ForkJoinPool(64);
        try {
            ioThreadPool.submit(() -> plugins.parallelStream().forEach(plugin -> {
                boolean successfulDownload = downloadPlugin(plugin, null);
                if (!successfulDownload) {
                    throw new DownloadPluginException("Unable to download " + plugin.getName());
                }
            })).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof DownloadPluginException) {
                throw (DownloadPluginException) e.getCause();
            } else {
                e.printStackTrace();
            }
        }
    }

    /**
     * Given a list of plugins, finds the recursive set of all dependent plugins. If multiple plugins rely on different
     * versions of the same plugin, the higher version required will replace the lower version dependency
     *
     * @param requestedPlugins list of plugins to find all dependencies for
     * @return set of all requested plugins and their recursive dependencies
     */
    public Map<String, Plugin> findPluginsAndDependencies(List<Plugin> requestedPlugins) {
        Map<String, Plugin> allPluginDependencies = new HashMap<>();

        for (Plugin requestedPlugin : requestedPlugins) {
            //for each requested plugin, find all the dependent plugins that will be downloaded (including requested plugin)
            Map<String, Plugin> dependencies = resolveRecursiveDependencies(requestedPlugin);

            for (Plugin dependentPlugin : dependencies.values()) {
                String dependencyName = dependentPlugin.getName();
                VersionNumber dependencyVersion = dependentPlugin.getVersion();
                if (!allPluginDependencies.containsKey(dependencyName)) {
                    allPluginDependencies.put(dependencyName, dependentPlugin);
                } else {
                    Plugin existingDependency = allPluginDependencies.get(dependencyName);
                    if (existingDependency.getVersion().isOlderThan(dependencyVersion)) {
                        outputPluginReplacementInfo(existingDependency, dependentPlugin);
                        allPluginDependencies.replace(existingDependency.getName(), dependentPlugin);
                    }
                }
            }
        }
        return allPluginDependencies;
    }

    /**
     * Outputs information about a lower version of a plugin being replaced by a higher version
     *
     * @param lowerVersion  lower version of plugin
     * @param higherVersion higher version of plugin
     */
    public void outputPluginReplacementInfo(Plugin lowerVersion, Plugin higherVersion) {
        logVerbose(String.format(
                "Version of %s (%s) required by %s (%s) is lower than the version required (%s) " +
                        "by %s (%s), upgrading required plugin version",
                lowerVersion.getName(),
                lowerVersion.getVersion().toString(),
                lowerVersion.getParent().getName(),
                lowerVersion.getParent().getVersion().toString(),
                higherVersion.getVersion().toString(),
                higherVersion.getParent().getName(),
                higherVersion.getParent().getVersion().toString()));
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
            String urlText = IOUtils.toString(url, StandardCharsets.UTF_8);
            return new JSONObject(removePossibleWrapperText(urlText));
        } catch (IOException e) {
            throw new UpdateCenterInfoRetrievalException("Error getting update center json", e);
        }
    }

    /**
     * Gets update center json, which is later used to determine plugin dependencies and security warnings
     */
    public void getUCJson() {
        logVerbose("\nRetrieving update center information");
        latestUcJson = getJson(jenkinsUcLatest);
        latestPlugins = latestUcJson.getJSONObject("plugins");
        experimentalUcJson = getJson(cfg.getJenkinsUcExperimental().toString());
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
        if (!plugins.has(plugin.getName())) {
            return null;
        }

        JSONObject pluginInfo = (JSONObject) plugins.get(plugin.getName());

        if (ucJson.equals(pluginInfoJson)) {
            //plugin-versions.json has a slightly different structure than other update center json
            if (pluginInfo.has(plugin.getVersion().toString())) {
                JSONObject specificVersionInfo = pluginInfo.getJSONObject(plugin.getVersion().toString());
                plugin.setJenkinsVersion(specificVersionInfo.getString("requiredCore"));
                return (JSONArray) specificVersionInfo.get("dependencies");
            }
        } else {
            plugin.setJenkinsVersion(pluginInfo.getString("requiredCore"));
            //plugin version is latest or experimental
            String version = pluginInfo.getString("version");
            plugin.setVersion(new VersionNumber(version));
            return (JSONArray) pluginInfo.get("dependencies");
        }
        return null;
    }

    /**
     *
     * @param pluginName the name of the plugin
     * @return latest version of the specified plugin
     */
    public VersionNumber getLatestPluginVersion(String pluginName) {
        if (!latestPlugins.has(pluginName)) {
            throw new PluginNotFoundException(String.format("Unable to find plugin %s in update center %s", pluginName,
                    jenkinsUcLatest));
        }

        JSONObject pluginInfo = (JSONObject) latestPlugins.get(pluginName);
        String latestPluginVersion = pluginInfo.getString("version");

        return new VersionNumber(latestPluginVersion);
    }

    /**
     * Resolves direct dependencies from downloaded plugin manifest. Done for plugins in which dependencies can't be
     * determined via easily via json, such as when a user downloads a plugin directly from url or incremental plugins,
     * or in other cases when getting information from json fails
     *
     * @param plugin plugin to resolve direct dependencies for
     * @return list of dependencies that were parsed from the plugin's manifest file
     */
    public List<Plugin> resolveDependenciesFromManifest(Plugin plugin) {
        List<Plugin> dependentPlugins = new ArrayList<>();
        try {
            File tempFile = Files.createTempFile(plugin.getName(), ".jpi").toFile();
            logVerbose(
                    String.format("%nResolving dependencies of %s by downloading plugin to temp file %s and parsing " +
                            "MANIFEST.MF", plugin.getName(), tempFile.toString()));
            if (!downloadPlugin(plugin, tempFile)) {
                Files.delete(tempFile.toPath());
                throw new DownloadPluginException("Unable to resolve dependencies for " + plugin.getName());
            }

            if (plugin.getVersion().toString().equals("latest") ||
                    plugin.getVersion().toString().equals("experimental")) {
                String version = getAttributeFromManifest(tempFile, "Plugin-Version");
                if (!StringUtils.isEmpty(version)) {
                    plugin.setVersion(new VersionNumber(version));
                }
            }

            String dependencyString = getAttributeFromManifest(tempFile, "Plugin-Dependencies");

            //not all plugin Manifests contain the Plugin-Dependencies field
            if (StringUtils.isEmpty(dependencyString)) {
                logVerbose("\n" + plugin.getName() + " has no dependencies");
                return dependentPlugins;
            }
            String[] dependencies = dependencyString.split(",");

            //ignore optional dependencies
            for (String dependency : dependencies) {
                if (!dependency.contains("resolution:=optional")) {
                    String[] pluginInfo = dependency.split(":");
                    String pluginName = pluginInfo[0];
                    String pluginVersion = pluginInfo[1];
                    Plugin dependentPlugin = new Plugin(pluginName, pluginVersion, null, null);
                    if (useLatestSpecified && plugin.isLatest() || useLatestAll) {
                        VersionNumber latestPluginVersion = getLatestPluginVersion(pluginName);
                        dependentPlugin.setVersion(latestPluginVersion);
                        dependentPlugin.setLatest(true);
                    }

                    dependentPlugins.add(dependentPlugin);
                    dependentPlugin.setParent(plugin);
                }
            }
            logVerbose(dependentPlugins.isEmpty() ? String.format("%n%s has no dependencies", plugin.getName()) :
                    String.format("%n%s depends on: %n", plugin.getName()) +
                            dependentPlugins.stream()
                                    .map(p -> p.getName() + " " + p.getVersion())
                                    .collect(Collectors.joining("\n")));

            plugin.setJenkinsVersion(getAttributeFromManifest(tempFile, "Jenkins-Version"));
            Files.delete(tempFile.toPath());
            return dependentPlugins;
        } catch (IOException e) {
            System.out.println(String.format("Unable to resolve dependencies for %s", plugin.getName()));
            return dependentPlugins;
        }
    }

    /**
     * Given a plugin and json that contains plugin information, determines the dependencies and returns the list of
     * dependencies. Optional dependencies will be excluded.
     *
     * @param plugin     for which to find dependencies
     * @param pluginJson json that will be parsed to find requested plugin's dependencies
     * @return list of plugin's dependencies, or null if dependencies are unable to be determined
     */
    public List<Plugin> resolveDependenciesFromJson(Plugin plugin, JSONObject pluginJson) {
        JSONArray dependencies = getPluginDependencyJsonArray(plugin, pluginJson);
        List<Plugin> dependentPlugins = new ArrayList<>();

        if (dependencies == null) {
            return null;
        }

        for (int i = 0; i < dependencies.length(); i++) {
            JSONObject dependency = dependencies.getJSONObject(i);
            boolean isPluginOptional = dependency.getBoolean("optional");
            if (!isPluginOptional) {
                String pluginName = dependency.getString("name");
                String pluginVersion = dependency.getString("version");
                Plugin dependentPlugin = new Plugin(pluginName, pluginVersion, null, null);
                if (useLatestSpecified && plugin.isLatest() || useLatestAll) {
                    VersionNumber latestPluginVersion = getLatestPluginVersion(pluginName);
                    dependentPlugin.setVersion(latestPluginVersion);
                    dependentPlugin.setLatest(true);
                }
                dependentPlugins.add(dependentPlugin);
                dependentPlugin.setParent(plugin);
            }
        }

        logVerbose(dependentPlugins.isEmpty() ? String.format("%n%s has no dependencies", plugin.getName()) :
                String.format("%n%s depends on: %n", plugin.getName()) +
                        dependentPlugins.stream()
                                .map(p -> p.getName() + " " + p.getVersion())
                                .collect(Collectors.joining("\n")));

        return dependentPlugins;
    }

    /**
     * Finds the dependencies for plugins by either resolving the information from the manifest or update center json.
     * If the requested plugin has a url from which it will be downloaded (by default if a plugin has a url, that will
     * override using the version to download the plugin), nothing is done to try to determine the plugin
     * version which might be used to find dependency information in the update center json; instead, the plugin
     * manifest is used to find the dependencies. Similarly, incremental plugins don't have their dependencies listed
     * anywhere, so the plugin manifest will also be used for these. If a plugin's dependencies can be determined by
     * looking at update center json, it will. If that fails, the manifest will be used.
     *
     * @param plugin for which to find and download dependencies
     * @return plugin's list of direct dependencies
     */
    public List<Plugin> resolveDirectDependencies(Plugin plugin) {
        List<Plugin> dependentPlugins;
        String version = plugin.getVersion().toString();
        if (!StringUtils.isEmpty(plugin.getUrl()) || !StringUtils.isEmpty(plugin.getGroupId())) {
            dependentPlugins = resolveDependenciesFromManifest(plugin);
            return dependentPlugins;
        } else if (version.equals("latest")) {
            dependentPlugins = resolveDependenciesFromJson(plugin, latestUcJson);
        } else if (version.equals("experimental")) {
            dependentPlugins = resolveDependenciesFromJson(plugin, experimentalUcJson);
        } else {
            dependentPlugins = resolveDependenciesFromJson(plugin, pluginInfoJson);
        }
        if (dependentPlugins == null) {
            return resolveDependenciesFromManifest(plugin);
        }

        return dependentPlugins;
    }

    /**
     * Finds all recursive dependencies for a given plugin. If the same plugin is required by different plugins, the
     * highest required version will be taken
     *
     * @param plugin to resolve dependencies for
     * @return map of plugin names and plugins representing all of the dependencies of the requested plugin, including
     * the requested plugin itself
     */
    public Map<String, Plugin> resolveRecursiveDependencies(Plugin plugin) {
        Deque<Plugin> queue = new LinkedList<>();
        Map<String, Plugin> recursiveDependencies = new HashMap<>();
        queue.add(plugin);
        recursiveDependencies.put(plugin.getName(), plugin);

        while (queue.size() != 0) {
            Plugin dependency = queue.poll();

            if (dependency.getDependencies().isEmpty()) {
                dependency.setDependencies(resolveDirectDependencies(dependency));
            }

            for (Plugin p : dependency.getDependencies()) {
                String dependencyName = p.getName();
                if (!recursiveDependencies.containsKey(dependencyName)) {
                    recursiveDependencies.put(dependencyName, p);
                    queue.add(p);
                } else {
                    Plugin existingDependency = recursiveDependencies.get(dependencyName);
                    if (existingDependency.getVersion().isOlderThan(p.getVersion())) {
                        outputPluginReplacementInfo(existingDependency, p);
                        queue.add(p); //in case the higher version contains dependencies the lower version didn't have
                        recursiveDependencies.replace(dependencyName, existingDependency, p);
                    }
                }
            }
        }
        return recursiveDependencies;
    }

    /**
     * Downloads a plugin, skipping if already installed or bundled in the war. A plugin's dependencies will be
     * resolved after the plugin is downloaded.
     *
     * @param plugin   to download
     * @param location location to download plugin to. If location is set to null, will download to the plugin folder
     *                 otherwise will download to the temporary location specified.
     * @return boolean signifying if plugin was successful
     */
    public boolean downloadPlugin(Plugin plugin, File location) {
        String pluginName = plugin.getName();
        VersionNumber pluginVersion = plugin.getVersion();
        // location will be populated if downloading a plugin to a temp file to determine dependencies
        // even if plugin is already downloaded, still want to download the temp file to parse dependencies to ensure
        // that all dependencies are also installed
        if (location == null && installedPluginVersions.containsKey(pluginName) &&
                installedPluginVersions.get(pluginName).getVersion().isNewerThanOrEqualTo(pluginVersion)) {
            logVerbose(pluginName + " already installed, skipping");
            return true;
        }
        String pluginDownloadUrl = getPluginDownloadUrl(plugin);
        boolean successfulDownload = downloadToFile(pluginDownloadUrl, plugin, location);
        if (!successfulDownload) {
            logVerbose(String.format("First download attempt of %s unsuccessful, reattempting",
                    plugin.getName()));
            //some plugin don't follow the rules about artifact ID, i.e. docker-plugin
            String newPluginName = plugin.getName() + "-plugin";
            plugin.setName(newPluginName);
            pluginDownloadUrl = getPluginDownloadUrl(plugin);
            successfulDownload = downloadToFile(pluginDownloadUrl, plugin, location);
        }
        if (successfulDownload && location == null) {
            System.out.println(String.format("%s downloaded successfully", plugin.getName()));
            installedPluginVersions.put(plugin.getName(), plugin);
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
            logVerbose(String.format("Will use url: %s to download %s plugin", pluginUrl, plugin.getName()));
            urlString = pluginUrl;
        } else if ((pluginVersion.equals("latest") || plugin.isLatest()) && !StringUtils.isEmpty(jenkinsUcLatest)) {
            urlString = appendPathOntoUrl(dirName(jenkinsUcLatest), "/latest", pluginName + ".hpi");
        } else if (pluginVersion.equals("experimental") || plugin.isExperimental()) {
            urlString = appendPathOntoUrl(dirName(cfg.getJenkinsUcExperimental()), "/latest", pluginName + ".hpi");
        } else if (!StringUtils.isEmpty(plugin.getGroupId())) {
            String groupId = plugin.getGroupId();
            groupId = groupId.replace(".", "/");
            String incrementalsVersionPath =
                    String.format("%s/%s/%s-%s.hpi", pluginName, pluginVersion, pluginName, pluginVersion);
            urlString =
                    appendPathOntoUrl(cfg.getJenkinsIncrementalsRepoMirror(), groupId, incrementalsVersionPath);
        } else {
            urlString = appendPathOntoUrl(removePath(cfg.getJenkinsUc()), "/download/plugins", pluginName, pluginVersion, pluginName + ".hpi");
        }
        return urlString;
    }

    /**
     * Downloads a plugin from a url to a file.
     *
     * @param urlString    String url to download the plugin from
     * @param plugin       Plugin object representing plugin to be downloaded
     * @param fileLocation contains the temp file if the plugin is downloaded so that dependencies can be parsed from
     *                     the manifest, if the plugin will be downloaded to the plugin download location, this will
     *                     be null
     * @return true if download is successful, false otherwise
     */
    public boolean downloadToFile(String urlString, Plugin plugin, File fileLocation) {
        return downloadToFile(urlString, plugin, fileLocation, DEFAULT_MAX_RETRIES);
    }

    /**
     * Downloads a plugin from a url to a file.
     *
     * @param urlString    String url to download the plugin from
     * @param plugin       Plugin object representing plugin to be downloaded
     * @param fileLocation contains the temp file if the plugin is downloaded so that dependencies can be parsed from
     *                     the manifest, if the plugin will be downloaded to the plugin download location, this will
     *                     be null
     * @param maxRetries   Maximum number of times to retry the download before failing
     * @return true if download is successful, false otherwise
     */
    @SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE")
    public boolean downloadToFile(String urlString, Plugin plugin, File fileLocation, int maxRetries) {
        File pluginFile;
        if (fileLocation == null) {
            pluginFile = new File(refDir, plugin.getArchiveFileName());
            System.out.println("\nDownloading plugin " + plugin.getName() + " from url: " + urlString);
        } else {
            pluginFile = fileLocation;
        }

        boolean success = true;
        for (int i = 0; i < maxRetries; i++) {
            try {
                if (pluginFile.exists()) {
                    Files.delete(pluginFile.toPath());
                }
            } catch (IOException) {
                logVerbose(String.format("Unable to delete %s before retry %d",pluginFile,i+1);
            }

            try (CloseableHttpClient httpclient = HttpClients.custom().useSystemProperties()
                    .addInterceptorLast((HttpRequestInterceptor) (request, context) -> {
                        throw new IOException("Retry on any failure");
                    })
                    .setRetryHandler(new DefaultHttpRequestRetryHandler(maxRetries, false))
                    .build()) {
                HttpClientContext context = HttpClientContext.create();
                HttpHead httphead = new HttpHead(urlString);
                try (CloseableHttpResponse response = httpclient.execute(httphead, context)) {
                    HttpHost target = context.getTargetHost();
                    List<URI> redirectLocations = context.getRedirectLocations();
                    // Expected to be an absolute URI
                    URI location = URIUtils.resolve(httphead.getURI(), target, redirectLocations);
                    FileUtils.copyURLToFile(location.toURL(), pluginFile);
                } catch (URISyntaxException | IOException e) {
                    logVerbose(String.format("Unable to resolve plugin URL %s, or download plugin %s to file",
                            urlString, plugin.getName()));
                    success = false;
                }
            } catch (IOException e) {
                System.out.println("Unable to create HTTP connection to download plugin");
                success = false;
            }

            // Check integrity of plugin file
            if (success) {
                try (JarFile pluginJpi = new JarFile(pluginFile)) {
                    plugin.setFile(pluginFile);
                } catch (IOException e) {
                    System.out.println("Downloaded file for " + plugin.getName() + " is not a valid ZIP");
                    success = false;
                }
            }

            // both the download and zip validation passed
            if(success) {
                break;
            }
        }

        if (!success) {
            failedPlugins.add(plugin);
        }
        return success;
    }

    /**
     * Given a jar file and a key to retrieve from the jar's MANIFEST.MF file, confirms that the file is a jar returns
     * the value matching the key
     *
     * @param file jar file to get manifest from
     * @param key  key matching value to retrieve
     * @return value matching the key in the jar file
     */
    public String getAttributeFromManifest(File file, String key) {
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
        String version = getAttributeFromManifest(jenkinsWarFile, "Jenkins-Version");
        if (StringUtils.isEmpty(version)) {
            System.out.println("Unable to get version from war file");
            return null;
        }
        logVerbose("Jenkins version: " + version);
        return new VersionNumber(version);
    }

    /**
     * Finds the plugin version by reading the manifest of a .hpi or .jpi file
     *
     * @param file plugin .hpi or .jpi of which to get the version
     * @return plugin version
     */
    public String getPluginVersion(File file) {
        String version = getAttributeFromManifest(file, "Plugin-Version");
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
    public Map<String, Plugin> installedPlugins() {
        Map<String, Plugin> installedPlugins = new HashMap<>();
        FileFilter fileFilter = new WildcardFileFilter("*.jpi");

        // Only lists files in same directory, does not list files recursively
        File[] files = refDir.listFiles(fileFilter);

        if (files != null) {
            for (File file : files) {
                String pluginName = FilenameUtils.getBaseName(file.getName());
                installedPlugins.put(pluginName, new Plugin(pluginName, getPluginVersion(file), null, null));
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
    @SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE")
    public Map<String, Plugin> bundledPlugins() {
        Map<String, Plugin> bundledPlugins = new HashMap<>();

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
                for (Iterator<Path> it = walk.iterator(); it.hasNext(); ) {
                    Path file = it.next();
                    if (matcher.matches(file)) {
                        Path fileName = file.getFileName();
                        if (fileName != null) {
                            // Because can't convert a ZipPath to a file with file.toFile()
                            InputStream in = Files.newInputStream(file);
                            final Path tempFile = Files.createTempFile("PREFIX", "SUFFIX");
                            try (FileOutputStream out = new FileOutputStream(tempFile.toFile())) {
                                IOUtils.copy(in, out);
                            }

                            String pluginVersion = getPluginVersion(tempFile.toFile());

                            Files.delete(tempFile);
                            String pluginName = FilenameUtils.getBaseName(fileName.toString());
                            bundledPlugins
                                    .put(pluginName, new Plugin(pluginName, pluginVersion, null, null));
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

    /**
     * Sets the installed plugins
     *
     * @param installedPlugins hashmap of plugin names and versions of plugins that already exist in the plugin
     *                         download directory
     */
    public void setInstalledPluginVersions(Map<String, Plugin> installedPlugins) {
        installedPluginVersions = installedPlugins;
    }

    /**
     * Sets the bundled plugins
     *
     * @param bundledPlugins hashmap of plugin names and version numbers representing the bundled plugins
     */
    public void setBundledPluginVersions(Map<String, Plugin> bundledPlugins) {
        bundledPluginVersions = bundledPlugins;
    }

    /**
     * Sets all plugins and their recursive dependencies
     *
     * @param allPluginsAndDependencies map of plugin name - plugin pairs corresponding the requested plugins and their
     *                                  recursive dependencies
     */
    public void setAllPluginsAndDependencies(Map<String, Plugin> allPluginsAndDependencies) {
        this.allPluginsAndDependencies = allPluginsAndDependencies;
    }

    /**
     * Sets the list of effective plugins
     *
     * @param effectivePlugins map of plugin name - plugin pairs corresponding to the highest required versions of
     *                         the requested plugins, their dependencies, and the already installed plugins
     */
    public void setEffectivePlugins(Map<String, Plugin> effectivePlugins) {
        this.effectivePlugins = effectivePlugins;
    }

    /**
     * Sets the list of plugins to be downloaded
     *
     * @param pluginsToBeDownloaded list of all plugins that will actually be downloaded after plugin dependencies have
     *                              been resolved and already installed plugins are taken into consideration
     */
    public void setPluginsToBeDownloaded(List<Plugin> pluginsToBeDownloaded) {
        this.pluginsToBeDownloaded = pluginsToBeDownloaded;
    }

    /**
     * Outputs inforation to the console if verbose option was set to true
     *
     * @param message informational string to output
     */
    public void logVerbose(String message) {
        if (verbose) {
            System.out.println(message);
        }
    }

    /**
     * Sets the latest update center json
     *
     * @param latestUcJson json to set latest update center to
     */
    public void setLatestUcJson(JSONObject latestUcJson) {
        this.latestUcJson = latestUcJson;
    }

    /**
     * Sets the json object containing latest plugin information
     * @param latestPlugins JSONObject containing info for latest plugins
     */
    public void setLatestUcPlugins(JSONObject latestPlugins) {
        this.latestPlugins = latestPlugins;
    }

    /**
     * Sets the security warnings
     *
     * @param securityWarnings map of the plugin name to the list of security warnings for that plugin
     */
    public void setAllSecurityWarnings(Map<String, List<SecurityWarning>> securityWarnings) {
        allSecurityWarnings = securityWarnings;
    }

    /**
     * Sets the plugin version json
     *
     * @param pluginInfoJson json to set plugin version info to
     */
    public void setPluginInfoJson(JSONObject pluginInfoJson) {
        this.pluginInfoJson = pluginInfoJson;
    }


    /**
     * Gets the list of failed plugins
     *
     * @return list of failed plugins
     */
    public List<Plugin> getFailedPlugins() {
        return failedPlugins;
    }
}
