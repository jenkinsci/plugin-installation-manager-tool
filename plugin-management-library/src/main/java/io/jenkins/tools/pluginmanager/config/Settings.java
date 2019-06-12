package io.jenkins.tools.pluginmanager.config;

import java.io.File;

public class Settings {

    public static File DEFAULT_PLUGIN_TXT = new File(System.getProperty("user.dir") + File.separator + "plugins.txt");
    public static File DEFAULT_PLUGIN_DIR = new File(System.getProperty("user.dir")+ File.separator + "plugins");

}
