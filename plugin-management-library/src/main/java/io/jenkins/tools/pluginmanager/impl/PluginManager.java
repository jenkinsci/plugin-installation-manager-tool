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
    private List<SecurityWarning> allSecurityWarnings;
    Config cfg;


    public PluginManager(Config cfg) {
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
                System.out.println("Unable to create plugin directory");
            }
        }

        jenkinsVersion = getJenkinsVersionFromWar();
        checkVersionSpecificUpdateCenter();

        String url;

        getSecurityWarnings();

        if (cfg.isShowAllWarnings()) {
            for (int i = 0; i < allSecurityWarnings.size(); i++) {
                SecurityWarning securityWarning = allSecurityWarnings.get(i);
                System.out.println(securityWarning.getName() + " - " + securityWarning.getMessage());
            }
        }

        bundledPlugins();
        installedPlugins();

        downloadPlugins(plugins);

        writeFailedPluginsToFile();

        //clean up locks

    }


    public void getSecurityWarnings() {
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
            JENKINS_UC_LATEST = new StringBuilder(JENKINS_UC).append(jenkinsVersion).toString();
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


    public void writeFailedPluginsToFile() {
        try (
          FileOutputStream fileOutputStream = new FileOutputStream("failedplugins.txt");
          Writer fstream = new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8)
        ) {
            try (Writer fstream = new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8)) {

                if (failedPlugins.size() > 0) {
                    System.out.println("Some plugins failed to download: ");
                    for (Plugin plugin : failedPlugins) {
                        String failedPluginName = plugin.getName();
                        System.out.println(failedPluginName);
                        fstream.write(failedPluginName + "\n");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }   catch (IOException e) {
                e.printStackTrace();
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


    public void resolveDependencies(Plugin plugin) {
        JSONObject updateCenterJson = getUpdateCenterJson();

        if (updateCenterJson == null) {
            System.out.println("Unable to get update center json");
            return;
        }

        JSONObject plugins = updateCenterJson.getJSONObject("plugins");
        JSONObject pluginInfo = (JSONObject) plugins.get(plugin.getName());
        JSONArray dependencies = (JSONArray) pluginInfo.get("dependencies");

        if (dependencies == null || dependencies.length() == 0) {
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


        for (Plugin dependency : dependentPlugins) {
            String dependencyName = dependency.getName();
            VersionNumber dependencyVersion = dependency.getVersion();
            if (dependency.getPluginOptional()) {
                System.out.println("Skipping optional dependency " + dependencyName);
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
                    System.out.println("Installed version (" + installedVersion + ") of " + dependencyName + " is less than minimum " +
                            "required version of " + dependencyVersion + ", upgrading bundled dependency");
                    downloadPlugin(dependency);
                } else {
                    System.out.println("Skipping already installed dependency " + dependencyName);
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
            resolveDependencies(plugin);
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
            urlString =
                    new StringBuffer(JENKINS_UC_LATEST).append("/latest/").append(pluginName).append(".hpi").toString();
        } else if (pluginVersion.equals("experimental")) {
            urlString = new StringBuffer(JENKINS_UC_EXPERIMENTAL).append("/latest/").append(pluginName).append(".hpi")
                    .toString();
        } else if (pluginVersion.contains("incrementals")) {
            String[] incrementalsVersionInfo = pluginVersion.split(";");
            String groupId = incrementalsVersionInfo[1];
            String incrementalsVersion = incrementalsVersionInfo[2];

            groupId = groupId.replace(".", "/");

            String incrementalsVersionPath = new StringBuffer(pluginName).append("/").append(incrementalsVersion).append("/").
                    append(pluginName).append("-").append(incrementalsVersion).append(".hpi").toString();

            urlString = new StringBuffer(JENKINS_INCREMENTALS_REPO_MIRROR).append("/").append(groupId).append("/").
                    append(incrementalsVersionPath).toString();
        } else {
            String pathToPlugin = new StringBuffer(pluginName).append("/").append(pluginVersion).append("/").
                    append(pluginName).append(".hpi").toString();
            urlString = new StringBuffer(JENKINS_UC_DOWNLOAD).append("/plugins/").append(pathToPlugin).toString();
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
        try (JarFile pluginJpi = new JarFile(pluginFile)){
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
<<<<<<< HEAD
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
=======
                        String fileName = file.getFileName().toString();
                        bundledPlugins.add(fileName);
                        System.out.println(fileName);
                        //because can't convert a ZipPath to a file with file.toFile();
                        InputStream in = Files.newInputStream(file);
                        final File tempFile = File.createTempFile("PREFIX", "SUFFIX");
                        try (FileOutputStream out = new FileOutputStream(tempFile)) {
                            IOUtils.copy(in, out);
>>>>>>> [JENKINS JENKINS-58123] Recursively resolve plugin dependencies
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




