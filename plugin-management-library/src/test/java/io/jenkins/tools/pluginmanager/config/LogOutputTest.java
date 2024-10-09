package io.jenkins.tools.pluginmanager.config;

import java.util.regex.Pattern;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErrNormalized;
import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOutNormalized;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

public class LogOutputTest {

    private LogOutput verboseEnabled;
    private LogOutput verboseDisabled;

    @Before
    public void setUp() {
        verboseEnabled = new LogOutput(true);
        verboseDisabled = new LogOutput(false);
    }

    @Test
    public void printMessage() {
        verifyStdErrOnly("foo\n", () -> verboseEnabled.printMessage("foo"));
        verifyStdErrOnly("foo\n", () -> verboseDisabled.printMessage("foo"));
    }

    @Test
    public void printVerboseMessage() {
        verifyStdErrOnly("foo\n", () -> verboseEnabled.printVerboseMessage("foo"));
        verifyStdErrOnly("", () -> verboseDisabled.printVerboseMessage("foo"));
    }

    @Test
    public void printVerboseMessageWithStackTrace() {
        Exception e = new Exception("blah");
        verifyStdErrOnly(Pattern.compile("foo$.java\\.lang\\.Exception: blah$.\\tat io\\.jenkins\\.tools\\.pluginmanager\\.config\\.LogOutputTest\\.printVerboseMessageWithStackTrace.*", Pattern.MULTILINE | Pattern.DOTALL), () -> verboseEnabled.printVerboseMessage("foo", e));
        verifyStdErrOnly("", () -> verboseDisabled.printVerboseMessage("foo", e));
    }

    @Test
    public void printVerboseStacktrace() {
        Exception e = new Exception("blah");
        verifyStdErrOnly(Pattern.compile("java\\.lang\\.Exception: blah$.\\tat io\\.jenkins\\.tools\\.pluginmanager\\.config\\.LogOutputTest\\.printVerboseStacktrace.*", Pattern.MULTILINE | Pattern.DOTALL), () -> verboseEnabled.printVerboseStacktrace(e));
        verifyStdErrOnly("", () -> verboseDisabled.printVerboseStacktrace(e));
    }

    static void verifyStdErrOnly(String expected, Runnable statement) {
        Pair<String, String> output = tapOutput(statement);
        assertThat(output.getLeft()).isEmpty();
        assertThat(output.getRight()).isEqualTo(expected);
    }

    static void verifyStdErrOnly(Pattern expected, Runnable statement) {
        Pair<String, String> output = tapOutput(statement);
        assertThat(output.getLeft()).isEmpty();
        assertThat(output.getRight()).matches(expected);
    }

    private static Pair<String, String> tapOutput(Runnable statement) {
        try {
            final MutablePair<String, String> output = new MutablePair<>();
            output.setLeft(tapSystemOutNormalized(() -> {
                output.setRight(tapSystemErrNormalized(statement::run));
            }));
            return output;
        } catch (Exception e) {
            fail(e.getMessage());
            return Pair.of("", "");
        }
    }
}
