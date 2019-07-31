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
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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
    private List<Plugin> failedPlugins;
    private File refDir;
    private String jenkinsUcLatest;
    private VersionNumber jenkinsVersion;
    private File jenkinsWarFile;
    private Map<String, VersionNumber> installedPluginVersions;
    private Map<String, VersionNumber> bundledPluginVersions;
    private Map<String, List<SecurityWarning>> allSecurityWarnings;
    private Map<String, Plugin> allPluginsAndDependencies;
    private Map<String, Plugin> effectivePlugins;
    private List<Plugin> pluginsToBeDownloaded;
    private Config cfg;
    private JSONObject latestUcJson;
    private JSONObject experimentalUcJson;
    private JSONObject pluginInfoJson;
    private boolean verbose;

    public static final String SEPARATOR = File.separator;

    public PluginManager(Config cfg) {
        this.cfg = cfg;
        refDir = cfg.getPluginDir();
        jenkinsWarFile = new File(cfg.getJenkinsWar());
        failedPlugins = new ArrayList();
        installedPluginVersions = new HashMap<>();
        bundledPluginVersions = new HashMap<>();
        allSecurityWarnings = new HashMap<>();
        allPluginsAndDependencies = new HashMap<>();
        verbose = cfg.isVerbose();
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
        bundledPluginVersions = bundledPlugins();
        installedPluginVersions = installedPlugins();

        allPluginsAndDependencies = findPluginsAndDependencies(cfg.getPlugins());
        pluginsToBeDownloaded = findPluginsToDownload(allPluginsAndDependencies);
        effectivePlugins = findEffectivePlugins(pluginsToBeDownloaded);

        listPlugins();
        showSpecificSecurityWarnings(pluginsToBeDownloaded);
        showAvailableUpdates(pluginsToBeDownloaded);

        if (cfg.doDownload()) {
            downloadPlugins(pluginsToBeDownloaded);
            outputFailedPlugins();
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
                installedVersion = installedPluginVersions.get(pluginName);
            } else if (bundledPluginVersions.containsKey(pluginName)) {
                installedVersion = bundledPluginVersions.get(pluginName);
            } else if (bundledPluginVersions.containsKey(pluginName) &&
                    installedPluginVersions.containsKey(pluginName)) {
                installedVersion = bundledPluginVersions.get(pluginName).
                        compareTo(installedPluginVersions.get(pluginName)) > 0 ?
                        bundledPluginVersions.get(pluginName) : installedPluginVersions.get(pluginName);
            }
            if (installedVersion == null) {
                pluginsToDownload.add(plugin);
            } else if (installedVersion.compareTo(plugin.getVersion()) < 0) {
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

        for (Map.Entry<String, VersionNumber> installedEntry : installedPluginVersions.entrySet()) {
            if (!effectivePlugins.containsKey(installedEntry.getKey())) {
                effectivePlugins.put(installedEntry.getKey(),
                        new Plugin(installedEntry.getKey(), installedEntry.getValue().toString(), null, null));
            } else if (
                    (effectivePlugins.get(installedEntry.getKey()).getVersion()).compareTo(installedEntry.getValue()) <
                            0) {
                effectivePlugins.replace(installedEntry.getKey(),
                        new Plugin(installedEntry.getKey(), installedEntry.getValue().toString(), null, null));
            }
        }

        for (Map.Entry<String, VersionNumber> bundledEntry : bundledPluginVersions.entrySet()) {
            if (!effectivePlugins.containsKey(bundledEntry.getKey())) {
                effectivePlugins.put(bundledEntry.getKey(),
                        new Plugin(bundledEntry.getKey(), bundledEntry.getValue().toString(), null, null));
            } else if (effectivePlugins.get(bundledEntry.getKey()).getVersion().compareTo(bundledEntry.getValue()) <
                    0) {
                effectivePlugins.replace(bundledEntry.getKey(),
                        new Plugin(bundledEntry.getKey(), bundledEntry.getValue().toString(), null, null));
            }
        }
        return effectivePlugins;
    }

    /**
     * Lists installed plugins, bundled plugins, set of all recurively determined requested plugins, which plugins will
     * actually be downloaded based on the requested plugins and currently installed plugins, and the effective plugin
     * set, which includes all currently installed plugins and plugins that will be downloaded by the tool
     */
    public void listPlugins() {
        if (cfg.isShowPluginsToBeDownloaded()) {
            System.out.println("\nInstalled plugins:");
            for (Map.Entry<String, VersionNumber> installedPlugin : installedPluginVersions.entrySet()) {
                System.out.println(installedPlugin.getKey() + ": " + installedPlugin.getValue());
            }

            System.out.println("\nBundled plugins:");
            for (Map.Entry<String, VersionNumber> bundledPlugin : bundledPluginVersions.entrySet()) {
                System.out.println(bundledPlugin.getKey() + ": " + bundledPlugin.getValue());
            }

            System.out.println("\nSet of all requested plugins:");
            for (Plugin requestedPlugin : allPluginsAndDependencies.values()) {
                System.out.println(requestedPlugin.getName() + ": " + requestedPlugin.getVersion());
            }

            System.out.println("\nSet of all requested plugins that will be downloaded:");
            for (Plugin plugin : pluginsToBeDownloaded) {
                System.out.println(plugin.getName() + ": " + plugin.getVersion());
            }

            System.out.println("\nSet of all existing plugins and plugins that will be downloaded:");
            for (Plugin plugin : effectivePlugins.values()) {
                System.out.println(plugin.getName() + ": " + plugin.getVersion());
            }
        }
    }

    /**
     * Gets the security warnings for plugins from the update center json and creates a list of all the security
     * warnings
     */
    public Map<String, List<SecurityWarning>> getSecurityWarnings() {
        if (latestUcJson == null) {
            System.out.println("Unable to get update center json");
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
                String lastVersion = "";
                if (warningVersion.has("lastVersion")) {
                    lastVersion = warningVersion.getString("lastVersion");
                }
                String pattern = warningVersion.getString("pattern");
                securityWarning.addSecurityVersion(lastVersion, pattern);
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
            for (List<SecurityWarning> securityWarningList : allSecurityWarnings.values()) {
                for (SecurityWarning securityWarning : securityWarningList) {
                    System.out.println(securityWarning.getName() + " - " + securityWarning.getMessage());
                }
            }
        }
    }


    /**
     * Prints out security warning information for a list of plugins if isShowWarnings is set to true in the config
     * file
     *
     * @param plugins
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
                JSONObject pluginsJson = latestUcJson.getJSONObject("plugins");
                JSONObject pluginInfo = (JSONObject) pluginsJson.get(plugin.getName());
                VersionNumber latestVersion = new VersionNumber(pluginInfo.getString("version"));
                if (plugin.getVersion().compareTo(latestVersion) < 0) {
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
        plugins.parallelStream().forEach(plugin -> {
            boolean successfulDownload = downloadPlugin(plugin, null);
            if (!successfulDownload) {
                System.out.println("Unable to download " + plugin.getName() + ". Skipping...");
                failedPlugins.add(plugin);
            }
        });
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
                    if (existingDependency.getVersion().compareTo(dependencyVersion) < 0) {
                        logVerbose(String.format(
                                "Version of %s (%s) required by %s (%s) is lower than the version required (%s) " +
                                        "by %s (%s), upgrading required plugin version",
                                dependencyName,
                                existingDependency.getVersion().toString(),
                                existingDependency.getParent().getName(),
                                existingDependency.getParent().getVersion().toString(),
                                dependencyVersion.toString(),
                                dependentPlugin.getParent().getName(),
                                dependentPlugin.getParent().getVersion().toString()));

                        allPluginDependencies.replace(existingDependency.getName(), dependentPlugin);
                    }
                }
            }
        }
        return allPluginDependencies;
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
        logVerbose("\nRetrieving update center information");
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
        if (!plugins.has(plugin.getName())) {
            return null;
        }

        if (!plugins.has(plugin.getName())) {
            return null;
        }
        JSONObject pluginInfo = (JSONObject) plugins.get(plugin.getName());

        if (ucJson.equals(pluginInfoJson)) {
            //plugin-versions.json has a slightly different structure than other update center json
            if (pluginInfo.has(plugin.getVersion().toString())) {
                JSONObject specificVersionInfo = pluginInfo.getJSONObject(plugin.getVersion().toString());
                return (JSONArray) specificVersionInfo.get("dependencies");
            }
        } else {
            //plugin version is latest or experimental
            String version = pluginInfo.getString("version");
            plugin.setVersion(new VersionNumber(version));
            return (JSONArray) pluginInfo.get("dependencies");
        }
        return null;
    }

    /**
     * Resolves direct dependencies from downloaded plugin manifest. Done for plugins in which dependencies can't be
     * determined via easily via json, such as when a user downloads a plugin directly from url or incremental plugins,
     * or in other cases when getting information from json fails
     *
     * @param plugin plugin to resolve direct dependencies for
     */
    public List<Plugin> resolveDependenciesFromManifest(Plugin plugin) {
        List<Plugin> dependentPlugins = new ArrayList<>();
        try {
            File tempFile = Files.createTempFile(plugin.getName(), ".jpi").toFile();
            logVerbose(
                    String.format("%nResolving dependencies of %s by downloading plugin to temp file %s and parsing " +
                            "MANIFEST.MF", plugin.getName(), tempFile.toString()));
            if (!downloadPlugin(plugin, tempFile)) {
                System.out.println("Unable to resolve dependencies for " + plugin.getName());
                Files.delete(tempFile.toPath());
                return dependentPlugins;
            }

            if (plugin.getVersion().toString().equals("latest") || plugin.getVersion().toString().equals("experimental")) {
                String version = getAttributefromManifest(tempFile, "Plugin-Version");
                plugin.setVersion(new VersionNumber(version));
            }

            String dependencyString = getAttributefromManifest(tempFile, "Plugin-Dependencies");

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
                    Plugin dependentPlugin = new Plugin(pluginName, pluginVersion, false);
                    dependentPlugins.add(dependentPlugin);
                }
            }
            logVerbose(dependentPlugins.isEmpty() ? String.format("%n%s has no dependencies", plugin.getName()) :
                    String.format("%n%s depends on: %n", plugin.getName()) +
                            dependentPlugins.stream()
                                    .map(p -> p.getName() + " " + p.getVersion())
                                    .collect(Collectors.joining("\n")));
            Files.delete(tempFile.toPath());
            return dependentPlugins;
        } catch (IOException e) {
            System.out.println(String.format("Unable to resolve dependencies for %s", plugin.getName()));
            return dependentPlugins;
        }
    }

    /**
     * Finds the dependencies for a plugins by using the update center plugin-versions json. Excludes optional
     * dependencies
     *
     * @param plugin for which to find and download dependencies
     */
    public List<Plugin> resolveDirectDependencies(Plugin plugin) {
        List<Plugin> dependentPlugins = new ArrayList<>();
        JSONArray dependencies = null;
        String version = plugin.getVersion().toString();
        if (!StringUtils.isEmpty(plugin.getUrl()) || version.contains("incrementals")) {
            dependentPlugins = resolveDependenciesFromManifest(plugin);
        } else if (version.equals("latest")) {
            dependencies = getPluginDependencyJsonArray(plugin, latestUcJson);
        } else if (version.equals("experimental")) {
            dependencies = getPluginDependencyJsonArray(plugin, experimentalUcJson);
        } else {
            dependencies = getPluginDependencyJsonArray(plugin, pluginInfoJson);
        }
        if (dependencies == null) {
            resolveDependenciesFromManifest(plugin);
            return dependentPlugins;
        }

        for (int i = 0; i < dependencies.length(); i++) {
            JSONObject dependency = dependencies.getJSONObject(i);
            boolean isPluginOptional = dependency.getBoolean("optional");
            if (!isPluginOptional) {
                String pluginName = dependency.getString("name");
                String pluginVersion = dependency.getString("version");
                Plugin dependentPlugin = new Plugin(pluginName, pluginVersion, isPluginOptional);
                dependentPlugins.add(dependentPlugin);
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
                    if (existingDependency.getVersion().compareTo(p.getVersion()) < 0) {
                        logVerbose(String.format("Version of %s (%s) required by %s (%s) is lower than the " +
                                        "version required (%s) by %s (%s), upgrading required plugin version",
                                dependencyName, existingDependency.getVersion().toString(),
                                existingDependency.getParent().getName(),
                                existingDependency.getParent().getVersion().toString(),
                                p.getVersion().toString(),
                                p.getParent().getName(),
                                p.getParent().getVersion().toString()));

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
     * @param plugin to download
     * @return boolean signifying if plugin was successful
     */
    public boolean downloadPlugin(Plugin plugin, File location) {
        String pluginName = plugin.getName();
        VersionNumber pluginVersion = plugin.getVersion();
        // location will be populated if downloading a plugin to a temp file to determine dependencies
        // even if plugin is already downloaded, still want to download the temp file to parse dependencies to ensure
        // that all dependencies are also installed
        if (location == null && installedPluginVersions.containsKey(pluginName) &&
                installedPluginVersions.get(pluginName).compareTo(pluginVersion) == 0) {
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
            installedPluginVersions.put(plugin.getName(), pluginVersion);
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
            urlString = String.format("%s/latest/%s.hpi", jenkinsUcLatest, pluginName);
        } else if (pluginVersion.equals("experimental") || plugin.isExperimental()) {
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
        File pluginFile;
        if (fileLocation == null) {
            pluginFile = new File(refDir + SEPARATOR + plugin.getArchiveFileName());
            System.out.println("\nDownloading plugin " + plugin.getName() + " from url: " + urlString);
        } else {
            pluginFile = fileLocation;
        }
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
                logVerbose(String.format("Unable to resolve plugin URL %s, or download plugin %s to file",
                        urlString, plugin.getName()));
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

    /**
     * Given a jar file and a key to retrieve from the jar's MANIFEST.MF file, confirms that the file is a jar returns
     * the value matching the key
     *
     * @param file jar file to get manifest from
     * @param key  key matching value to retrieve
     * @return value matching the key in the jar file
     */
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
    public Map<String, VersionNumber> installedPlugins() {
        Map<String, VersionNumber> installedPlugins = new HashMap<>();
        FileFilter fileFilter = new WildcardFileFilter("*.jpi");

        // Only lists files in same directory, does not list files recursively
        File[] files = refDir.listFiles(fileFilter);

        if (files != null) {
            for (File file : files) {
                String pluginName = FilenameUtils.getBaseName(file.getName());
                VersionNumber pluginVersion = new VersionNumber(getPluginVersion(file));
                installedPlugins.put(pluginName, pluginVersion);
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
    public Map<String, VersionNumber> bundledPlugins() {
        Map<String, VersionNumber> bundledPlugins = new HashMap<>();

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

                            VersionNumber pluginVersion = new VersionNumber(getPluginVersion(tempFile.toFile()));

                            Files.delete(tempFile);
                            bundledPlugins
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

    /**
     * Sets the installed plugins
     *
     * @param installedPlugins hashmap of plugin names and versions of plugins that already exist in the plugin
     *                         download directory
     */
    public void setInstalledPluginVersions(Map<String, VersionNumber> installedPlugins) {
        installedPluginVersions = installedPlugins;
    }

    /**
     * Sets the bundled plugins
     *
     * @param bundledPlugins hashmap of plugin names and version numbers representing the bundled plugins
     */
    public void setBundledPluginVersions(Map<String, VersionNumber> bundledPlugins) {
        bundledPluginVersions = bundledPlugins;
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
     * Sets the plugin version json
     *
     * @param pluginInfoJson json to set plugin version info to
     */
    public void setPluginInfoJson(JSONObject pluginInfoJson) {
        this.pluginInfoJson = pluginInfoJson;
    }
}
