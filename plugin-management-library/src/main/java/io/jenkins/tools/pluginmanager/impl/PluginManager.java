package io.jenkins.tools.pluginmanager.impl;

import hudson.util.VersionNumber;
import io.jenkins.tools.pluginmanager.config.Config;
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

    private String JENKINS_UC_LATEST = "";
    public static final String JENKINS_UC = "https://updates.jenkins.io";
    public static final String JENKINS_UC_EXPERIMENTAL = JENKINS_UC + "/experimental";
    public static final String JENKINS_UC_DOWNLOAD = JENKINS_UC + "/download";
    public static final String JENKINS_UC_JSON = JENKINS_UC + "/update-center.json";
    public static final String JENKINS_INCREMENTALS_REPO_MIRROR = "https://repo.jenkins-ci.org/incrementals";
    public static final String SEPARATOR = File.separator;

    private String jenkinsVersion;

    private File jenkinsWarFile;
    private Map<String, VersionNumber> installedPluginVersions;
    private Map<String, VersionNumber> bundledPluginVersions;
    private Map<String, SecurityWarning> allSecurityWarnings;
    JSONObject updateCenterJson;
    Config cfg;


    public PluginManager(Config cfg) {
        failedPlugins = new ArrayList();
        jenkinsWarFile = new File(cfg.getJenkinsWar());
        installedPluginVersions = new HashMap<>();
        bundledPluginVersions = new HashMap<>();
        refDir = cfg.getPluginDir();
        this.cfg = cfg;
        allSecurityWarnings = new HashMap();
    }


    public void start() {
        if (!refDir.exists()) {
            try {
                Files.createDirectory(refDir.toPath());
            } catch (IOException e) {
                System.out.println("Unable to create plugin directory");
            }
        }

        updateCenterJson = getUpdateCenterJson();

        jenkinsVersion = getJenkinsVersionFromWar();
        checkVersionSpecificUpdateCenter();

        String url;

        getSecurityWarnings();
        showAllSecurityWarnings();

        bundledPlugins();
        installedPlugins();

        //finds which plugins and their dependencies would be downloaded
        Map<String, Plugin> allPluginsAndDependencies = findPluginsAndDependencies(cfg.getPlugins(), true);

        List<Plugin> pluginsToBeDownloaded = new ArrayList<>(allPluginsAndDependencies.values());

        viewPlugins(pluginsToBeDownloaded);
        downloadPlugins(new ArrayList<>(pluginsToBeDownloaded));

        viewPlugins(failedPlugins);

    }


    public void viewPlugins(List<Plugin> plugins) {
        for (Plugin plugin : plugins) {
            System.out.println(plugin);
        }
    }

    public void showAllSecurityWarnings() {
        if (cfg.isShowAllWarnings()) {
            for (SecurityWarning securityWarning : allSecurityWarnings.values()) {
                System.out.println(securityWarning.getName() + " - " + securityWarning.getMessage());
            }
        }
    }

    public boolean warningExists(Plugin plugin) {
        String pluginName = plugin.getName();
        if (allSecurityWarnings.containsKey(pluginName)) {
            for (SecurityWarning.SecurityVersion effectedVersion : allSecurityWarnings.get(pluginName)
                    .getSecurityVersions()) {
                Matcher m = effectedVersion.getPattern().matcher(plugin.getVersion().toString());
                if (m.matches()) {
                    //System.out.println("Security warning for " + plugin);
                    return true;
                }
            }
        }
        return false;
    }

    public Map<String, Plugin> findPluginsAndDependencies(List<Plugin> requestedPlugins,
                                                          boolean includePluginsWithSecurityWarnings) {
        Map<String, Plugin> allPluginDependencies = new HashMap<>();

        if (updateCenterJson == null) {
            return allPluginDependencies;
        }

        for (Plugin requestedPlugin : requestedPlugins) {
            //for each requested plugin, find all the dependent plugins that be downloaded (including requested plugin)
            Map<String, Plugin> dependencies = resolveRecursiveDependencies(requestedPlugin);

            boolean pluginWarnings = false;

            //check if any of the plugins that would be downloaded contain a security warning
            for (Plugin dependentPlugin : dependencies.values()) {
                if (warningExists(dependentPlugin)) {
                    pluginWarnings = true;
                }
            }
            if (pluginWarnings && !includePluginsWithSecurityWarnings) {
                System.out.println("Requested plugin " + requestedPlugin + " has a security warning and will not be " +
                        "downloaded");
            }

            //if plugin does not have any dependencies with security warnings or user wants to include plugins with
            // security warnings, then add plugins to list of all plugins to be downloaded, upgrading any previously
            // requested plugins if required version is higher than the existing plugin requirement in the list or any
            // currently installed plugins of the same name
            if (!pluginWarnings || (pluginWarnings && includePluginsWithSecurityWarnings)) {
                for (Plugin dependentPlugin : dependencies.values()) {
                    String dependencyName = dependentPlugin.getName();
                    VersionNumber dependencyVersion = dependentPlugin.getVersion();
                    if (!allPluginDependencies.containsKey(dependencyName)) {
                        allPluginDependencies.put(dependencyName, dependentPlugin);
                    } else {
                        Plugin existingDependency = allPluginDependencies.get(dependencyName);
                        if (existingDependency.getVersion().compareTo(dependencyVersion) < 0) {
                            /*
                            System.out
                                    .println("Version of " + dependencyName + " (" + existingDependency.getVersion()
                                            + ") required by " + existingDependency.getParent().getName() + " (" +
                                            existingDependency.getParent().getVersion() +
                                            ") is lower than the version " +
                                            "required (" + dependencyVersion + ") by " +
                                            dependentPlugin.getParent().getName() + " (" +
                                            dependentPlugin.getParent().getVersion() +
                                            "), upgrading required plugin version");
                            */
                            allPluginDependencies.replace(existingDependency.getName(), dependentPlugin);
                        }
                    }

                    VersionNumber installedVersion = null;
                    if (installedPluginVersions.containsKey(dependencyName)) {
                        installedVersion = installedPluginVersions.get(dependencyName);
                    } else if (bundledPluginVersions.containsKey(dependencyName)) {
                        installedVersion = bundledPluginVersions.get(dependencyName);
                    }

                    if (installedVersion != null && installedVersion.compareTo(dependencyVersion) < 0) {
                        /*
                        System.out.println("Installed version (" + installedVersion + ") of " + dependencyName +
                                " is less than minimum " +
                                "required version of " + dependencyVersion + ", bundled plugin will be upgraded");
                        */
                        allPluginDependencies.put(dependencyName, dependentPlugin);
                    }
                }
            }
        }
        return allPluginDependencies;
    }


    public List<Plugin> resolveDirectDependencies(Plugin plugin) {
        List<Plugin> dependentPlugins = new LinkedList<>();
        if (updateCenterJson == null) {
            System.out.println("Unable to get update center json");
            return dependentPlugins;
        }

        JSONObject plugins = updateCenterJson.getJSONObject("plugins");
        JSONObject pluginInfo = (JSONObject) plugins.get(plugin.getName());
        JSONArray dependencies = (JSONArray) pluginInfo.get("dependencies");

        if (dependencies == null || dependencies.length() == 0) {
            //System.out.println(plugin.getName() + " has no dependencies");
            return dependentPlugins;
        }

        //System.out.println("\n" + plugin.getName() + " depends on: ");

        for (int i = 0; i < dependencies.length(); i++) {
            JSONObject dependency = dependencies.getJSONObject(i);
            String pluginName = dependency.getString("name");
            String pluginVersion = dependency.getString("version");
            boolean isPluginOptional = dependency.getBoolean("optional");
            if (isPluginOptional) {
                //System.out.println("Skipping optional dependency " + pluginName);
                continue;
            }
            Plugin dependentPlugin = new Plugin(pluginName, pluginVersion, isPluginOptional);
            System.out.println(dependentPlugin);
            dependentPlugins.add(dependentPlugin);
            dependentPlugin.setParent(plugin);
        }
        plugin.setDependencies(dependentPlugins);
        return dependentPlugins;

    }

    public Map<String, Plugin> resolveRecursiveDependencies(Plugin plugin) {
        ;
        Deque<Plugin> queue = new LinkedList<>();
        Map<String, Plugin> recursiveDependencies = new HashMap<>();
        queue.add(plugin);

        recursiveDependencies.put(plugin.getName(), plugin);

        while (queue.size() != 0) {
            Plugin dependency = queue.poll();

            if (dependency.getDependencies().isEmpty()) {
                dependency.setDependencies(resolveDirectDependencies(dependency));
            }
            Iterator<Plugin> i = dependency.getDependencies().iterator();


            while (i.hasNext()) {
                Plugin p = i.next();
                String dependencyName = p.getName();
                if (!recursiveDependencies.containsKey(dependencyName)) {
                    recursiveDependencies.put(dependencyName, p);
                    queue.add(p);
                } else {
                    Plugin existingDependency = recursiveDependencies.get(dependencyName);
                    if (existingDependency.getVersion().compareTo(p.getVersion()) < 0) {
                        /*
                        System.out.println("Version of " + dependencyName + " ( " + existingDependency.getVersion()
                                + " ) required by " + existingDependency.getParent().getName() + " (" +
                                existingDependency.getParent().getVersion() + ") is lower than the version " +
                                "required (" + p.getVersion() + ") by " + p.getParent().getName() + " (" +
                                p.getParent().getVersion() + "), upgrading required plugin version");
                        */
                        recursiveDependencies.replace(dependencyName, existingDependency, p);
                    }
                }
            }

        }
        return recursiveDependencies;
    }


    public void getSecurityWarnings() {
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
            allSecurityWarnings.put(warningName, securityWarning);
        }
    }


    public void checkVersionSpecificUpdateCenter() {
        //check if version specific update center
        if (!StringUtils.isEmpty(jenkinsVersion)) {
            JENKINS_UC_LATEST = JENKINS_UC + "/" + jenkinsVersion;
            try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
                HttpGet httpget = new HttpGet(JENKINS_UC_LATEST);
                try (CloseableHttpResponse response = httpclient.execute(httpget)) {
                    if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                        JENKINS_UC_LATEST = "";
                    }
                } catch (IOException e) {
                    JENKINS_UC_LATEST = "";
                    System.out.println("No version specific update center for Jenkins version " + jenkinsVersion);
                }
            } catch (IOException e) {
                JENKINS_UC_LATEST = "";
                System.out.println(
                        "Unable to check if version specific update center for Jenkins version " + jenkinsVersion);
            }

        }

    }


    public void downloadPlugins(List<Plugin> plugins) {
        for (Plugin plugin : plugins) {
            boolean successfulDownload = downloadPlugin(plugin);
            if (!successfulDownload) {
                System.out.println("Unable to download " + plugin.getName() + ". Skipping...");
                failedPlugins.add(plugin);
            }
        }
    }

    public JSONObject getUpdateCenterJson() {
        URL updateCenter;

        try {
            updateCenter = new URL(JENKINS_UC_JSON);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        }

        try {
            String updateCenterText = IOUtils.toString(updateCenter, Charset.forName("UTF-8"));
            updateCenterText = updateCenterText.replace("updateCenter.post(\n", "");
            updateCenterText = updateCenterText.replace(");", ""); //should probably make this more robust
            JSONObject updateCenterJson = new JSONObject(updateCenterText);
            return updateCenterJson;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }


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
            //some plugin don't follow the rules about artifact ID, i.e. docker-plugin
            String newPluginName = plugin.getName() + "-plugin";
            plugin.setName(newPluginName);
            pluginDownloadUrl = getPluginDownloadUrl(plugin);
            successfulDownload = downloadToFile(pluginDownloadUrl, plugin);
        }
        if (successfulDownload) {
            installedPluginVersions.put(plugin.getName(), pluginVersion);
            //resolveDependencies(plugin);
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
            System.out.println("Will use url: " + pluginUrl);
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
                System.out.println("Unable to resolve plugin URL to download plugin");
                return false;
            }
        } catch (IOException e) {
            System.out.println("Unable to create HTTP connection to download plugin");
            return false;
        }

        //check integrity of plugin file
        try (JarFile pluginJpi = new JarFile(pluginFile)) {
        } catch (IOException e) {
            failedPlugins.add(plugin);
            System.out.println("Downloaded file is not a valid ZIP");
            return false;
        }

        return true;
    }


    public String getJenkinsVersionFromWar() {
        //java -jar $JENKINS_WAR --version
        try (JarFile jenkinsWar = new JarFile(jenkinsWarFile)) {
            Manifest manifest = jenkinsWar.getManifest();
            Attributes attributes = manifest.getMainAttributes();
            return attributes.getValue("Jenkins-Version");
        } catch (IOException e) {
            System.out.println("Unable to open war file");
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
            e.printStackTrace();
        }
        return "";
    }

    public List<String> installedPlugins() {
        List<String> installedPlugins = new ArrayList<>();
        FileFilter fileFilter = new WildcardFileFilter("*.jpi");

        //only lists files in same directory, does not list files recursively
        System.out.println("\nInstalled plugins: ");
        File[] files = refDir.listFiles(fileFilter);

        if (files != null) {
            for (File file : files) {
                String pluginName = FilenameUtils.getBaseName(file.getName());
                VersionNumber pluginVersion = new VersionNumber(getPluginVersion(file));
                installedPluginVersions.put(pluginName, pluginVersion);
                installedPlugins.add(pluginName);
                System.out.println(pluginName);
            }
        }

        return installedPlugins;
    }


    public List<String> bundledPlugins() {
        List<String> bundledPlugins = new ArrayList<>();

        if (jenkinsWarFile.exists()) {
            //for i in $(jar tf $JENKINS_WAR | grep -E '[^detached-]plugins.*\..pi' | sort)
            Path path = Paths.get(jenkinsWarFile.toString());
            URI jenkinsWarUri;
            try {
                jenkinsWarUri = new URI("jar:" + path.toUri());
            } catch (URISyntaxException e) {
                e.printStackTrace();
                return bundledPlugins;
            }

            //walk through war contents and find bundled plugins
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
                e.printStackTrace();
            }

        } else {
            System.out.println("War not found, installing all plugins: " + jenkinsWarFile.toString());
        }

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




