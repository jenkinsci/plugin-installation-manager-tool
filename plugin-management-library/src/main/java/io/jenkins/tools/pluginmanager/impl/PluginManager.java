package io.jenkins.tools.pluginmanager.impl;

import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.net.MalformedURLException;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.FileWriter;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.ArrayList;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.Charset;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.stream.Stream;
import java.util.Iterator;
import java.nio.file.PathMatcher;

import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.commons.io.IOUtils;

import java.io.FileFilter;

import org.apache.commons.io.FilenameUtils;

import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.jar.Attributes;
import java.util.Map;
import java.util.HashMap;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.io.FileUtils;

import java.net.URL;

import org.json.JSONObject;
import org.json.JSONArray;

import io.jenkins.tools.pluginmanager.config.Config;


public class PluginManager {
    private List<Plugin> plugins;
    private List<Plugin> failedPlugins;
    private File refDir;

    private String JENKINS_UC_LATEST = "";
    private final String JENKINS_UC_EXPERIMENTAL = "https://updates.jenkins.io/experimental";
    private final String JENKINS_INCREMENTALS_REPO_MIRROR = "https://repo.jenkins-ci.org/incrementals";
    private final String JENKINS_UC = "https://updates.jenkins.io";
    private final String JENKINS_UC_DOWNLOAD = JENKINS_UC + "/download";
    private final String JENKINS_UC_JSON = "https://updates.jenkins.io/update-center.json";
    private final String SEPARATOR = File.separator;

    //private final String JENKINS_WAR = "/usr/share/jenkins/jenkins.war";
    public static final String JENKINS_WAR = "/Users/natashastopa/Jenkins/Jenkins/test2/mywar-1.0-SNAPSHOT.war";

    private String jenkinsVersion;

    private File jenkinsWarFile;
    private Map<String, String> installedPluginVersions;
    private Map<String, String> bundledPluginVersions;
    Config cfg;


    public PluginManager(Config cfg) {
        plugins = new ArrayList<>();
        failedPlugins = new ArrayList();
        jenkinsWarFile = new File(JENKINS_WAR);
        installedPluginVersions = new HashMap<>();
        bundledPluginVersions = new HashMap<>();
        refDir = cfg.getPluginDir();
        this.cfg = cfg;
    }


    public void start() {
        if (!refDir.mkdir()) {
            System.out.println("Unable to create plugin directory");
        }

        jenkinsVersion = getJenkinsVersion();

        //check if version specific update center
        if (!StringUtils.isEmpty(jenkinsVersion)) {
            JENKINS_UC_LATEST = new StringBuilder("https://updates.jenkins.io/").append(jenkinsVersion).toString();
            try {
                URL url = new URL(JENKINS_UC_LATEST);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("HEAD");
                conn.connect();
                if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    JENKINS_UC_LATEST = "";
                }
            } catch (IOException e) {
                JENKINS_UC_LATEST = "";
            }
        }

        String url;

        System.out.println("Reading in plugins...");
        try {
            Scanner scanner = new Scanner(cfg.getPluginTxt());
            while (scanner.hasNextLine()) {
                String[] pluginInfo = scanner.nextLine().split(":");
                String pluginName = pluginInfo[0];
                String pluginVersion = null;
                String pluginUrl = null;
                if (pluginInfo.length >= 2) {
                    pluginVersion = pluginInfo[1];
                }
                if (pluginInfo.length == 3) {
                    pluginUrl = pluginInfo[2];
                }

                Plugin plugin = new Plugin(pluginName, pluginVersion, pluginUrl);
                plugins.add(plugin);

            }
        } catch (
                FileNotFoundException e) {
            e.printStackTrace();
        }

        createLocks(plugins);

        List<String> bundledPlugins = bundledPlugins();
        List<String> installedPlugins = installedPlugins();

        downloadPlugins(plugins);

        writeFailedPluginsToFile();

