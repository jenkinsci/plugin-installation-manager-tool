package io.jenkins.tools.pluginmanager.cli;

import io.jenkins.tools.pluginmanager.impl.Plugin;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.yaml.snakeyaml.Yaml;
import io.jenkins.tools.pluginmanager.cli.PluginInputFormatException;

import static java.util.stream.Collectors.toList;

public class PluginParser {

    public List<Plugin> parsePluginsFromCliOption(String[] plugins) {
        if (plugins == null) {
            return new ArrayList<>();
        }
        return Arrays.stream(plugins)
                .map(this::parsePluginLine)
                .collect(toList());
    }

    /**
     * Given a txt file that lists the plugins (in the format artifactId:version:url) to be downloaded on each line,
     * returns a list of Plugin objects representing the requested plugins
     *
     * @param pluginTxtFile text file containing plugins to be parsed
     * @return list of plugins contained in the text file
     */
    public List<Plugin> parsePluginTxtFile(File pluginTxtFile) {
        List<Plugin> pluginsFromTxt = new ArrayList<>();
        if (fileExists(pluginTxtFile)) {
            try (BufferedReader bufferedReader = Files.newBufferedReader(pluginTxtFile.toPath(),
                    StandardCharsets.UTF_8)) {
                List<Plugin> pluginsFromFile = bufferedReader.lines()
                        .map(line -> line.replaceAll("\\s#+.*", ""))
                        .map(line -> line.replaceAll("\\s", ""))
                        .filter(line -> !line.startsWith("#"))
                        .filter(line -> line.length() > 0)
                        .map(line -> parsePluginLine(line))
                        .collect(toList());
                pluginsFromTxt.addAll(pluginsFromFile);
            } catch (IOException e) {
                System.out.println("Unable to open " + pluginTxtFile);
            }
        }

        return pluginsFromTxt;
    }

    /**
     * Given a Jenkins yaml file with a plugins root element, will parse the yaml file and create a list of requested
     * plugins
     *
     * @param pluginYamlFile yaml file to parse
     * @return list of plugins contained in yaml file
     */
    public List<Plugin> parsePluginYamlFile(File pluginYamlFile) {
        List<Plugin> pluginsFromYaml = new ArrayList<>();
        if (fileExists(pluginYamlFile)) {
            Yaml yaml = new Yaml();
            try (InputStream inputStream = new FileInputStream(pluginYamlFile)) {
                Map map = (Map) yaml.load(inputStream);
                List plugins = (List) map.get("plugins");
                for (Object p : plugins) {
                    Map pluginInfo = (Map) p;
                    Object nameObject = pluginInfo.get("artifactId");
                    String name = nameObject == null ? null : nameObject.toString();
                    if (StringUtils.isEmpty(name)) {
                        throw new PluginInputFormatException("ArtifactId is required");
                    }
                    Object groupIdObject = pluginInfo.get("groupId");
                    String groupId = groupIdObject == null ? null : groupIdObject.toString();
                    Map pluginSource = (Map) pluginInfo.get("source");
                    String incrementalsVersion = null;
                    Plugin plugin;
                    if (pluginSource == null && !StringUtils.isEmpty(groupId)) {
                        throw new PluginInputFormatException("Version must be input for " + name);
                    } else if (pluginSource == null) {
                        plugin = new Plugin(name, "latest", null, null);
                    } else {
                        Object versionObject = pluginSource.get("version");
                        if (!StringUtils.isEmpty(groupId) && versionObject == null) {
                            throw new PluginInputFormatException("Version must be input for " + name);
                        }
                        String version = versionObject == null ? "latest" : versionObject.toString();
                        Object urlObject = pluginSource.get("url");
                        String url;
                        if (urlObject != null && isURL(urlObject.toString())) {
                            url = urlObject.toString();
                        } else {
                            url = null;
                        }
                        plugin = new Plugin(name, version, url, groupId);
                    }
                    pluginsFromYaml.add(plugin);
                }
            } catch (IOException e) {
                System.out.println("Unable to open " + pluginsFromYaml);
            }
        }
        return pluginsFromYaml;
    }

    /**
     * Checks if a file exists
     *
     * @param pluginFile file of which to check the existence
     * @return true if file exists, false otherwise
     */
    public boolean fileExists(File pluginFile) {
        if (pluginFile == null) {
            return false;
        }
        if (Files.exists(pluginFile.toPath())) {
            System.out.println("Reading in plugins from " + pluginFile + "\n");
            return true;
        }
        System.out.println(pluginFile + " file does not exist");
        return false;
    }

    /**
     * For each plugin specified in the CLI using the --plugins option or line in the plugins.txt file, creates a Plugin
     * object containing the  plugin name (required), version (optional), and url (optional)
     *
     * @param pluginLine plugin information to parse
     * @return plugin object containing name, version, and/or url
     */
    private Plugin parsePluginLine(String pluginLine) {
        String[] pluginInfo = pluginLine.split(":", 3);
        String pluginName = pluginInfo[0];
        String pluginVersion = "latest";
        String pluginUrl = null;
        String groupId = null;

        // "http, https, ftp" are valid

        if (pluginInfo.length >= 2) {
            pluginVersion = pluginInfo[1];
            if (pluginVersion.contains("incrementals")) {
                String[] incrementalsVersionInfo = pluginVersion.split(";");
                groupId = incrementalsVersionInfo[1];
                pluginVersion = incrementalsVersionInfo[2];
            }
        }

        if (pluginInfo.length >= 3) {
            pluginVersion = pluginInfo[1];
            if (isURL(pluginInfo[2])) {
                pluginUrl = pluginInfo[2];
            } else {
                System.out.println("Invalid URL "+ pluginInfo[2] +" , will ignore");
            }
        }
        return new Plugin(pluginName, pluginVersion, pluginUrl, groupId);
    }

    public static boolean isURL(String url) {
        try {
            new URL(url);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
