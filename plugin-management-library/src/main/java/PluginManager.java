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


public class PluginManager {
    private List<String> plugins = new ArrayList<>();
    private File refDir = new File("./plugins");
    private final String JENKINS_WAR = "/usr/share/jenkins/jenkins.war";

    public PluginManager() {

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

        //the script created files by appending .lock; is using the FileLock class a better way to do this?
        createLocks(plugins);

        bundledPlugins();
        installedPlugins();
    }

    public void installedPlugins() {
        String jpiFiles = new StringBuilder(refDir.toString()).append("*.jpi").toString();
        FileFilter fileFilter = new WildcardFileFilter(jpiFiles);
        File[] files = refDir.listFiles(fileFilter);
        for (File file: files) {
            String basename = FilenameUtils.getBaseName(file.getName());
            //I'm not sure if get_plugin_version is supposed to actually return the plugin version?
            //This didn't look like it actually called a method or referred to a variable
            String newFileName = new StringBuilder("get_plugin_version").append(basename).toString();
            file.renameTo(new File(newFileName));
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
        File jenkinsWarFile = new File(JENKINS_WAR);

        if (jenkinsWarFile.exists()) {
            //install-plugins.sh uses the pid, but this looked less straightforward with Java
            double random = Math.random() * 1000 + 1;
            String tempPluginDir = new StringBuilder("/tmp/plugintemp.").append(random).toString();

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

                //I couldn't get any PathMatcher I tried to work
                //Should I use the WildCardFileFilter or RegEx instead?
                PathMatcher matcher = warFS.getPathMatcher("glob:*.{hpi}");
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

    }

}




