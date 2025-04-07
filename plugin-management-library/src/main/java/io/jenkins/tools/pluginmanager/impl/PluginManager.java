package io.jenkins.tools.pluginmanager.impl;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.util.VersionNumber;
import io.jenkins.tools.pluginmanager.config.Config;
import io.jenkins.tools.pluginmanager.config.Credentials;
import io.jenkins.tools.pluginmanager.config.HashFunction;
import io.jenkins.tools.pluginmanager.config.LogOutput;
import io.jenkins.tools.pluginmanager.parsers.PluginOutputConverter;
import io.jenkins.tools.pluginmanager.parsers.StdOutPluginOutputConverter;
import io.jenkins.tools.pluginmanager.parsers.TxtOutputConverter;
import io.jenkins.tools.pluginmanager.parsers.YamlPluginOutputConverter;
import io.jenkins.tools.pluginmanager.util.FileDownloadResponseHandler;
import io.jenkins.tools.pluginmanager.util.ManifestTools;
import java.io.Closeable;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.json.JSONArray;
import org.json.JSONObject;

import static io.jenkins.tools.pluginmanager.util.PluginManagerUtils.appendPathOntoUrl;
import static io.jenkins.tools.pluginmanager.util.PluginManagerUtils.dirName;
import static io.jenkins.tools.pluginmanager.util.PluginManagerUtils.removePath;
import static io.jenkins.tools.pluginmanager.util.PluginManagerUtils.removePossibleWrapperText;

