package io.jenkins.tools.pluginmanager.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.jenkins.tools.pluginmanager.config.PluginInputException;
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
import org.apache.commons.lang3.StringUtils;

import static java.util.stream.Collectors.toList;

public class PluginListParser {

    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER))
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final boolean verbose;

    public PluginListParser(boolean verbose) {
        this.verbose = verbose;
    }

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
                        .map(this::parsePluginLine)
                        .collect(toList());
                pluginsFromTxt.addAll(pluginsFromFile);
            } catch (IOException e) {
                System.err.println("Unable to open " + pluginTxtFile);
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
            try (InputStream inputStream = new FileInputStream(pluginYamlFile)) {
                Plugins plugins = MAPPER.readValue(inputStream, Plugins.class);
                for (PluginInfo pluginInfo : plugins.getPlugins()) {
                    String name = pluginInfo.getArtifactId();
                    if (StringUtils.isEmpty(name)) {
                        throw new PluginInputException("ArtifactId is required");
                    }
                    String groupId = pluginInfo.getGroupId();
                    Source pluginSource = pluginInfo.getSource();
                    Plugin plugin;
                    if (pluginSource == null && StringUtils.isNotEmpty(groupId)) {
                        throw new PluginInputException("Version must be input for " + name);
                    } else if (pluginSource == null) {
                        plugin = new Plugin(name, "latest", null, null);
                    } else {
                        String version = pluginSource.getVersion();
                        if (StringUtils.isNotEmpty(groupId) && version == null) {
                            throw new PluginInputException("Version must be input for " + name);
                        }
                        if (version == null) {
                            version = "latest";
                        }
                        String url = pluginSource.getUrl();
                        if (!isURL(url)) {
                            url = null;
                        }
                        plugin = new Plugin(name, version, url, groupId);
                    }
                    pluginsFromYaml.add(plugin);
                }
            } catch (IOException e) {
                System.err.println("Unable to open " + pluginsFromYaml);
                e.printStackTrace();
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
            if (verbose) {
                System.err.println("Reading in plugins from " + pluginFile + "\n");
            }
            return true;
        }
        System.err.println(pluginFile + " file does not exist");
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
            if (isURL(pluginInfo[2])) {
                pluginUrl = pluginInfo[2];
            } else {
                System.err.println("Invalid URL " + pluginInfo[2] + " , will ignore");
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
