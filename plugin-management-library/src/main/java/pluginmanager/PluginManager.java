package pluginmanager;

import java.io.File;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.io.FileNotFoundException;
import java.io.IOException;
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
import java.nio.channels.OverlappingFileLockException;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.io.FileUtils;

import java.net.URL;

import org.json.JSONException;
import org.json.JSONObject;


public class PluginManager {
    private List<Plugin> plugins;
    private List<Plugin> failedPlugins;
    private File refDir;
    private final String JENKINS_WAR = "/usr/share/jenkins/jenkins.war";
    private String JENKINS_UC_LATEST = "";
    private final String JENKINS_UC_EXPERIMENTAL = "https://updates.jenkins.io/experimental";
    private final String JENKINS_INCREMENTALS_REPO_MIRROR = "https://repo.jenkins-ci.org/incrementals";
    private final String JENKINS_UC = "https://updates.jenkins.io";
    private final String JENKINS_UC_DOWNLOAD = JENKINS_UC + "/download";
    private final String JENKINS_UC_JSON = "https://updates.jenkins.io/update-center.json";
    private final String SEPARATOR = File.separator;

    private String jenkinsVersion;

    private File jenkinsWarFile;
    private Map<String, String> installedPluginVersions;


    public PluginManager() {
        plugins = new ArrayList<>();
        failedPlugins = new ArrayList();
        jenkinsWarFile = new File(JENKINS_WAR);
        installedPluginVersions = new HashMap<>();
        refDir = new File("." + SEPARATOR + "plugins");
    }


    public void start() {
        if (!refDir.mkdir()) {
            System.out.println("Unable to create plugin directory");
        }


        jenkinsVersion = getJenkinsVersion();
        String url;

        if (!StringUtils.isEmpty(jenkinsVersion)) {
            JENKINS_UC_LATEST = new StringBuilder("https://updates.jenkins.io").append(jenkinsVersion).toString();
        }


        System.out.println("Reading in plugins...");
        try {
            Scanner scanner = new Scanner(new File("plugins.txt"));
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
    }


    public void downloadPlugins(List<Plugin> plugins) {
        for (Plugin plugin : plugins) {
            boolean successfulDownload = downloadPlugin(plugin);
            if (!successfulDownload) {
                System.out.println("Unable to download " + plugin.getName() + ". Skipping...");
            } else {
                resolveDependencies(plugin);
            }
        }
    }


    public void resolveDependencies(Plugin plugin) {
        URL updateCenter;

        try {
            updateCenter = new URL(JENKINS_UC_JSON);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return;
        }

        try {
            JSONObject json = new JSONObject(IOUtils.toString(updateCenter, Charset.forName("UTF-8")));
            System.out.println(json.toString());
        } catch (IOException e) {
            e.printStackTrace();
            return;
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

        if (!StringUtils.isEmpty(pluginUrl)) {
            System.out.println("Will use url: " + pluginUrl);
        } else if (pluginVersion.equals("latest") && !StringUtils.isEmpty(JENKINS_UC_LATEST)) {
            urlString = new StringBuffer(JENKINS_UC_LATEST).append("/latest/").append(pluginName).append("hpi").toString();
        } else if (pluginVersion.equals("experimental")) {
            urlString = new StringBuffer(JENKINS_UC_LATEST).append("/latest/").append(pluginName).append("hpi").toString();
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

        } catch (MalformedURLException e) {
            e.printStackTrace();
            return false;
        }

        try {
            File pluginFile = new File("." + SEPARATOR + plugin.getArchiveFileName());
            FileUtils.copyURLToFile(url, pluginFile);
            //retry some number of times if fails?
            //also, this doesn't seem to be working - the local file is being created but it can't be opened/extracted

            //check integrity with creation of JarFile object
            JarFile pluginJpi = new JarFile(pluginFile, true);
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
            return attributes.getValue("Implementation-Version");
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
            Path tempPluginDir;
            try {
                tempPluginDir = Files.createTempDirectory("plugintemp");
            } catch (IOException e) {
                e.printStackTrace();
            }

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
                        bundledPlugins.add(file.toString());
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




