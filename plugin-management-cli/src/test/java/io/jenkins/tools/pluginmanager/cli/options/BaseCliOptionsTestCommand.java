package io.jenkins.tools.pluginmanager.cli.options;

import io.jenkins.tools.pluginmanager.cli.options.BaseCliOptions;
import picocli.CommandLine;

import java.util.concurrent.Callable;

/**
 * Tests for {@link io.jenkins.tools.pluginmanager.cli.options.BaseCliOptions}
 * @author Oleg Nenashev
 * @since TODO
 */
public class BaseCliOptionsTestCommand implements Callable<Integer> {

    @CommandLine.Mixin
    public BaseCliOptions options;


    @Override
    public Integer call() throws Exception {
        // NOOP
        return 0;
    }
}
