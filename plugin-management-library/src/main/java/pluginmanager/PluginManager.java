package pluginmanager;

import java.io.File;
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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.stream.Stream;
import java.util.Iterator;
import java.nio.file.PathMatcher;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import java.io.FileFilter;
import org.apache.commons.io.FilenameUtils;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.jar.Attributes;
import java.util.Map;
import java.util.HashMap;



public class PluginManager {
    private List<String> plugins = new ArrayList<>();
    private File refDir = new File("./plugins");
    private final String JENKINS_WAR = "/usr/share/jenkins/jenkins.war";
    private File jenkinsWarFile;
    private Map<String, String> installedPluginVersions;

    public PluginManager() {
        jenkinsWarFile = new File(JENKINS_WAR);
        installedPluginVersions = new HashMap<>();

    }

    public void start() {
        refDir.mkdir();

        System.out.println("Reading in plugins...");
        try {
            Scanner scanner = new Scanner(new File("plugins.txt"));
            String plugin;

            while (scanner.hasNext()) {
                plugin = scanner.nextLine();
                plugins.add(plugin);
            }
        } catch (
                FileNotFoundException e) {
            e.printStackTrace();
        }


        //will update with Java FileLock
        createLocks(plugins);

        bundledPlugins();
        installedPlugins();
        getJenkinsVersion();

        downloadPlugins("");

    }


    public void downloadPlugins(String plugin) {
        String[] pluginInfo = plugin.split(":");


        doDownloadPlugin();
    }



    public void doDownloadPlugin() {

    }

    public String getJenkinsVersion() {
        //java -jar $JENKINS_WAR --version
        try {
            JarFile jenkinsWar = new JarFile(jenkinsWarFile);
            Manifest manifest= jenkinsWar.getManifest();
            Attributes attributes = manifest.getMainAttributes();
            return attributes.getValue("Implementation-Version");
        }
        catch (IOException e) {
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
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    public void installedPlugins() {
        String jpiFiles = new StringBuilder(refDir.toString()).append("*.jpi").toString();
        FileFilter fileFilter = new WildcardFileFilter(jpiFiles);

        //only lists files in same directory, does not list files recursively
        File[] files = refDir.listFiles(fileFilter);
        for (File file: files) {
            String pluginName = file.getName();  //includes file extension
            String pluginVersion = getPluginVersion(file);
            installedPluginVersions.put(pluginName, pluginVersion);
        }
    }


    public void createLocks(List<String> plugins) {
        for (String plugin : plugins) {
            createLock(plugin);
        }
    }

    public void createLock(String plugin) {
        String pluginLock = new StringBuilder(plugin).append(".lock").toString();
        File lockedFile = new File(refDir, pluginLock);
    }


    public void bundledPlugins() {
        if (jenkinsWarFile.exists()) {
            Path tempPluginDir;
            try {
                tempPluginDir = Files.createTempDirectory("plugintemp");
            }
            catch (IOException e) {
                e.printStackTrace();
            }

            //for i in $(jar tf $JENKINS_WAR | grep -E '[^detached-]plugins.*\..pi' | sort)
            Path path = Paths.get(JENKINS_WAR);
            URI jenkinsWarUri;
            try {
                jenkinsWarUri = new URI("jar:" + path.toUri());
            }

            catch (URISyntaxException e) {
                e.printStackTrace();
                return;
            }

            //walk through war contents and find bundled plugins
            List<Path> bundledPlugins = new ArrayList<>();
            try (FileSystem warFS = FileSystems.newFileSystem(jenkinsWarUri, Collections.<String, Object>emptyMap())) {
                Path warPath = warFS.getPath("/").getRoot();
                PathMatcher matcher = warFS.getPathMatcher("regex:.*[^detached-]plugins.*\\.\\w+pi");
                Stream<Path> walk = Files.walk(warPath);
                for (Iterator<Path> it = walk.iterator(); it.hasNext();) {
                    Path file = it.next();
                    if (matcher.matches(file)) {
                        System.out.println(it.next());
                    }
                }
            }
            catch (IOException e) {
                e.printStackTrace();
            }

        } else {
            System.out.println("War not found, installing all plugins: " + JENKINS_WAR);
        }

    }

    public void download(String plugin) {
        return;
    }

}




