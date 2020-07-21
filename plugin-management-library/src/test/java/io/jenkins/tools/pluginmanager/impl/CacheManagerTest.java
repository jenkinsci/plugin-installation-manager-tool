package io.jenkins.tools.pluginmanager.impl;

import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOutNormalized;
import static java.time.Clock.systemDefaultZone;
import static java.time.Clock.systemUTC;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

public class CacheManagerTest {

    private static final boolean VERBOSE = true;

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void cacheReturnsJsonThatWasPutIntoCacheForSpecifiedKey() {
        CacheManager cacheManager = cacheManager();
        cacheManager.addToCache("the-cache-key", "{\"value\":123}");
        cacheManager.addToCache("another-cache-key", "{\"value\":456}");

        JSONObject jsonObject = cacheManager.retrieveFromCache("the-cache-key");

        assertThat(jsonObject.toMap())
                .isEqualTo(singletonMap("value", 123));
    }

    @Test
    public void cacheReturnsLatestJsonThatWasPutIntoCacheForSpecifiedKey() {
        CacheManager cacheManager = cacheManager();
        cacheManager.addToCache("the-cache-key", "{\"value\":123}");
        cacheManager.addToCache("the-cache-key", "{\"value\":456}");

        JSONObject jsonObject = cacheManager.retrieveFromCache("the-cache-key");

        assertThat(jsonObject.toMap())
                .isEqualTo(singletonMap("value", 456));
    }

    @Test
    public void cacheReturnsJsonStoredByAnotherCacheManagerInstance() throws Exception {
        Path cacheFolder = folder.newFolder("reused_cache").toPath();
        CacheManager writeInstance = new CacheManager(cacheFolder, !VERBOSE);
        writeInstance.createCache();
        CacheManager readInstance = new CacheManager(cacheFolder, !VERBOSE);

        writeInstance.addToCache("the-cache-key", "{\"value\":123}");
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

        managerWithExpiredEntries.addToCache("the-cache-key", "{\"value\":123}");

        JSONObject jsonObject = managerWithExpiredEntries.retrieveFromCache("the-cache-key");

        assertThat(jsonObject).isNull();
    }

    @Test
    public void messageThatCacheFolderIsCreatedIsWrittenToSystemOutWhenItDidNotExist() throws Exception {
        String out = tapSystemOutNormalized(this::cacheManager);

        assertThat(out)
                .startsWith("Created cache at: ")
                .endsWith("cache\n");
    }

    @Test
    public void infoAboutAnExpiredCacheEntryIsWrittenToSystemOut() throws Exception {
        CacheManager managerWithExpiredEntries = cacheManagerWithExpiredEntries();

        managerWithExpiredEntries.addToCache("the-cache-key", "{\"value\":123}");

        String out = tapSystemOutNormalized(
                () -> managerWithExpiredEntries.retrieveFromCache("the-cache-key")
        );

         assertThat(out).isEqualTo("Cache entry expired\n");
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
        Path cacheFolder = folder.getRoot().toPath().resolve("cache");
        CacheManager manager = new CacheManager(cacheFolder, VERBOSE, clock);
        manager.createCache();
        return manager;
    }
}
