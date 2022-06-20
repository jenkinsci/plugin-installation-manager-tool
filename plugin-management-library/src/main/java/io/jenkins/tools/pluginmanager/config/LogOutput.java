package io.jenkins.tools.pluginmanager.config;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Output for user informational messages. Messages are written to standard error so not to interfere with
 * primary cli output.
 */
public class LogOutput {

    private final boolean verbose;

    /**
     * Create new log output.
     *
     * @param verbose true if verbose messages are enabled
     */
    public LogOutput(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Output message.
     *
     * @param message message to output
     */
    public void printMessage(String message) {
        System.err.println(message);
    }

    /**
     * Output message if verbose enabled.
     *
     * @param message message to output if verbose enabled
     */
    public void printVerboseMessage(String message) {
        if (verbose) {
            System.err.println(message);
        }
    }

    /**
     * Output message and exception stack trace if verbose enabled.
     *
     * @param message message to output if verbose enabled
     * @param e exception to print stack trace
     */
    @SuppressFBWarnings("INFORMATION_EXPOSURE_THROUGH_AN_ERROR_MESSAGE")
    public void printVerboseMessage(String message, Exception e) {
        if (verbose) {
            System.err.println(message);
            e.printStackTrace(System.err);
        }
    }

    /**
     * Output exception stack trace if verbose enabled.
     *
     * @param e exception to print stack trace
     */
    @SuppressFBWarnings("INFORMATION_EXPOSURE_THROUGH_AN_ERROR_MESSAGE")
    public void printVerboseStacktrace(Exception e) {
        if (verbose) {
            e.printStackTrace(System.err);
        }
    }

}
