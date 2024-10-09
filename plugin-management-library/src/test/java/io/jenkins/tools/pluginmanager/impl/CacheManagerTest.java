package io.jenkins.tools.pluginmanager.impl;

import io.jenkins.tools.pluginmanager.config.LogOutput;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErrNormalized;
import static java.nio.file.Files.setPosixFilePermissions;
import static java.nio.file.Files.write;
import static java.time.Clock.systemDefaultZone;
import static java.time.Clock.systemUTC;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonMap;
import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeFalse;

public class CacheManagerTest {

    private static final boolean VERBOSE = true;

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void cacheReturnsJsonThatWasPutIntoCacheForSpecifiedKey() {
        CacheManager cacheManager = cacheManager();
        cacheManager.addToCache(
                "the-cache-key",
                new JSONObject().put("value", 123));
        cacheManager.addToCache(
                "another-cache-key",
                new JSONObject().put("value", 456));

        JSONObject jsonObject = cacheManager.retrieveFromCache("the-cache-key");

        assertThat(jsonObject.toMap())
                .isEqualTo(singletonMap("value", 123));
    }

    @Test
    public void cacheReturnsLatestJsonThatWasPutIntoCacheForSpecifiedKey() {
        CacheManager cacheManager = cacheManager();
        cacheManager.addToCache(
                "the-cache-key",
                new JSONObject().put("value", 123));
        cacheManager.addToCache(
                "the-cache-key",
                new JSONObject().put("value", 456));

        JSONObject jsonObject = cacheManager.retrieveFromCache("the-cache-key");

        assertThat(jsonObject.toMap())
                .isEqualTo(singletonMap("value", 456));
    }

    @Test
    public void cacheReturnsJsonStoredByAnotherCacheManagerInstance() throws Exception {
        Path cacheFolder = folder.newFolder("reused_cache").toPath();
        CacheManager writeInstance = new CacheManager(cacheFolder, new LogOutput(!VERBOSE));
        writeInstance.createCache();
        CacheManager readInstance = new CacheManager(cacheFolder, new LogOutput(!VERBOSE));

        writeInstance.addToCache(
                "the-cache-key",
                new JSONObject().put("value", 123));
        JSONObject jsonObject = readInstance.retrieveFromCache("the-cache-key");

        assertThat(jsonObject.toMap())
                .isEqualTo(singletonMap("value", 123));
    }

    @Test
    public void cacheReturnsNullWhenNoJsonWasPutIntoCache() {
        JSONObject jsonObject = cacheManager().retrieveFromCache("key-without-json");

        assertThat(jsonObject).isNull();
    }

    @Test
    public void cacheReturnsNullWhenJsonWasPutIntoCacheMoreThanAnHourAgo() {
        CacheManager managerWithExpiredEntries = cacheManagerWithExpiredEntries();

        managerWithExpiredEntries.addToCache("the-cache-key", new JSONObject());

        JSONObject jsonObject = managerWithExpiredEntries.retrieveFromCache("the-cache-key");

        assertThat(jsonObject).isNull();
    }

    @Test
    public void messageThatCacheFolderIsCreatedIsWrittenToSystemErrWhenItDidNotExist() throws Exception {
        String out = tapSystemErrNormalized(this::cacheManager);

        assertThat(out)
                .startsWith("Created cache at: ")
                .endsWith("cache\n");
    }

    @Test
    public void infoAboutAnExpiredCacheEntryIsWrittenToSystemErr() throws Exception {
        CacheManager managerWithExpiredEntries = cacheManagerWithExpiredEntries();

        managerWithExpiredEntries.addToCache("the-cache-key", new JSONObject());

        String out = tapSystemErrNormalized(
                () -> managerWithExpiredEntries.retrieveFromCache("the-cache-key")
        );

         assertThat(out).isEqualTo("Cache entry expired: the-cache-key. Will skip it\n");
    }

    @Test
    public void cacheReturnsNullWhenCachedFileIsNotJson() throws Exception {
        CacheManager manager = cacheManagerWithNonJsonFileForKey("the-cache-key");

        JSONObject jsonObject = manager.retrieveFromCache("the-cache-key");

        assertThat(jsonObject).isNull();
    }

    @Test
    public void messageAboutInvalidCacheFileIsWrittenToSystemErr() throws Exception {
        CacheManager manager = cacheManagerWithNonJsonFileForKey("the-cache-key");

        String out = tapSystemErrNormalized(
                () -> manager.retrieveFromCache("the-cache-key"));

        assertThat(out).startsWith(
                "Cache ignored invalid file the-cache-key.json.\n"
                        + "org.json.JSONException: A JSONObject text must begin with '{' at 3 [character 4 line 1]");
    }

    @Test
    public void cacheReturnsNullWhenCachedFileCannotBeRead() throws Exception {
        skipOnWindows(); // we cannot modify the read permission on Windows
        CacheManager manager = cacheManagerWithNonReadableJsonFileForKey("the-cache-key");

        JSONObject jsonObject = manager.retrieveFromCache("the-cache-key");

        assertThat(jsonObject).isNull();
    }

    @Test
    public void cacheManagerWithNonReadableJsonFileForKey() throws Exception {
        skipOnWindows(); // we cannot modify the read permission on Windows
        CacheManager manager = cacheManagerWithNonReadableJsonFileForKey("the-cache-key");

        String out = tapSystemErrNormalized(
                () -> manager.retrieveFromCache("the-cache-key"));

        assertThat(out).startsWith(
                "Cache ignored file the-cache-key.json because it cannot be read.\n"
                        + "java.nio.file.AccessDeniedException:");
    }

    private void skipOnWindows() {
        assumeFalse(IS_OS_WINDOWS);
    }

    private CacheManager cacheManager() {
        return cacheManager(systemDefaultZone());
    }

    private CacheManager cacheManagerWithExpiredEntries() {
        Clock oneHourAndOneMinuteInTheFuture = Clock.fixed(
                systemUTC().instant().plus(61, MINUTES),
                ZoneId.systemDefault());
        return cacheManager(
                oneHourAndOneMinuteInTheFuture);
    }

    private CacheManager cacheManager(Clock clock) {
        Path cacheFolder = cacheFolder();
        CacheManager manager = new CacheManager(cacheFolder, new LogOutput(VERBOSE), clock, true);
        manager.createCache();
        return manager;
    }

    private CacheManager cacheManagerWithNonJsonFileForKey(String key) throws IOException {
        Path cacheFolder = cacheFolder();
        CacheManager manager = new CacheManager(cacheFolder, new LogOutput(VERBOSE));
        manager.createCache();
        write(cacheFolder.resolve(key + ".json"), new byte[] { 1, 2, 3});
        return manager;
    }

    private CacheManager cacheManagerWithNonReadableJsonFileForKey(String key) throws IOException {
        Path cacheFolder = cacheFolder();
        CacheManager manager = new CacheManager(cacheFolder, new LogOutput(VERBOSE));
        manager.createCache();
        manager.addToCache(key, new JSONObject());
        setPosixFilePermissions(cacheFolder.resolve(key + ".json"), emptySet());
        return manager;
    }

    private Path cacheFolder() {
        return folder.getRoot().toPath().resolve("cache");
    }
}
