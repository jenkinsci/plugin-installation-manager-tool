package io.jenkins.tools.pluginmanager.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PluginExceptionTest {

    @Test
    void originatorChainIsNullWhenPluginIsMissing() {
        PluginException exception = new PluginNotFoundException(null, "was not found in the update center or archives");

        assertThat(exception.getOriginatorPluginAndDependencyChain()).isNull();
        assertThat(exception.getMessage()).isEqualTo("was not found in the update center or archives");
    }

    @Test
    void originatorChainPrefixesMessageWhenPluginIsPresent() {
        Plugin plugin = new Plugin("break-it", "4.2", null, null);
        PluginException exception = new PluginNotFoundException(plugin, "was not found in the update center or archives");

        assertThat(exception.getOriginatorPluginAndDependencyChain()).isEqualTo("Plugin break-it:4.2 ");
        assertThat(exception.getMessage()).isEqualTo("Plugin break-it:4.2 was not found in the update center or archives");
    }
}
