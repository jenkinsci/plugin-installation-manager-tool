package config;

import java.io.File;

public class Config {
    private File pluginTxt;
    private File pluginDir;

    public void setPluginTxt(File pluginTxt) {
        this.pluginTxt = pluginTxt;
    }

    public File getPluginTxt() {
        return pluginTxt;
    }

    public void setPluginDir(File pluginDir) {
        this.pluginDir = pluginDir;
    }

    public File getPluginDir() {
        return pluginDir;
    }
}
