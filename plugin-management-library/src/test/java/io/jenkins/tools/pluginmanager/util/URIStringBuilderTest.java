package io.jenkins.tools.pluginmanager.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;


class URIStringBuilderTest {

    @Test
    void constructorTest() {
        String result = new URIStringBuilder("http://bob.com")
                .build();
        assertThat(result).isEqualTo("http://bob.com");
    }

    @Test
    void constructorDomainInPathTest() {
        String result = new URIStringBuilder("http://")
                .addPath("bob.com")
                .build();
        assertThat(result).isEqualTo("http://bob.com");
    }

    @Test
    void constructorWithTrailingSlashTest() {
        String result = new URIStringBuilder("http://bob.com/")
                .build();
        assertThat(result).isEqualTo("http://bob.com");
    }

    @Test
    void dontAddPathTest() {
        String result = new URIStringBuilder("http://bob.com")
                .build();
        assertThat(result).isEqualTo("http://bob.com");
    }

    @Test
    void addPathTest() {
        String result = new URIStringBuilder("http://bob.com")
                .addPath("path/to")
                .addPath("file.html")
                .build();
        assertThat(result).isEqualTo("http://bob.com/path/to/file.html");
    }

    @Test
    void addPathWithBlankSectionTest() {
        String result = new URIStringBuilder("http://bob.com")
                .addPath("path/to")
                .addPath("")
                .addPath("file.html")
                .build();
        assertThat(result).isEqualTo("http://bob.com/path/to/file.html");
    }

    @Test
    void buildTest() {
        String result = new URIStringBuilder("http://bob.com")
                .addPath("file.html")
                .build();
        assertThat(result).isEqualTo("http://bob.com/file.html");
    }
}
