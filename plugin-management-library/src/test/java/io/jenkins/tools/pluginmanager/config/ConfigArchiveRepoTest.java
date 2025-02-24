package io.jenkins.tools.pluginmanager.config;

import java.net.URL;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ConfigArchiveRepoTest {

    @Test
    public void defaultArchiveRepoMirrorTest() {
        // default archive url
        Config config = Config.builder().build();
        assertEquals(Settings.DEFAULT_ARCHIVE_REPO_MIRROR, config.getJenkinsArchiveRepoMirror());
    }

    @Test
    public void setCustomArchiveRepoMirrorTest() throws Exception {
        // custom archive url
        URL customUrl = new URL("https://mirrors.jenkins-ci.org/");
        Config config = Config.builder()
                .withJenkinsArchiveRepoMirror(customUrl)
                .build();
        assertEquals(customUrl, config.getJenkinsArchiveRepoMirror());
    }
}
