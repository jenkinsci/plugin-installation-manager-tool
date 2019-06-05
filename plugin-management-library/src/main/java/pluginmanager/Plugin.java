package pluginmanager;

public class Plugin {
    private String name;
    private String version;
    private String url;


    public Plugin(String name, String version, String url) {
        this.name = name;
        this.version = version;
        this.url = url;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setUrl(String url) {
        this.url = url;
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
}
