package io.jenkins.tools.pluginmanager.util;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.util.VersionNumber;
import io.jenkins.tools.pluginmanager.impl.DownloadPluginException;
import io.jenkins.tools.pluginmanager.impl.Plugin;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.apache.commons.lang3.StringUtils;

public class ManifestTools {

    public static Plugin readPluginFromFile(File file) throws IOException {
        Plugin plugin = new Plugin(file.getName(), "undefined", null, null);


        // TODO: refactor code so that we read the manifest only once
        String version = getAttributeFromManifest(file, "Plugin-Version");
        if (!StringUtils.isEmpty(version)) {
            plugin.setVersion(new VersionNumber(version));
        }
        plugin.setJenkinsVersion(getAttributeFromManifest(file, "Jenkins-Version"));


        String dependencyString = getAttributeFromManifest(file, "Plugin-Dependencies");
        if (StringUtils.isEmpty(dependencyString)) {
            // not all plugin Manifests contain the Plugin-Dependencies field
            return plugin;
        }

        String[] dependencies = dependencyString.split(",");
        List<Plugin> dependentPlugins = new ArrayList<>();
        for (String dependency : dependencies) {
            if (!dependency.contains("resolution:=optional")) {
                String[] pluginInfo = dependency.split(":");
                String pluginName = pluginInfo[0];
                String pluginVersion = pluginInfo[1];
                Plugin dependentPlugin = new Plugin(pluginName, pluginVersion, null, null);
                dependentPlugins.add(dependentPlugin);
                dependentPlugin.setParent(plugin);
            }
        }
        plugin.setDependencies(dependentPlugins);

        return plugin;
    }

    public static Attributes getAttributesFromManifest(File file) throws IOException {
        final Manifest mf;
        if (file.isDirectory()) { // Already exploded
            try (FileInputStream istream = new FileInputStream(new File(file, "META-INF/MANIFEST.MF"))) {
                mf = new Manifest(istream);
            }
        } else {
            try (JarFile jarFile = new JarFile(file)) {
                mf = jarFile.getManifest();
            }
        }
        return mf.getMainAttributes();
    }

    /**
     * Given a jar file and a key to retrieve from the jar's MANIFEST.MF file, confirms that the file is a jar returns
     * the value matching the key
     *
     * @param file jar file to get manifest from
     * @param key  key matching value to retrieve
     * @return value matching the key in the jar file
     */
    @CheckForNull
    public static String getAttributeFromManifest(File file, String key) {
        try {
            return getAttributesFromManifest(file).getValue(key);
        } catch (IOException e) {
            System.err.println("Unable to open " + file);
            if (key.equals("Plugin-Dependencies")) {
                throw new DownloadPluginException("Unable to read " + key + " from the manifest of " + file, e);
            }
        }
        return null;
    }

}
