package io.jenkins.tools.pluginmanager.parsers;

import io.jenkins.tools.pluginmanager.impl.Plugin;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class StdOutPluginOutputConverterTest extends BaseParserTest {

    @Test
    public void convert() {
        List<Plugin> plugins = singletonList(plugin("mailer", "1.31"));
        String converted = new StdOutPluginOutputConverter("Plugins").convert(plugins);
        assertThat(converted)
                .isEqualTo(String.format("Plugins%nmailer 1.31%n"));
    }

    @Test
    public void covertEmptyList() {
        String converted = new StdOutPluginOutputConverter("Plugins").convert(Collections.emptyList());
        assertThat(converted)
                .isEqualTo(String.format("Plugins%n-none-%n"));
    }
}