        //clean up locks


    }

    public void writeFailedPluginsToFile() {
        FileWriter fileWriter;

        try {
            fileWriter = new FileWriter("failedplugins.txt");
            if (failedPlugins.size() > 0) {
                System.out.println("Some plugins failed to download: ");
                for (Plugin plugin : failedPlugins) {
                    String failedPluginName = plugin.getName();
                    System.out.println(failedPluginName);
                    fileWriter.write(failedPluginName + "\n");
                }
            }
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void downloadPlugins(List<Plugin> plugins) {
        for (Plugin plugin : plugins) {
            boolean successfulDownload = downloadPlugin(plugin);
            if (!successfulDownload) {
                System.out.println("Unable to download " + plugin.getName() + ". Skipping...");
                failedPlugins.add(plugin);
            } else {
                resolveDependencies(plugin);
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
        }

        JSONObject plugins = updateCenterJson.getJSONObject("plugins");
        JSONObject pluginInfo = (JSONObject) plugins.get(plugin.getName());
        JSONArray dependencies = (JSONArray) pluginInfo.get("dependencies");

        if (dependencies == null || dependencies.length() == 0) {
            System.out.println(plugin.getName() + " has no dependencies");
            return;
        }

        List<Plugin> dependentPlugins = new ArrayList<>();

        System.out.println("Plugin depends on: ");

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
            String dependencyVersion = dependency.getVersion();
            if (dependency.getPluginOptional()) {
                System.out.println("Skipping optional dependency " + dependencyName);
                continue;
            }

            String installedVersion = "";
            if (installedPluginVersions.containsKey(plugin.getName())) {
                installedVersion = installedPluginVersions.get(dependencyName);
            } else if (bundledPluginVersions.containsKey(plugin.getName())) {
                installedVersion = bundledPluginVersions.get(dependencyName);
            }

            if (!StringUtils.isEmpty(installedVersion)) {
                //probably not the best way: https://stackoverflow.com/questions/6701948/efficient-way-to-compare-version-strings-in-java
                if (installedVersion.compareTo(dependencyVersion) < 0) {
                    System.out.println("Installed version of " + dependencyName + " is less than minimum " +
                            "required version of " + dependencyVersion + ", upgrading bundled dependency");
                } else {
                    System.out.println("Skipping already installed dependency ");
                }
            } else {
                downloadPlugin(dependency);
            }
        }

    }


    public boolean downloadPlugin(Plugin plugin) {
        boolean successfulDownload = doDownloadPlugin(plugin);
        if (!successfulDownload) {
            //some plugin don't follow the rules about artifact ID, i.e. docker-plugin
            String pluginName = plugin.getName();
            String newPluginName = new StringBuffer(plugin.getName()).append("-plugin").toString();
            plugin.setName(newPluginName);
            successfulDownload = doDownloadPlugin(plugin);
        }
        return successfulDownload;
    }


    public boolean doDownloadPlugin(Plugin plugin) {
        String pluginName = plugin.getName();
        String pluginVersion = plugin.getVersion();
        String pluginUrl = plugin.getUrl();

        String urlString = "";

        if (installedPluginVersions.containsKey(pluginName) && installedPluginVersions.get(pluginName).equals(pluginVersion)) {
            return true;
        }

        if (!StringUtils.isEmpty(pluginVersion)) {
            pluginVersion = "latest";
        }

        if (!StringUtils.isEmpty(pluginUrl)) {
            System.out.println("Will use url: " + pluginUrl);
        } else if (pluginVersion.equals("latest") && !StringUtils.isEmpty(JENKINS_UC_LATEST)) {
            urlString = new StringBuffer(JENKINS_UC_LATEST).append("/latest/").append(pluginName).append(".hpi").toString();
        } else if (pluginVersion.equals("experimental")) {
            urlString = new StringBuffer(JENKINS_UC_LATEST).append("/latest/").append(pluginName).append(".hpi").toString();
        } else if (pluginVersion.contains("incrementals")) {
            String[] incrementalsVersionInfo = pluginVersion.split(";");
            String groupId = incrementalsVersionInfo[1];
            String incrementalsVersion = incrementalsVersionInfo[2];

            groupId = groupId.replace(".", "/");

            String incrementalsVersionPath = new StringBuffer(incrementalsVersion).append("/").append(pluginName)
                    .append("-").append(incrementalsVersion).append(".hpi").toString();

            urlString = new StringBuffer(JENKINS_INCREMENTALS_REPO_MIRROR).append("/").append(groupId).append("/").
                    append(incrementalsVersionPath).toString();
        } else {
            String pathToPlugin = new StringBuffer(pluginName).append("/").append(pluginVersion).append("/").
                    append(pluginName).append(".hpi").toString();
            urlString = new StringBuffer(JENKINS_UC_DOWNLOAD).append("/plugins/").append(pathToPlugin).toString();
        }

        System.out.println("Downloading plugin: " + pluginName + " from url: " + urlString);

        URL url;

        try {
            url = new URL(urlString);
            System.out.println(urlString);

        } catch (MalformedURLException e) {
            e.printStackTrace();
            return false;
        }

        try {

            File pluginFile = new File(refDir + SEPARATOR + plugin.getArchiveFileName());
            FileUtils.copyURLToFile(url, pluginFile);
            //retry some number of times if fails?

            //check integrity with creation of JarFile object
            //also, this doesn't seem to be working - the local file is being created but it can't be opened/extracted
            //JarFile pluginJpi = new JarFile(pluginFile, true);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;

    }


    public String getJenkinsVersion() {
        //java -jar $JENKINS_WAR --version
        try {
            JarFile jenkinsWar = new JarFile(jenkinsWarFile);
            Manifest manifest = jenkinsWar.getManifest();
            Attributes attributes = manifest.getMainAttributes();
            return attributes.getValue("Jenkins-Version");
        } catch (IOException e) {
            e.printStackTrace();
        }

        return "";
    }


    public String getPluginVersion(File file) {
        //this is also done in the existing plugin manager from core - should I do this a similar way to that instead?
        try {
            JarFile pluginJpi = new JarFile(file);
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

        String jpiFiles = new StringBuilder(refDir.toString()).append("*.jpi").toString();
        FileFilter fileFilter = new WildcardFileFilter(jpiFiles);

        //only lists files in same directory, does not list files recursively
        File[] files = refDir.listFiles(fileFilter);
        for (File file : files) {
            String pluginName = FilenameUtils.getBaseName(file.getName());
            String pluginVersion = getPluginVersion(file);
            installedPluginVersions.put(pluginName, pluginVersion);
            installedPlugins.add(pluginName);
        }

        return installedPlugins;
    }


    public void createLocks(List<Plugin> plugins) {
        for (Plugin plugin : plugins) {
            createLock(plugin);
        }
    }

    public void createLock(Plugin plugin) {
        //in bash script, users can also pass in a version, but lock is only on plugin name
        String pluginLock = new StringBuilder(plugin.getName()).append(".lock").toString();

        File lockedFile = new File(refDir, pluginLock);

        FileChannel channel;
        FileLock lock;

        try {
            channel = new RandomAccessFile(lockedFile, "rw").getChannel();
            lock = channel.lock();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //need to return the file locks?

    }


    public List<String> bundledPlugins() {
        List<String> bundledPlugins = new ArrayList<>();

        if (jenkinsWarFile.exists()) {

            //for i in $(jar tf $JENKINS_WAR | grep -E '[^detached-]plugins.*\..pi' | sort)
            Path path = Paths.get(JENKINS_WAR);
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
                for (Iterator<Path> it = walk.iterator(); it.hasNext(); ) {
                    Path file = it.next();
                    if (matcher.matches(file)) {
                        bundledPlugins.add(file.getFileName().toString());

                        //because can't convert a ZipPath to a file with file.toFile();
                        InputStream in = Files.newInputStream(file);
                        final File tempFile = File.createTempFile("PREFIX", "SUFFIX");
                        try (FileOutputStream out = new FileOutputStream(tempFile)) {
                            IOUtils.copy(in, out);
                        }

                        String pluginVersion = getPluginVersion(tempFile);
                        tempFile.delete();

                        bundledPluginVersions.put(FilenameUtils.getBaseName(file.getFileName().toString()), pluginVersion);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        } else {
            System.out.println("War not found, installing all plugins: " + JENKINS_WAR);
        }

        return bundledPlugins;
    }


}




