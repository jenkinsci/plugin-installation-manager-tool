package io.jenkins.tools.pluginmanager.impl;

import io.jenkins.tools.pluginmanager.config.LogOutput;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import org.json.JSONObject;
import org.json.JSONTokener;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.newBufferedWriter;
import static java.nio.file.Files.newInputStream;

public class CacheManager {

    private final Path cache;
    private final LogOutput logOutput;
    private final Clock clock;
    private final boolean expires;

    public CacheManager(Path cache, LogOutput logOutput) {
        this(cache, logOutput, Clock.systemDefaultZone(), true);
    }

    CacheManager(Path cache, LogOutput logOutput, Clock clock, boolean expires) {
        this.cache = cache;
        this.logOutput = logOutput;
        this.clock = clock;
        this.expires = expires;
    }

    void createCache() {
        if (!Files.exists(cache)) {
            try {
                Path parent = cache.getParent();
                if (parent != null && !Files.exists(parent)) {
                    Files.createDirectory(parent);
                }
                Files.createDirectory(cache);
                logOutput.printVerboseMessage("Created cache at: " + cache);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }


    void addToCache(String cacheKey, JSONObject value) {
        Path fileToCache = cache.resolve(cacheKey + ".json");
        try (Writer writer = newBufferedWriter(fileToCache, UTF_8)) {
            value.write(writer);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Retrieves a json object from the cache.
     * <p>
     * Will return null if the key can't be found or if it hasn't been
     * modified for 1 hour
     *
     * @param cacheKey key to lookup, i.e. update-center
     * @return the cached json object or null
     */
    JSONObject retrieveFromCache(String cacheKey) {
        String filename = cacheKey + ".json";
        Path cachedPath = cache.resolve(filename);
        try {
            FileTime lastModifiedTime = Files.getLastModifiedTime(cachedPath);
            Duration between = Duration.between(lastModifiedTime.toInstant(), clock.instant());
            long betweenHours = between.toHours();

            if (betweenHours > 0L) {
                logOutput.printVerboseMessage("Cache entry expired: " + cacheKey +
                        (expires ? ". Will skip it" : ". Will accept it, because expiration is disabled"));
                if (expires) {
                    return null;
                }
            }

            JSONTokener tokener = new JSONTokener(newInputStream(cachedPath));
            return new JSONObject(tokener);
        } catch (NoSuchFileException e) {
            return null;
        } catch (RuntimeException e) {
            logOutput.printVerboseMessage("Cache ignored invalid file " + filename + ".", e);
            return null;
        } catch (IOException e) {
            logOutput.printVerboseMessage("Cache ignored file " + filename + " because it cannot be read.", e);
            return null;
        }
    }
}
