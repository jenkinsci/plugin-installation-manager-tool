package io.jenkins.tools.pluginmanager.cli.commands;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import io.jenkins.tools.pluginmanager.cli.options.BaseCliOptions;
import io.jenkins.tools.pluginmanager.config.Config;
import io.jenkins.tools.pluginmanager.impl.PluginManager;
import picocli.CommandLine;

import java.util.concurrent.Callable;

/**
 * Base command implementation for Plugin Installation Manager.
 * @author Oleg Nenashev
 * @since 3.0
 */
public abstract class AbstractPluginManagerCommand implements Callable<Integer> {

    @CommandLine.Mixin
    public BaseCliOptions options;

    /**
     * Skips execution, mostly for test purposes
     */
    private boolean skipExecution;

    protected Config config;

    public Config getConfig() {
        if (config == null) {
            config = options.setupWithConfig(getConfigurator());
        }
        return config;
    }

    public PluginManager getPluginManager() {
        PluginManager pm = new PluginManager(getConfig());
        return pm;
    }

    @Override
    public Integer call() throws Exception {
        if (skipExecution) {
            return 0;
        }

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

    /**
     * Gets configurator that sets additional options.
     */
    @CheckForNull
    public Config.Configurator getConfigurator() {
        return null;
    }

    public boolean isSkipExecution() {
        return skipExecution;
    }

    public AbstractPluginManagerCommand withSkipExecution(boolean skipExecution) {
        this.skipExecution = skipExecution;
        return this;
    }
}
