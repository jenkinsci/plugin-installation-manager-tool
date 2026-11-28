package io.jenkins.tools.pluginmanager.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.jenkins.tools.pluginmanager.config.PluginInputException;
import io.jenkins.tools.pluginmanager.impl.Plugin;
import java.io.File;
import java.io.IOException;
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
        if (!fileExists(pluginTxtFile)) {
            return new ArrayList<>();
        }
        try {
            return parsePluginTxtLines(Files.readAllLines(pluginTxtFile.toPath(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new PluginInputException("Unable to open " + pluginTxtFile, e);
        }
    }

    public List<Plugin> parsePluginTxtLines(List<String> lines) {
        return lines.stream()
                .map(line -> line.substring(0, pluginTextEnd(line)).replaceAll("\\s", ""))
                .filter(line -> !line.isEmpty())
                .map(this::parsePluginLine)
                .collect(toList());
    }

    private static final java.util.regex.Pattern COMMENT = java.util.regex.Pattern.compile("(?:^|\\s+)#");

    public static int pluginTextEnd(String line) {
        java.util.regex.Matcher comment = COMMENT.matcher(line);
        return comment.find() ? comment.start() : line.length();
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
            try {
                // an empty file, or one with only comments, asks for no plugins
                if (MAPPER.readTree(pluginYamlFile).isMissingNode()) {
                    return pluginsFromYaml;
                }
                Plugins plugins = MAPPER.readValue(pluginYamlFile, Plugins.class);
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
                throw new PluginInputException("Unable to read " + pluginYamlFile + ": " + e.getMessage(), e);
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
        // in "artifactId:url", splitting on ":" also cuts the url after its scheme
        if (pluginInfo.length == 3 && pluginInfo[2].startsWith("//") && isURL(pluginInfo[1] + ":" + pluginInfo[2])) {
            pluginInfo = new String[] {pluginInfo[0], "", pluginInfo[1] + ":" + pluginInfo[2]};
        }
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
