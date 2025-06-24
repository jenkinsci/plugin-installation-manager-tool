package io.jenkins.tools.pluginmanager.config;

import java.util.regex.Pattern;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErrNormalized;
import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOutNormalized;
import static org.assertj.core.api.Assertions.assertThat;

class LogOutputTest {

    private LogOutput verboseEnabled;
    private LogOutput verboseDisabled;

    @BeforeEach
    void setUp() {
        verboseEnabled = new LogOutput(true);
        verboseDisabled = new LogOutput(false);
    }

    @Test
    void printMessage() throws Exception {
        verifyStdErrOnly("foo\n", () -> verboseEnabled.printMessage("foo"));
        verifyStdErrOnly("foo\n", () -> verboseDisabled.printMessage("foo"));
    }

    @Test
    void printVerboseMessage() throws Exception {
        verifyStdErrOnly("foo\n", () -> verboseEnabled.printVerboseMessage("foo"));
        verifyStdErrOnly("", () -> verboseDisabled.printVerboseMessage("foo"));
    }

    @Test
    void printVerboseMessageWithStackTrace() throws Exception {
        Exception e = new Exception("blah");
        verifyStdErrOnly(Pattern.compile("foo$.java\\.lang\\.Exception: blah$.\\tat io\\.jenkins\\.tools\\.pluginmanager\\.config\\.LogOutputTest\\.printVerboseMessageWithStackTrace.*", Pattern.MULTILINE | Pattern.DOTALL), () -> verboseEnabled.printVerboseMessage("foo", e));
        verifyStdErrOnly("", () -> verboseDisabled.printVerboseMessage("foo", e));
    }

    @Test
    void printVerboseStacktrace() throws Exception {
        Exception e = new Exception("blah");
        verifyStdErrOnly(Pattern.compile("java\\.lang\\.Exception: blah$.\\tat io\\.jenkins\\.tools\\.pluginmanager\\.config\\.LogOutputTest\\.printVerboseStacktrace.*", Pattern.MULTILINE | Pattern.DOTALL), () -> verboseEnabled.printVerboseStacktrace(e));
        verifyStdErrOnly("", () -> verboseDisabled.printVerboseStacktrace(e));
    }

    private static void verifyStdErrOnly(String expected, Runnable statement) throws Exception {
        Pair<String, String> output = tapOutput(statement);
        assertThat(output.getLeft()).isEmpty();
        assertThat(output.getRight()).isEqualTo(expected);
    }

    private static void verifyStdErrOnly(Pattern expected, Runnable statement) throws Exception {
        Pair<String, String> output = tapOutput(statement);
        assertThat(output.getLeft()).isEmpty();
        assertThat(output.getRight()).matches(expected);
    }

    private static Pair<String, String> tapOutput(Runnable statement) throws Exception {
        final MutablePair<String, String> output = new MutablePair<>();
        output.setLeft(tapSystemOutNormalized(() ->
            output.setRight(tapSystemErrNormalized(statement::run))));
        return output;
    }
}
