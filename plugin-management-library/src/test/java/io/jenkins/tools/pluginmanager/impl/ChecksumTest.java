package io.jenkins.tools.pluginmanager.impl;

import io.jenkins.tools.pluginmanager.config.Config;
import java.io.File;
import java.net.URL;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThrows;

public class ChecksumTest {

    private PluginManager pm;

    @Before
    public void setUp() {
        Config cfg = Config.builder()
                .withJenkinsWar("")
                .withIsVerbose(true)
                .build();

        pm = new PluginManager(cfg);
    }

    @Test
    public void mailerPluginChecksumsMatch() {
        Plugin mailer = new Plugin("mailer", "1.32", null, null);
        mailer.setSha256Checksum("BChiuBjHIiPxWZrBuVqB+QwxKWFknoim5jnCr4I55Lc=");

        URL mailerHpi = this.getClass().getResource("mailer.hpi");
        File mailerFile = new File(mailerHpi.getFile());

        pm.verifyChecksum(mailer, mailerFile);
    }

    @Test
    public void mailerPluginInvalidChecksums() {
        Plugin mailer = new Plugin("mailer", "1.32", null, null);
        mailer.setSha256Checksum("jBChiuBjHIiPxWZrBuVqB+QwxKWFknoim5jnCr4I55Lc=");

        URL mailerHpi = this.getClass().getResource("mailer.hpi");
        File mailerFile = new File(mailerHpi.getFile());

        PluginChecksumMismatchException checksumMismatchException = assertThrows(PluginChecksumMismatchException.class, () -> {
            pm.verifyChecksum(mailer, mailerFile);
        });

        assertThat(checksumMismatchException.getMessage(),
                is("Plugin mailer:1.32 invalid checksum, expected: jBChiuBjHIiPxWZrBuVqB+QwxKWFknoim5jnCr4I55Lc=, actual: BChiuBjHIiPxWZrBuVqB+QwxKWFknoim5jnCr4I55Lc="));
    }
}