public class PluginManager implements Closeable {
    private static final VersionNumber LATEST = new VersionNumber(Plugin.LATEST);
    private final List<Plugin> failedPlugins;
    /**
     * Directory where the plugins will be downloaded
     */
    private final File pluginDir;
    private String jenkinsUcLatest;
    private HashFunction hashFunction;
    private final @CheckForNull VersionNumber jenkinsVersion;
    private final @CheckForNull File jenkinsWarFile;
    private Map<String, Plugin> installedPluginVersions;
    private Map<String, Plugin> bundledPluginVersions;
    private Map<String, List<SecurityWarning>> allSecurityWarnings;
    private Map<String, Plugin> allPluginsAndDependencies;
    private Map<String, Plugin> effectivePlugins;
    private List<Plugin> pluginsToBeDownloaded;
    private final Config cfg;
    private JSONObject latestUcJson;
    private JSONObject experimentalUcJson;
    private JSONObject pluginInfoJson;
    private JSONObject latestPlugins;
    private JSONObject experimentalPlugins;
    private final boolean verbose;
    private final boolean useLatestSpecified;
    private final boolean useLatestAll;
    private final String userAgentInformation;
    private final boolean skipFailedPlugins;
    private CloseableHttpClient httpClient;
    private final CacheManager cm;
    private final LogOutput logOutput;

    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final String MIRROR_FALLBACK_BASE_URL = "https://archives.jenkins.io/";

    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "we want the user to be able to specify a path")
    public PluginManager(Config cfg) {
        this.cfg = cfg;
        logOutput = cfg.getLogOutput();
        pluginDir = cfg.getPluginDir();
        jenkinsVersion = cfg.getJenkinsVersion();
        final String warArg = cfg.getJenkinsWar();
        jenkinsWarFile = warArg != null ? new File(warArg) : null;
        failedPlugins = new ArrayList<>();
        installedPluginVersions = new HashMap<>();
        bundledPluginVersions = new HashMap<>();
        allSecurityWarnings = new HashMap<>();
        allPluginsAndDependencies = new HashMap<>();
        verbose = cfg.isVerbose();
        jenkinsUcLatest = cfg.getJenkinsUc().toString();
        useLatestSpecified = cfg.isUseLatestSpecified();
        useLatestAll = cfg.isUseLatestAll();
        skipFailedPlugins = cfg.isSkipFailedPlugins();
        hashFunction = cfg.getHashFunction();
        httpClient = null;
        userAgentInformation = this.getUserAgentInformation();
        cm = new CacheManager(cfg.getCachePath(), cfg.getLogOutput());
    }

    private String getUserAgentInformation() {
        String userAgentInformation = "JenkinsPluginManager";
        Properties properties = new Properties();
        try (InputStream propertiesStream = this.getClass().getClassLoader().getResourceAsStream("version.properties")) {
            properties.load(propertiesStream);
            userAgentInformation =  "JenkinsPluginManager/" + properties.getProperty("project.version");
        } catch (IOException e) {
            logVerbose("Not able to load/detect version.properties file");
        }

        String additionalUserAgentInfo = System.getProperty("http.agent");
        if (additionalUserAgentInfo != null) {
            userAgentInformation = additionalUserAgentInfo + " " + userAgentInformation;
        }

        return userAgentInformation;
    }

    private HttpClient getHttpClient() {
        if (httpClient == null) {
            RequestConfig globalConfig = RequestConfig.custom()
                .setCookieSpec(CookieSpecs.STANDARD) // use modern cookie policy (RFC 6265)
                .build();
            httpClient = HttpClients.custom().useSystemProperties()
                // there is a more complex retry handling in downloadToFile(...) on the whole flow
                // this affects only the single request
                .setRetryHandler(new DefaultHttpRequestRetryHandler(DEFAULT_MAX_RETRIES, true))
                .setConnectionManager(new PoolingHttpClientConnectionManager())
                .setUserAgent(userAgentInformation)
                .setDefaultRequestConfig(globalConfig)
                .build();
        }
        return httpClient;
    }

    /**
     * Drives the process to download plugins. Calls methods to find installed plugins, download plugins, and output
     * the failed plugins
     */
    public void start() {
        start(true);
    }

    /**
     * Drives the process to download plugins.
     * Calls methods to find installed plugins, download plugins, and output the failed plugins.
     *
     * @param downloadUc {@code false} to disable Update Center and other external resources download.
     *                   In such case the update center metadata should be provided by API.
     * @since TODO
     */
    public void start(boolean downloadUc) {
        if (cfg.isCleanPluginDir() && pluginDir.exists()) {
            try {
                logVerbose("Cleaning up the target plugin directory: " + pluginDir);
                File[] toBeDeleted = pluginDir.listFiles();
                if (toBeDeleted != null) {
                    for (File deletableFile : toBeDeleted) {
                        FileUtils.forceDelete(deletableFile);
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Unable to delete: " + pluginDir.getAbsolutePath(), e);
            }
        }
        if (cfg.doDownload() && !pluginDir.exists()) {
            createPluginDir(cfg.isCleanPluginDir());
        }

        if (useLatestSpecified && useLatestAll) {
            throw new PluginDependencyStrategyException("Only one plugin dependency version strategy can be selected " +
                    "at a time");
        }

        VersionNumber jenkinsVersion = getJenkinsVersion();
        if (downloadUc) {
            getUCJson(jenkinsVersion);
        }
        getSecurityWarnings();
        showAllSecurityWarnings();
        bundledPluginVersions = bundledPlugins();
        installedPluginVersions = installedPlugins();
        List<Exception> exceptions = new ArrayList<>();
        allPluginsAndDependencies = findPluginsAndDependencies(cfg.getPlugins(), exceptions);
        pluginsToBeDownloaded = findPluginsToDownload(allPluginsAndDependencies);
        effectivePlugins = findEffectivePlugins(pluginsToBeDownloaded);

        listPlugins();
        showSpecificSecurityWarnings(pluginsToBeDownloaded);
        checkVersionCompatibility(jenkinsVersion, pluginsToBeDownloaded, exceptions);
        if (!exceptions.isEmpty()) {
            throw new AggregatePluginPrerequisitesNotMetException(exceptions);
        }
        if (cfg.doDownload()) {
            downloadPlugins(pluginsToBeDownloaded);
        }
        logMessage("Done");
    }

    void createPluginDir(boolean failIfExists) {
        if (pluginDir.exists()) {
            if (failIfExists) {
                throw new DirectoryCreationException("The plugin directory already exists: " + pluginDir);
            } else {
                if (!pluginDir.isDirectory()) {
                    throw new DirectoryCreationException("The plugin directory path is not a directory: " + pluginDir);
                }
                return;
            }
        }
        try {
            Files.createDirectories(pluginDir.toPath());
        } catch (IOException e) {
            throw new DirectoryCreationException(String.format("Unable to create plugin directory: '%s', supply a directory with -d <your-directory>", pluginDir), e);
        }
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
                logVerbose(String.format(
                        "Will install new plugin %s %s", pluginName, plugin.getVersion()));
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
    void listPlugins() {
        if (cfg.isShowPluginsToBeDownloaded()) {
            logPlugins("\nInstalled plugins:", new ArrayList<>(installedPluginVersions.values()));
            logPlugins("Bundled plugins:", new ArrayList<>(bundledPluginVersions.values()));
            logPlugins("All requested plugins:", new ArrayList<>(allPluginsAndDependencies.values()));
            logPlugins("Plugins that will be downloaded:", pluginsToBeDownloaded);
            outputPluginList(new ArrayList<>(effectivePlugins.values()), () -> new StdOutPluginOutputConverter("Resulting plugin list:"));
        }
    }

    /**
     * Given a list of plugins and a description, prints them out
     *
     * @param description string describing plugins to be printed
     * @param plugins     list of plugins to be output
     */
    private void logPlugins(String description, List<Plugin> plugins) {
        logMessage(new StdOutPluginOutputConverter(description).convert(plugins));
    }

    /**
     * Generate plugin list in the format requested by the user and output to standard out.
     * @param plugins plugins to include in the list
     * @param stdOutConverter if the output format is STDOUT, use the supplied converter.
     */
    public void outputPluginList(@NonNull List<Plugin> plugins, @NonNull Supplier<PluginOutputConverter> stdOutConverter) {
        System.out.println(formatPluginsList(plugins, stdOutConverter));
    }

    /**
     * Generate plugin list in the format requested by the user.
     * @param plugins plugins to include in the list
     * @param stdOutConverter if the output format is STDOUT, use the supplied converter.
     */
    private String formatPluginsList(@NonNull List<Plugin> plugins, @NonNull Supplier<PluginOutputConverter> stdOutConverter) {
        switch (cfg.getOutputFormat()) {
            case YAML:
                return new YamlPluginOutputConverter().convert(plugins);
            case TXT:
                return new TxtOutputConverter().convert(plugins);
            case STDOUT:
            default:
                return stdOutConverter.get().convert(plugins);
        }
    }

    /**
     * Gets the security warnings for plugins from the update center json and creates a list of all the security
     * warnings
     *
     * @return map of plugins and their security warnings
     */
    public Map<String, List<SecurityWarning>> getSecurityWarnings() {
        if (latestUcJson == null) {
            logMessage("Unable to get update center json");
            return allSecurityWarnings;
        }
        if (!latestUcJson.has("warnings")) {
            logMessage("update center json has no warnings: ignoring");
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
            allSecurityWarnings.values()
                    .stream()
                    .flatMap(List::stream)
                    .sorted(Comparator.comparing(SecurityWarning::getName))
                    .map(w -> w.getName() + " - " + w.getMessage())
                    .forEach(this::logMessage);
        }
    }

    /**
     * Prints out security warning information for a list of plugins if isShowWarnings is set to true in the config
     * file
     *
     * @param plugins list of plugins for which to see security warnings
     */

    public void showSpecificSecurityWarnings(List<Plugin> plugins) {
        // NOTE: By default, the plugin installation manager tool will show security warnings.
        // see: https://github.com/jenkinsci/plugin-installation-manager-tool/issues/258
        if (!cfg.isHideWarnings()) {
            boolean headingDisplayed = false;
            for (Plugin plugin : plugins) {
                if (warningExists(plugin)) {
                    if (!headingDisplayed) {
                        logMessage("\nSecurity warnings:");
                        headingDisplayed = true;
                    }
                    String pluginName = plugin.getName();
                    logMessage(plugin.getSecurityWarnings().stream()
                            .map(warning -> String.format("%s (%s): %s %s %s", pluginName,
                                    plugin.getVersion(), warning.getId(), warning.getMessage(), warning.getUrl())).
                                    collect(Collectors.joining("\n")));
                }
            }
        }
    }

    /**
     * Takes a list of plugins and returns the latest version
     * Returns existing version if no update
     * @param plugins updated list of plugins
     * @return latest plugin versions
     */
    public List<Plugin> getLatestVersionsOfPlugins(List<Plugin> plugins) {
        return plugins.stream()
                .map(plugin -> {
                    String pluginVersion = plugin.getVersion().toString();
                    if (plugin.getUrl() != null || plugin.getGroupId() != null || pluginVersion.equals(Plugin.LATEST)) {
                        return plugin;
                    }
                    if (latestPlugins == null) {
                        throw new IllegalStateException("List of plugins is not available. Likely Update Center data has not been downloaded yet");
                    }

                    if (isBeta(pluginVersion)) {
                        resolveExperimentalUcIfRequired();
                        if (experimentalPlugins.has(plugin.getName())) {
                            return getUpdatedPlugin(plugin, experimentalPlugins);
                        }
                    }

                    if (latestPlugins.has(plugin.getName())) {
                        return getUpdatedPlugin(plugin, latestPlugins);
                    }
                    return plugin;
                })
                .collect(Collectors.toList());
    }

    private Plugin getUpdatedPlugin(Plugin plugin, JSONObject pluginsFromUpdateCenter) {
        JSONObject pluginInfo = pluginsFromUpdateCenter.getJSONObject(plugin.getName());
        VersionNumber versionNumber = new VersionNumber(pluginInfo.getString("version"));
        if (versionNumber.isOlderThan(plugin.getVersion())) {
            versionNumber = plugin.getVersion();
        }

        return new Plugin(plugin.getName(), versionNumber.toString(), null, null);
    }

    private boolean isBeta(String version) {
        return StringUtils.indexOfAny(version, "alpha", "beta") != -1;
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
     * @param jenkinsVersion the current version of Jenkins
     * @param pluginsToBeDownloaded list of plugins to check version compatibility with the Jenkins version
     */
    public void checkVersionCompatibility(VersionNumber jenkinsVersion, List<Plugin> pluginsToBeDownloaded) {
        checkVersionCompatibility(jenkinsVersion, pluginsToBeDownloaded, null);
    }

    /**
     * Checks that required Jenkins version of all plugins to be downloaded is less than the Jenkins version in the
     * user specified Jenkins war file
     *
     * @param jenkinsVersion the current version of Jenkins
     * @param pluginsToBeDownloaded list of plugins to check version compatibility with the Jenkins version
     * @param exceptions if not null populated with the list of exception which occurred during this call, otherwise the exception is not caught
     */
    public void checkVersionCompatibility(VersionNumber jenkinsVersion, List<Plugin> pluginsToBeDownloaded, @CheckForNull List<Exception> exceptions) {
        if (jenkinsVersion != null && !StringUtils.isEmpty(jenkinsVersion.toString())) {
            for (Plugin p : pluginsToBeDownloaded) {
                final VersionNumber pluginJenkinsVersion = p.getJenkinsVersion();
                if (pluginJenkinsVersion!= null) {
                    if (pluginJenkinsVersion.isNewerThan(jenkinsVersion)) {
                        VersionCompatibilityException exception = new VersionCompatibilityException(
                                String.format("%n%s (%s) requires a greater version of Jenkins (%s) than %s",
                                        p.getName(), p.getVersion().toString(), pluginJenkinsVersion.toString(),
                                        jenkinsVersion.toString()));
                        if (exceptions != null) {
                            exceptions.add(exception);
                        } else {
                            throw exception;
                        }
                    }
                }
            }
        }
    }

    /**
     * Downloads a list of plugins.
     * Plugins will be downloaded to a temporary directory, and then copied over to the final destination.
     *
     * @param plugins list of plugins to download
     */
    @SuppressFBWarnings("PATH_TRAVERSAL_IN")
    public void downloadPlugins(List<Plugin> plugins) {
        final File downloadsTmpDir;
        try {
            downloadsTmpDir = Files.createTempDirectory("plugin-installation-manager-downloads").toFile();
        } catch (IOException ex) {
            throw new DownloadPluginException("Cannot create a temporary directory for downloads", ex);
        }

        // Download to a temporary dir
        ForkJoinPool ioThreadPool = new ForkJoinPool(64);
        try {
            ioThreadPool.submit(() -> plugins.parallelStream().forEach(plugin -> {
                boolean successfulDownload = downloadPlugin(plugin, getPluginArchive(downloadsTmpDir, plugin));
                if (skipFailedPlugins) {
                    logMessage("SKIP: Unable to download " + plugin.getName());
                } else if (!successfulDownload) {
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

        // Filter out failed plugins
        final List<Plugin> failedPlugins = getFailedPlugins();
        if (!skipFailedPlugins && failedPlugins.size() > 0) {
            throw new DownloadPluginException("Some plugin downloads failed: " +
                    failedPlugins.stream().map(Plugin::getName).collect(Collectors.joining(",")) +
                    ". See " + downloadsTmpDir.getAbsolutePath() + " for the temporary download directory");
        }
        Set<String> failedPluginNames = new HashSet<>(failedPlugins.size());
        failedPlugins.forEach(plugin -> failedPluginNames.add(plugin.getName()));

        // Copy files over to the destination directory
        for (Plugin plugin : plugins) {
            String archiveName = plugin.getArchiveFileName();
            File downloadedPlugin = new File(downloadsTmpDir, archiveName);
            try {
                if (failedPluginNames.contains(plugin.getName())) {
                    logMessage("Will skip the failed plugin download: " + plugin.getName() +
                            ". See " + downloadedPlugin.getAbsolutePath() + " for the downloaded file");
                }
                // We do not double-check overrides here, because findPluginsToDownload() has already done it
                File finalPath = new File(pluginDir, archiveName);
                File backupPath = new File(pluginDir, plugin.getBackupFileName());
                if (finalPath.isDirectory()) {
                    // Jenkins supports storing plugins as unzipped files with ".jpi" extension
                    FileUtils.cleanDirectory(finalPath);
                    Files.delete(finalPath.toPath());
                }
                if (finalPath.exists()) {
                    Files.move(finalPath.toPath(), backupPath.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                Files.move(downloadedPlugin.toPath(), finalPath.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                if (skipFailedPlugins) {
                    logMessage("SKIP: Unable to move " + plugin.getName() + " to the plugin directory");
                } else {
                    throw new DownloadPluginException("Unable to move " + plugin.getName() + " to the plugin directory", ex);
                }
            }
        }
    }

    @SuppressFBWarnings("PATH_TRAVERSAL_IN")
    private File getPluginArchive(File pluginDir, Plugin plugin) {
        return new File(pluginDir, plugin.getArchiveFileName());
    }

    /**
     * Given a list of plugins, finds the recursive set of all dependent plugins. If multiple plugins rely on different
     * versions of the same plugin, the higher version required will replace the lower version dependency
     *
     * @param requestedPlugins list of plugins to find all dependencies for
     * @return set of all requested plugins and their recursive dependencies
     */
    public Map<String, Plugin> findPluginsAndDependencies(List<Plugin> requestedPlugins) {
        return findPluginsAndDependencies(requestedPlugins, null);
    }

    /**
     * Given a list of plugins, finds the recursive set of all dependent plugins. If multiple plugins rely on different
     * versions of the same plugin, the higher version required will replace the lower version dependency
     *
     * @param requestedPlugins list of plugins to find all dependencies for
     * @param exceptions if not null populated with the list of exception which occurred during this call, otherwise the exception is not caught
     * @return set of all requested plugins and their recursive dependencies
     */
    public Map<String, Plugin> findPluginsAndDependencies(List<Plugin> requestedPlugins, @CheckForNull List<Exception> exceptions) {
        // Prepare the initial list by putting all explicitly requested plugins
        Map<String, Plugin> topLevelDependencies = new HashMap<>();
        for (Plugin requestedPlugin : requestedPlugins) {
            topLevelDependencies.put(requestedPlugin.getName(), requestedPlugin);
        }
        Map<String, Plugin> allPluginDependencies = new HashMap<>(topLevelDependencies);

        for (Plugin requestedPlugin : requestedPlugins) {
            calculateChecksum(requestedPlugin);
            //for each requested plugin, find all the dependent plugins that will be downloaded (including requested plugin)
            Map<String, Plugin> dependencies = resolveRecursiveDependencies(requestedPlugin, topLevelDependencies, exceptions);

            for (Plugin dependentPlugin : dependencies.values()) {
                String dependencyName = dependentPlugin.getName();
                VersionNumber dependencyVersion = dependentPlugin.getVersion();
                calculateChecksum(requestedPlugin);
                if (!allPluginDependencies.containsKey(dependencyName)) {
                    allPluginDependencies.put(dependencyName, dependentPlugin);
                } else {
                    Plugin existingDependency = allPluginDependencies.get(dependencyName);
                    allPluginDependencies.replace(existingDependency.getName(),
                            combineDependencies(existingDependency, dependentPlugin));
                }
            }
        }
        return removeOptional(allPluginDependencies);
    }

    private Map<String, Plugin> removeOptional(Map<String, Plugin> plugins) {
        Map<String, Plugin> filtered = new HashMap<>();
        for (Map.Entry<String, Plugin> entry : plugins.entrySet()) {
            if (!entry.getValue().getOptional()) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered;
    }

    // Return a new dependency which is the intersection of the two given dependencies. The rules
    // for determining this are as follows:
    // - The resulting plugin is optional iff both the given plugins are optional
    // - the resulting plugin will have the higher of the given versions
    // - any remaining plugin attributes will come from the plugin with the higher version
    private Plugin combineDependencies(Plugin a, Plugin b) {
        if (!a.getName().equals(b.getName())) {
            throw new IllegalStateException("Can only combine dependencies on the same plugin. Got " + a.getName() + " and " + b.getName());
        }

        boolean resultIsOptional = a.getOptional() && b.getOptional();

        Plugin higherVersion = a;
        if (a.getVersion().isOlderThan(b.getVersion())) {
            higherVersion = b;
        }

        higherVersion.setOptional(resultIsOptional);
        return higherVersion;
    }

    private void calculateChecksum(Plugin requestedPlugin) {
        if (latestPlugins.has(requestedPlugin.getName())) {
            JSONObject pluginFromUpdateCenter = latestPlugins.getJSONObject(requestedPlugin.getName());

            String versionInUpdateCenter = pluginFromUpdateCenter.getString("version");
            if (versionInUpdateCenter.equals(requestedPlugin.getVersion().toString())) {

                String checksum = pluginFromUpdateCenter.getString(getHashFunction().toString());
                logVerbose("Setting checksum for: " + requestedPlugin.getName() + " to " + checksum);
                requestedPlugin.setChecksum(checksum);
            } else if (requestedPlugin.getChecksum() == null) {
                logVerbose("Couldn't find checksum for " + requestedPlugin.getName() + " at version: " + requestedPlugin.getVersion().toString());
            }
        } else if (requestedPlugin.getChecksum() == null) {
            logVerbose("Couldn't find checksum for: " + requestedPlugin.getName());
        }
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
     * @deprecated see {@link #getJson(URL, String)}
     * @return JSON object from data provided by the URL at urlString
     */
    @Deprecated
    public JSONObject getJson(String urlString) {
        URL url = stringToUrlQuietly(urlString);
        return getJson(url, null);
    }

    private URL stringToUrlQuietly(String urlString) {
        URL url;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            throw new UpdateCenterInfoRetrievalException("Malformed url for update center", e);
        }
        return url;
    }

    /**
     * Retrieves JSON from a URL and caches it
     *
     * @param url the url to retrieve json from
     * @param cacheKey a key to use for caching i.e. 'update-center'
     * @return the JSON
     */
    public JSONObject getJson(URL url, String cacheKey) {
        JSONObject jsonObject = cm.retrieveFromCache(cacheKey);
        if (jsonObject != null) {
            logVerbose("Returning cached value for: " + cacheKey);
            return jsonObject;
        } else {
            logVerbose("Cache miss for: " + cacheKey);
        }
        final String response;
        try {
            if (url.getProtocol().equalsIgnoreCase("http") || url.getProtocol().equalsIgnoreCase("https")) {
                response = getViaHttpWithResponseHandler(
                  url.toString(),
                  new BasicResponseHandler(),
                  cacheKey,
                  e -> String.format("Unable to retrieve JSON from %s: %s", url, e.getMessage()),
                  3);
            } else {
                response = IOUtils.toString(url, StandardCharsets.UTF_8);
            }
            String result = removePossibleWrapperText(response);
            JSONObject json = new JSONObject(result);
            cm.addToCache(cacheKey, json);
            return json;
        } catch (IOException e) {
            throw new UpdateCenterInfoRetrievalException("Error getting update center json", e);
        }
    }

    /**
     * Gets update center json, which is later used to determine plugin dependencies and security warnings
     * @param jenkinsVersion the version of Jenkins to use
     */
    public void getUCJson(VersionNumber jenkinsVersion) {
        logVerbose("\nRetrieving update center information");
        cm.createCache();

        String cacheSuffix = getCacheSuffix(jenkinsVersion);
        try {
            URIBuilder uriBuilder = new URIBuilder(cfg.getJenkinsUc().toURI());
            if (jenkinsVersion != null) {
                uriBuilder.addParameter("version", jenkinsVersion.toString()).build();
            }
            URL url = uriBuilder.build().toURL();
            logVerbose("Update center URL: " + url);

            latestUcJson = getJson(url, "update-center" + cacheSuffix);
        } catch (MalformedURLException | URISyntaxException e) {
            /* Spotbugs 4.7.0 warns when throwing a runtime exception,
             * but the program cannot do anything with a malformed URL.
             * Spotbugs warning is ignored.
             */
            throw new RuntimeException(e);
        }
        latestPlugins = latestUcJson.getJSONObject("plugins");
        pluginInfoJson = getJson(cfg.getJenkinsPluginInfo(), "plugin-versions");
    }

    private static String getCacheSuffix(VersionNumber jenkinsVersion) {
        return jenkinsVersion != null ? "-" + jenkinsVersion : "";
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
                String checksum = specificVersionInfo.getString(getHashFunction().toString());
                logVerbose("Setting checksum for: " + plugin.getName() + " to " + checksum);
                plugin.setChecksum(checksum);
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
     * Retrieves the latest available version of a specified plugin.
     *
     * @param dependendantPlugin the plugin depending on the given plugin
     * @param pluginName the name of the plugin
     * @return latest version of the specified plugin
     * @throws IllegalStateException Update Center JSON has not been retrieved yet
     */
    public VersionNumber getLatestPluginVersion(Plugin dependendantPlugin, String pluginName) {
        if (latestPlugins == null) {
            throw new IllegalStateException("List of plugins is not available. Likely Update Center data has not been downloaded yet");
        }

        if (!latestPlugins.has(pluginName)) {
            throw new PluginNotFoundException(dependendantPlugin, String.format("unable to find dependant plugin %s in update center %s", pluginName,
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
        // TODO(oleg_nenashev): refactor to use ManifestTools. This logic not only resolves dependencies, but also modifies the plugin's metadata
        List<Plugin> dependentPlugins = new ArrayList<>();
        try {
            File tempFile = Files.createTempFile(FilenameUtils.getName(plugin.getName()), ".jpi").toFile();
            logVerbose(
                    String.format("%nResolving dependencies of %s by downloading plugin to temp file %s and parsing " +
                            "MANIFEST.MF", plugin.getName(), tempFile.toString()));
            if (!downloadPlugin(plugin, tempFile)) {
                Files.deleteIfExists(tempFile.toPath());
                throw new DownloadPluginException("Unable to resolve dependencies for " + plugin.getName());
            }

            if (plugin.getVersion().toString().equals(Plugin.LATEST) ||
                    plugin.getVersion().toString().equals(Plugin.EXPERIMENTAL)) {
                String version = getAttributeFromManifest(tempFile, "Plugin-Version");
                if (!StringUtils.isEmpty(version)) {
                    plugin.setVersion(new VersionNumber(version));
                }
            }
            String minimumJenkinsVersion = getAttributeFromManifest(tempFile, "Jenkins-Version");
            if (minimumJenkinsVersion == null) {
                minimumJenkinsVersion = getAttributeFromManifest(tempFile, "Hudson-Version");
            }
            if (minimumJenkinsVersion == null) {
                throw new PluginDependencyException(plugin, "does not contain a Jenkins-Version attribute in the MANIFEST.MF");
            }
            plugin.setJenkinsVersion(minimumJenkinsVersion);

            String dependencyString = getAttributeFromManifest(tempFile, "Plugin-Dependencies");

            //not all plugin Manifests contain the Plugin-Dependencies field
            if (StringUtils.isEmpty(dependencyString)) {
                logVerbose("\n" + plugin.getName() + " has no dependencies");
                return dependentPlugins;
            }
            String[] dependencies = dependencyString.split(",");

            for (String dependency : dependencies) {
                String[] pluginInfo = dependency
                        .replace(";resolution:=optional", "")
                        .split(":");
                String pluginName = pluginInfo[0];
                String pluginVersion = pluginInfo[1];
                Plugin dependentPlugin = new Plugin(pluginName, pluginVersion, null, null);
                dependentPlugin.setOptional(dependency.contains("resolution:=optional"));

                dependentPlugins.add(dependentPlugin);
                dependentPlugin.setParent(plugin);
            }
            logVerbose(dependentPlugins.isEmpty() ? String.format("%n%s has no dependencies", plugin.getName()) :
                    String.format("%n%s depends on: %n", plugin.getName()) +
                            dependentPlugins.stream()
                                    .map(p -> p.getName() + " " + p.getVersion())
                                    .collect(Collectors.joining("\n")));

            Files.delete(tempFile.toPath());
            return dependentPlugins;
        } catch (IOException e) {
            logMessage(String.format("Unable to resolve dependencies for %s", plugin.getName()));
            logOutput.printVerboseStacktrace(e);
            return dependentPlugins;
        }
    }

    /**
     * Given a plugin and json that contains plugin information, determines the dependencies and returns the list of
     * dependencies.
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
            String pluginName = dependency.getString("name");
            String pluginVersion = dependency.getString("version");
            Plugin dependentPlugin = new Plugin(pluginName, pluginVersion, null, null);
            dependentPlugin.setOptional(dependency.getBoolean("optional"));
            dependentPlugin.setParent(plugin);
            dependentPlugins.add(dependentPlugin);
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
        } else if (version.equals(Plugin.LATEST)) {
            dependentPlugins = resolveDependenciesFromJson(plugin, latestUcJson);
        } else if (version.equals(Plugin.EXPERIMENTAL)) {
            resolveExperimentalUcIfRequired();

            dependentPlugins = resolveDependenciesFromJson(plugin, experimentalUcJson);
        } else {
            dependentPlugins = resolveDependenciesFromJson(plugin, pluginInfoJson);
        }
        if (dependentPlugins == null) {
            return resolveDependenciesFromManifest(plugin);
        }

        return dependentPlugins;
    }

    private void resolveExperimentalUcIfRequired() {
        if (experimentalPlugins == null) {
            experimentalUcJson = getJson(
                    cfg.getJenkinsUcExperimental(),
                    "experimental-update-center" + getCacheSuffix(getJenkinsVersion())
            );
            experimentalPlugins = experimentalUcJson.getJSONObject("plugins");
        }
    }

    /**
     * Finds all recursive dependencies for a given plugin. If the same plugin is required by different plugins, the
     * highest required version will be taken.
     *
     * @param plugin to resolve dependencies for
     * @return map of plugin names and plugins representing all of the dependencies of the requested plugin, including
     * the requested plugin itself
     */
    public Map<String, Plugin> resolveRecursiveDependencies(Plugin plugin) {
        return resolveRecursiveDependencies(plugin, null, null);
    }

    public Map<String, Plugin> resolveRecursiveDependencies(Plugin plugin, @CheckForNull Map<String, Plugin> topLevelDependencies) {
        return resolveRecursiveDependencies(plugin, topLevelDependencies, null);
    }

    // A full dependency graph resolution and removal of non-needed dependency trees is required
    public Map<String, Plugin> resolveRecursiveDependencies(Plugin plugin, @CheckForNull Map<String, Plugin> topLevelDependencies, @CheckForNull List<Exception> exceptions) {
        Deque<Plugin> queue = new LinkedList<>();
        Map<String, Plugin> recursiveDependencies = new HashMap<>();
        queue.add(plugin);
        recursiveDependencies.put(plugin.getName(), plugin);

        while (queue.size() != 0) {
            Plugin dependency = queue.poll();

            try {
                if (!dependency.isDependenciesSpecified()) {
                    dependency.setDependencies(resolveDirectDependencies(dependency));
                }
            } catch (RuntimeException e) {
                if (!(e instanceof PluginException)) {
                    e = new PluginDependencyException(dependency, String.format("has unresolvable dependencies: %s", e.getMessage()), e);
                }
                if (exceptions != null) {
                    exceptions.add(e);
                } else {
                    /* Spotbugs 4.7.0 warns when throwing a runtime exception,
                     * but the program cannot do anything with unexpected runtime
                     * exceptions except throw them or record them in the list of
                     * exceptions for processing by the caller.
                     * Spotbugs warning is ignored.
                     */
                    throw e;
                }
                continue;
            }
            for (Plugin p : dependency.getDependencies()) {
                String dependencyName = p.getName();
                Plugin pinnedPlugin = topLevelDependencies != null ? topLevelDependencies.get(dependencyName) : null;

                if (pinnedPlugin != null) { // There is a top-level plugin with the same ID
                    if (pinnedPlugin.getVersion().isOlderThan(p.getVersion()) && !pinnedPlugin.getVersion().equals(LATEST)) {
                        String message = String.format("depends on %s:%s, but there is an older version defined on the top level - %s:%s",
                                p.getName(), p.getVersion(), pinnedPlugin.getName(), pinnedPlugin.getVersion());
                        PluginDependencyException exception = new PluginDependencyException(dependency, message);
                        if (exceptions != null) {
                            exceptions.add(exception);
                        } else {
                            throw exception;
                        }
                    } else {
                        logVerbose(String.format("Skipping dependency %s:%s and its sub-dependencies, because there is a higher version defined on the top level - %s:%s",
                                        p.getName(), p.getVersion(), pinnedPlugin.getName(), pinnedPlugin.getVersion()));
                        continue;
                    }
                } else if (useLatestSpecified && dependency.isLatest() || useLatestAll) {
                    try {
                        VersionNumber latestPluginVersion = getLatestPluginVersion(dependency, p.getName());
                        p.setVersion(latestPluginVersion);
                        p.setLatest(true);
                    } catch (PluginNotFoundException e) {
                        if (!p.getOptional()) {
                            throw e;
                        }
                        logVerbose(String.format(
                                    "%s unable to find optional plugin %s in update center %s. " +
                                    "Ignoring until it becomes required.", e.getOriginatorPluginAndDependencyChain(),
                                    dependencyName, jenkinsUcLatest));
                    }
                }

                if (!recursiveDependencies.containsKey(dependencyName)) {
                    recursiveDependencies.put(dependencyName, p);
                    if (!p.getOptional()) {
                        // If/when this dependency becomes non-optional, we will expand its dependencies.
                        queue.add(p);
                    }
                } else {
                    Plugin existingDependency = recursiveDependencies.get(dependencyName);
                    Plugin newDependency = combineDependencies(existingDependency, p);
                    if (!newDependency.equals(existingDependency)) {
                        outputPluginReplacementInfo(existingDependency, newDependency);
                        recursiveDependencies.replace(dependencyName, existingDependency, newDependency);
                        // newDependency may have additional dependencies if it is a higher version or
                        // if it became non-optional.
                        queue.add(newDependency);
                    }
                }
            }
        }
        return recursiveDependencies;
    }

    /**
     * Downloads a plugin, skipping if already installed or bundled in the war. A plugin's dependencies will be
     * resolved after the plugin is downloaded/copied.
     *
     * @param plugin   to download
     * @param location location to download plugin to. If location is set to {@code null}, will download to the plugin folder
     *                 otherwise will download to the temporary location specified.
     *                 Location can be in form of a http://, https:// or file:// URI
     * @return boolean signifying if plugin was successful
     */
    public boolean downloadPlugin(Plugin plugin, @CheckForNull File location) {
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
        if (successfulDownload && location == null) {
            logMessage(String.format("%s downloaded successfully", plugin.getName()));
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

        String urlString;

        if (StringUtils.isEmpty(pluginVersion)) {
            pluginVersion = Plugin.LATEST;
        }

        String jenkinsUcDownload = System.getenv("JENKINS_UC_DOWNLOAD");
        String jenkinsUcDownloadUrl = System.getenv("JENKINS_UC_DOWNLOAD_URL");

        if (StringUtils.isNotEmpty(pluginUrl)) {
            urlString = pluginUrl;
        } else if (StringUtils.isNotEmpty(jenkinsUcDownloadUrl)) {
            urlString = appendPathOntoUrl(jenkinsUcDownloadUrl, pluginName, pluginVersion, pluginName + ".hpi");
        } else if (pluginVersion.equals(Plugin.LATEST) && !StringUtils.isEmpty(jenkinsUcLatest)) {
            urlString = appendPathOntoUrl(dirName(jenkinsUcLatest), "/latest", pluginName + ".hpi");
        } else if (pluginVersion.equals(Plugin.EXPERIMENTAL)) {
            urlString = appendPathOntoUrl(dirName(cfg.getJenkinsUcExperimental()), "/latest", pluginName + ".hpi");
        } else if (!StringUtils.isEmpty(plugin.getGroupId())) {
            String groupId = plugin.getGroupId();
            groupId = groupId.replace(".", "/");
            String incrementalsVersionPath = String.format("%s/%s/%s-%s.hpi", pluginName, pluginVersion, pluginName, pluginVersion);
            urlString = appendPathOntoUrl(cfg.getJenkinsIncrementalsRepoMirror(), groupId, incrementalsVersionPath);
        } else if (StringUtils.isNotEmpty(jenkinsUcDownload)) {
            urlString = appendPathOntoUrl(jenkinsUcDownload, "/plugins", pluginName, pluginVersion, pluginName + ".hpi");
        } else {
            urlString = appendPathOntoUrl(removePath(cfg.getJenkinsUc()), "/download/plugins", pluginName, pluginVersion, pluginName + ".hpi");
        }
        logVerbose(String.format("Will use url: %s to download %s plugin", urlString, plugin.getName()));
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
    public boolean downloadToFile(String urlString, Plugin plugin, @CheckForNull File fileLocation) {
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
    @SuppressFBWarnings("PATH_TRAVERSAL_IN")
    public boolean downloadToFile(String urlString, Plugin plugin, @CheckForNull File fileLocation, int maxRetries) {
        File pluginFile;
        if (fileLocation == null) {
            pluginFile = new File(pluginDir, plugin.getArchiveFileName());
            logMessage("\nDownloading plugin " + plugin.getName() + " from url: " + urlString);
        } else {
            pluginFile = fileLocation;
        }

        boolean success = true;

        if(urlString.startsWith("http://") || urlString.startsWith("https://")){
            success = downloadHttpToFile(urlString, plugin, pluginFile, maxRetries);

            if (!success && !urlString.startsWith(MIRROR_FALLBACK_BASE_URL)) {
                logMessage("Downloading from mirrors failed, falling back to " + MIRROR_FALLBACK_BASE_URL);
                // as fallback try to directly download from Jenkins server (only if mirrors fail)
                urlString = appendPathOntoUrl(MIRROR_FALLBACK_BASE_URL, "/plugins", plugin.getName(), plugin.getVersion(), plugin.getName() + ".hpi");
                return downloadToFile(urlString, plugin, fileLocation, 1);
            }
        } else if (urlString.startsWith("file://")){
            success = copyLocalFile(urlString, plugin, pluginFile);
        }

        if (success) {
            // Check integrity of plugin file
            try (JarFile ignored = new JarFile(pluginFile)) {
                verifyChecksum(plugin, pluginFile);
                plugin.setFile(pluginFile);
            } catch (IOException e) {
                failedPlugins.add(plugin);
                logMessage("Downloaded file is not a valid ZIP");
                logOutput.printVerboseStacktrace(e);
                return false;
            } catch (PluginChecksumMismatchException e) {
                failedPlugins.add(plugin);
                logMessage(e.getMessage());
                return false;
            }
        } else {
            failedPlugins.add(plugin);
        }
        return success;
    }

    /**
     * Downloads a plugin from HTTP(s) location
     *
     * @param pluginUrl     location to download plugin to.
     * @param plugin        to download
     * @param pluginFile    location to store the plugin file.
     *                      If file already exists, it will be overrided.
     * @param maxRetries   Maximum number of times to retry the download before failing
     * @return              boolean signifying if plugin was successfully downloaded
     */
    protected boolean downloadHttpToFile(String pluginUrl, Plugin plugin, File pluginFile, int maxRetries){
        try {
            getViaHttpWithResponseHandler(pluginUrl, new FileDownloadResponseHandler(pluginFile), plugin.getName(),
                    e -> String.format("Unable to resolve plugin URL %s, or download plugin %s to file: %s", pluginUrl,
                            plugin.getName(), e.getMessage()),
                    maxRetries);

            // Check if plugin is a proper ZIP file
            try (JarFile ignored = new JarFile(pluginFile)) {
                plugin.setFile(pluginFile);
                logVerbose("Downloaded and validated plugin " + plugin.getName());
            } catch (IOException e) {
                throw new IOException("Downloaded file for " + plugin.getName() + " is not a valid ZIP", e);
            }
        } catch (IOException e) {
            logMessage(e.getMessage());
            logOutput.printVerboseStacktrace(e);
            return false;
        }
        return true;
    }

    @SuppressFBWarnings({"HTTP_PARAMETER_POLLUTION"})
    private <T> T getViaHttpWithResponseHandler(String url, ResponseHandler<? extends T> responseHandler, String resourceName, Function<IOException, String> ioExceptionMessageSupplier, int maxRetries) throws IOException {
        HttpClient httpClient = getHttpClient();
        HttpClientContext context = HttpClientContext.create();
        CredentialsProvider credentialsProvider = getCredentialsProvider();
        if (credentialsProvider != null) {
            context.setCredentialsProvider(credentialsProvider);
        }
        HttpGet httpGet = new HttpGet(url);
        boolean success = false;
        // TODO: retry logic should rather be implemented via DefaultHttpRequestRetruHandler, there is no need for an additional retry
        for (int i = 0; i < maxRetries; i++) {
            try {
                T response = httpClient.execute(httpGet, responseHandler, context);
                success = true;
                return response;
            } catch (IOException e) {
                String message = ioExceptionMessageSupplier.apply(e);
                if (i < maxRetries - 1) {
                    logMessage(message);
                } else {
                    throw new IOException(message, e);
                }
            } finally {
                // get final URI (after all redirects)
                List<URI> locations = context.getRedirectLocations();
                if (locations != null) {
                    String message = String.format("%s %s from %s (attempt %d of %d)",
                            success ? "Downloaded" : "Tried downloading", resourceName,
                            locations.get(locations.size() - 1), i + 1, maxRetries);
                    if (success) {
                        logVerbose(message);
                    } else {
                        logMessage(message);
                    }
                }
            }
        }
        throw new IllegalStateException("Reached maximum number of retries without triggering IOException");
    }

    /**
     * Downloads a plugin from local folder location
     *
     * @param pluginUrl location to download plugin to.
     * @param plugin   to download
     * @param pluginFile    location to store the plugin file.
     *                      If file already exists, it will be overrided.
     * @return boolean signifying if plugin was successfully downloaded
     */
    @SuppressFBWarnings({"PATH_TRAVERSAL_IN"})
    protected boolean copyLocalFile(String pluginUrl, Plugin plugin, File pluginFile){
        boolean success = false;
        try {
            File originFile = new File(new URI(pluginUrl));
            if(originFile.exists()){
                Files.copy(originFile.toPath(), pluginFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                success = true;
            } else {
                String message = String.format("Unable to copy plugin URL %s, original file does not exists or is not accessible", originFile.getAbsolutePath());
                logMessage(message);
            }
        } catch (URISyntaxException | InvalidPathException | IOException e) {
            logMessage("ERROR " + e.getClass().toGenericString());

            String message = String.format("Unable to resolve plugin URL %s, or copy plugin %s to file: %s",
            pluginUrl, plugin.getName(), e.getMessage());
            logMessage(message);
            success = false;
        }
        return success;
    }

    private CredentialsProvider getCredentialsProvider() {
        if (cfg.getCredentials().isEmpty()) {
            return null;
        }
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        for (Credentials credentials : cfg.getCredentials()) {
            credsProvider.setCredentials(
                    new AuthScope(credentials.getHost(), credentials.getPort()),
                    new UsernamePasswordCredentials(credentials.getUsername(), credentials.getPassword()));
        }
        return credsProvider;
    }

    void verifyChecksum(Plugin plugin, File pluginFile) {
        String expectedChecksum = plugin.getChecksum();
        if (expectedChecksum == null) {
            logVerbose("No checksum found for " + plugin.getName() + " (probably custom built plugin)");
            return;
        }

        byte[] actualChecksumDigest = calculateChecksum(pluginFile);
        byte[] expectedCheckSumDigest;

        try {
            expectedCheckSumDigest = Base64.getDecoder().decode(expectedChecksum);
        } catch (IllegalArgumentException e) {
            String actual = new String(Base64.getEncoder().encode(actualChecksumDigest), StandardCharsets.UTF_8);
            throw new PluginChecksumMismatchException(plugin, expectedChecksum, actual);
        }
        if (!MessageDigest.isEqual(actualChecksumDigest, expectedCheckSumDigest)) {
            String actual = new String(Base64.getEncoder().encode(actualChecksumDigest), StandardCharsets.UTF_8);
            throw new PluginChecksumMismatchException(plugin, expectedChecksum, actual);
        } else {
            logVerbose("Checksum valid for: " + plugin.getName());
        }
    }

    @SuppressFBWarnings(value = "WEAK_MESSAGE_DIGEST_SHA1", justification = "CloudBees update center only uses sha1, remove sha1 once this has been updated.")
    private byte[] calculateChecksum(File pluginFile) {
        try (FileInputStream fin = new FileInputStream(pluginFile)) {
            HashFunction hashFunction = getHashFunction();
            switch (hashFunction) {
                case SHA1:
                    return DigestUtils.sha1(fin);
                case SHA512:
                    return DigestUtils.sha512(fin);
                case SHA256:
                    return DigestUtils.sha256(fin);
                default:
                    throw new UnsupportedChecksumException(hashFunction.toString() + "is an unsupported hash function.");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Gets Jenkins version using one of the available methods.
     * @return Jenkins version or {@code null} if it cannot be determined
     */
    @CheckForNull
    public VersionNumber getJenkinsVersion() {
        if (jenkinsVersion != null) {
            return jenkinsVersion;
        }
        if (jenkinsWarFile != null) {
            return getJenkinsVersionFromWar();
        }
        logMessage("Unable to determine Jenkins version");
        return null;
    }

    /**
     * Gets the Jenkins version from the manifest in the Jenkins war specified in the Config class
     *
     * @return Jenkins version or {@code null} if the version cannot be determined
     */
    @CheckForNull
    public VersionNumber getJenkinsVersionFromWar() {
        if (jenkinsWarFile == null) {
            logMessage("Unable to get Jenkins version from the WAR file: WAR file path is not defined.");
            return null;
        }
        String version = getAttributeFromManifest(jenkinsWarFile, "Jenkins-Version");
        if (StringUtils.isEmpty(version)) {
            logMessage("Unable to get Jenkins version from the WAR file " + jenkinsWarFile.getPath());
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
            logMessage("Unable to get plugin version from " + file);
            return "";
        }
        return version;
    }

    /**
     * @param file plugin .hpi or .jpi of which to get the version
     * @param key index key used to find the attribute in file
     * @deprecated Use {@link ManifestTools#getAttributeFromManifest(File, String)}
     * @return attribute for key as read from file
     */
    @Deprecated
    public String getAttributeFromManifest(File file, String key) {
        return ManifestTools.getAttributeFromManifest(file, key);
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
        File[] files = pluginDir.listFiles(fileFilter);

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
    @SuppressFBWarnings("PATH_TRAVERSAL_IN")
    public Map<String, Plugin> bundledPlugins() {
        Map<String, Plugin> bundledPlugins = new HashMap<>();

        if (jenkinsWarFile == null) {
            logMessage("WAR file is not defined, cannot retrieve the bundled plugins");
            return bundledPlugins;
        }

        if (jenkinsWarFile.exists()) {
            Path path = Paths.get(jenkinsWarFile.toString());
            URI jenkinsWarUri;
            try {
                jenkinsWarUri = new URI("jar:" + path.toUri());
            } catch (URISyntaxException e) {
                throw new WarBundledPluginException("Unable to open war file to extract bundled plugin information", e);
            }

            // Walk through war contents and find bundled plugins
            try (FileSystem warFS = FileSystems.newFileSystem(jenkinsWarUri, Collections.emptyMap())) {
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
            logMessage("War not found, installing all plugins: " + jenkinsWarFile.toString());
        }
        return bundledPlugins;
    }


    /**
     * Gets the hash function used for the update center
     *
     * @return Jenkins update center hash function string
     */
    public HashFunction getHashFunction() {
        return hashFunction;
    }

    public void setHashFunction(HashFunction hashFunction) {
        this.hashFunction = hashFunction;
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
     * Outputs information to the console (std err) if verbose option was set to true
     *
     * @param message informational string to output
     */
    private void logVerbose(String message) {
        logOutput.printVerboseMessage(message);
    }

    /**
     * Output message to the console (std err).
     *
     * @param message informational string to output
     */
    private void logMessage(String message) {
        logOutput.printMessage(message);
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
     * Sets the json object containing latest experimental plugin information
     * @param experimentalUcJson JSONObject containing info for latest and experimental plugins
     */
    public void setExperimentalUcJson(JSONObject experimentalUcJson) {
        this.experimentalUcJson = experimentalUcJson;
    }

    /**
     * Sets the json object containing latest experimental plugin information
     * @param experimentalPlugins JSONObject containing info for latest experimental plugins
     */
    public void setExperimentalPlugins(JSONObject experimentalPlugins) {
        this.experimentalPlugins = experimentalPlugins;
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

    @Override
    public void close() throws IOException {
        if (httpClient != null) {
            httpClient.close();
        }
    }

}
