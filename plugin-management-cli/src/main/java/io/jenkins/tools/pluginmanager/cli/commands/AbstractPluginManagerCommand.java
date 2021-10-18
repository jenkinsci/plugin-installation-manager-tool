package io.jenkins.tools.pluginmanager.cli.commands;

import io.jenkins.tools.pluginmanager.cli.options.BaseCliOptions;
import io.jenkins.tools.pluginmanager.config.Config;
import io.jenkins.tools.pluginmanager.impl.PluginManager;
import picocli.CommandLine;

import java.util.concurrent.Callable;

/**
 * @author Oleg Nenashev
 * @since TODO
 */
public abstract class AbstractPluginManagerCommand implements Callable<Integer> {

    @CommandLine.Mixin
    public BaseCliOptions options;

    protected Config config;

    public Config getConfig() {
        if (config == null) {
            config = options.setup();
        }
        return config;
    }

    public PluginManager getPluginManager() {
        PluginManager pm = new PluginManager(getConfig());
        return pm;
    }

    @Override
    public Integer call() throws Exception {
        try (PluginManager pm = getPluginManager()) {
            return call(pm, getConfig());
        } catch (Exception e) {
            if (options.isVerbose()) {
                e.printStackTrace();
            }
            System.err.println(e.getMessage());
            return 1;
        }
    }

    public abstract Integer call(PluginManager pm, Config config) throws Exception;

}
