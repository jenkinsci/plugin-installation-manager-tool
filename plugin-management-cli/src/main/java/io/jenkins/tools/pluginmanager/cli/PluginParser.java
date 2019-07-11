package io.jenkins.tools.pluginmanager.cli;

import io.jenkins.tools.pluginmanager.impl.Plugin;
import io.jenkins.tools.pluginmanager.impl.Plugins;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.validator.routines.UrlValidator;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import static java.util.stream.Collectors.toList;

public class PluginParser {
    public List<Plugin> parsePluginsFromCliOption(String[] plugins) {
        List<Plugin> cliPlugins = Arrays.stream(plugins)
                .map(this::parsePluginLine)
                .collect(toList());
        return cliPlugins;
    }


    public List<Plugin> parsePluginTxtFile(File pluginTxtFile) {
        List<Plugin> pluginsFromTxt = new ArrayList<>();

        if (fileExists(pluginTxtFile)) {
            try (BufferedReader bufferedReader = Files.newBufferedReader(pluginTxtFile.toPath(),
                    StandardCharsets.UTF_8)) {
                List<Plugin> pluginsFromFile = bufferedReader.lines()
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

    public List<Plugin> parsePluginYamlFile(File pluginYamlFile) {
        List<Plugin> pluginsFromYaml = new ArrayList<>();
        /*
        if (fileExists(pluginYamlFile)) {
            final Constructor pluginConstructor = new Constructor(Plugins.class);
            final TypeDescription typeDescription = new TypeDescription(Plugin.class);
            Yaml yaml = new Yaml(new Constructor(Plugins.class));
            try (InputStream inputStream = new FileInputStream(pluginYamlFile)) {
                Iterable<Object> itr = yaml.loadAll(inputStream);
                for (Object o: itr) {
                    System.out.println("Loaded object type: " + o.getClass());
                    System.out.println(o);
                }
            }
            catch (IOException e) {
                System.out.println("Unable to open " + pluginsFromYaml);
            }
        }
        */
        return pluginsFromYaml;
    }


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
        String[] pluginInfo = pluginLine.split(":");
        String pluginName = pluginInfo[0];
        String pluginVersion = "latest";
        String pluginUrl = null;

        // "http, https, ftp" are valid
        UrlValidator urlValidator = new UrlValidator();

        if (pluginInfo.length == 2) {
            if (urlValidator.isValid(pluginInfo[1])) {
                pluginUrl = pluginInfo[1];
            } else {
                pluginVersion = pluginInfo[1];
            }
        }

        if (pluginInfo.length >= 3) {
            pluginVersion = pluginInfo[1];
            if (urlValidator.isValid(pluginInfo[2])) {
                pluginUrl = pluginInfo[2];
            } else {
                System.out.println("Invalid URL entered, will ignore");
            }
        }
        return new Plugin(pluginName, pluginVersion, pluginUrl);
    }
}
