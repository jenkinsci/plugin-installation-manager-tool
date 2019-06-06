package pluginmanager;

import java.util.jar.JarFile;

public class Plugin {
    private String name;
    private String version;
    private String url;
    private String archiveFileName;
    private JarFile jarFile;


    public Plugin(String name, String version, String url) {
        this.name = name;
        this.version = version;
        this.url = url;
        this.archiveFileName = new StringBuffer(name).append(".jpi").toString();
    }

    public void setName(String name) {
        this.name = name;
        setArchiveFileName(name + ".jpi");
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setJarFile(JarFile jarFile) {
        this.jarFile = jarFile;
    }

    public void setArchiveFileName(String archiveFileName) {
        this.archiveFileName = archiveFileName;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getUrl() {
        return url;
    }

    public String getArchiveFileName() {
        return archiveFileName;
    }

    public JarFile getJarFile() {
        return jarFile;
    }
}
