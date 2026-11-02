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

    @Test
    void dependencyNotFoundMessageHighlightsDeclaringParent() {
        Plugin parent = new Plugin("git-forensics", "3.2138", null, null);
        Plugin child = new Plugin("plugin-util-api", "6.1183", null, null);
        child.setParent(parent);

        PluginNotFoundException exception = new PluginNotFoundException(child, "was not found in the update center or archives");

        assertThat(exception.getMessage())
                .isEqualTo("Plugin git-forensics:3.2138 depends on plugin-util-api:6.1183, which was not found in the update center or archives");
    }

    @Test
    void transitiveDependencyNotFoundMessageIncludesIntermediates() {
        Plugin root = new Plugin("git-forensics", "3.2138", null, null);
        Plugin intermediate = new Plugin("forensics-api", "2.0", null, null);
        intermediate.setParent(root);
        Plugin leaf = new Plugin("plugin-util-api", "6.1183", null, null);
        leaf.setParent(intermediate);

        PluginNotFoundException exception = new PluginNotFoundException(leaf, "was not found in the update center or archives");

        assertThat(exception.getMessage())
                .isEqualTo("Plugin git-forensics:3.2138 (via forensics-api:2.0) depends on plugin-util-api:6.1183, which was not found in the update center or archives");
    }
}
