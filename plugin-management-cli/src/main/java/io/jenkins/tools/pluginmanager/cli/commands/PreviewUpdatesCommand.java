package io.jenkins.tools.pluginmanager.cli.commands;

import io.jenkins.tools.pluginmanager.config.Config;
import io.jenkins.tools.pluginmanager.impl.PluginManager;
import picocli.CommandLine;

/**
 * Command that lists the plugin download action without actually downloading them.
 * It is a dry-run edition of {@link InstallPluginsCommand}
 * It is an equivalent of the "--no-download" option in Plugin Installation Manager before 3.0.
 *
 * @author Oleg Nenashev
 * @since 3.0
 */
@CommandLine.Command(
        name = "preview-updates",
        description = "Preview the plugin update without actually installing the plugins",
        mixinStandardHelpOptions = true)
public class PreviewUpdatesCommand extends InstallPluginsCommand {

    @Override
    public Config.Configurator getConfigurator() {
        return (builder) -> builder.withDoDownload(false);
    }

}
