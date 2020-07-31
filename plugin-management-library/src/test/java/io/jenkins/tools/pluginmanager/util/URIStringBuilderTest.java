package io.jenkins.tools.pluginmanager.util;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;


public class URIStringBuilderTest {

    @Test
    public void constructorTest() {
        String result = new URIStringBuilder("http://bob.com")
                .build();
        assertThat(result).isEqualTo("http://bob.com");
    }

    @Test
    public void constructorDomainInPathTest() {
        String result = new URIStringBuilder("http://")
                .addPath("bob.com")
                .build();
        assertThat(result).isEqualTo("http://bob.com");
    }

    @Test
    public void constructorWithTrailingSlashTest() {
        String result = new URIStringBuilder("http://bob.com/")
                .build();
        assertThat(result).isEqualTo("http://bob.com");
    }

    @Test
    public void dontAddPathTest() {
        String result = new URIStringBuilder("http://bob.com")
                .build();
        assertThat(result).isEqualTo("http://bob.com");
    }

    @Test
    public void addPathTest() {
        String result = new URIStringBuilder("http://bob.com")
                .addPath("path/to")
                .addPath("file.html")
                .build();
        assertThat(result).isEqualTo("http://bob.com/path/to/file.html");
    }

    @Test
    public void addPathWithBlankSectionTest() {
        String result = new URIStringBuilder("http://bob.com")
                .addPath("path/to")
                .addPath("")
                .addPath("file.html")
                .build();
        assertThat(result).isEqualTo("http://bob.com/path/to/file.html");
    }

    @Test
    public void buildTest() {
        String result = new URIStringBuilder("http://bob.com")
                .addPath("file.html")
                .build();
        assertThat(result).isEqualTo("http://bob.com/file.html");
    }
}
