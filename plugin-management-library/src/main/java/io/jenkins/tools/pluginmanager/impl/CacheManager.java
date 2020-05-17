package io.jenkins.tools.pluginmanager.impl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import org.json.JSONObject;

public class CacheManager {

    private Path cache;
    private boolean verbose;

    public CacheManager(Path cache, boolean verbose) {
        this.cache = cache;
        this.verbose = verbose;
    }

    void createCache() {
        if (!Files.exists(cache)) {
            try {
                Files.createDirectory(cache);
                if (verbose) {
                    System.out.println("Created cache at: " + cache);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }


    void addToCache(String cacheKey, String value) {
        try {
            Path fileToCache = cache.resolve(cacheKey + ".json");
            Files.write(fileToCache, value.getBytes(StandardCharsets.UTF_8));
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
        Path cachedPath = cache.resolve(cacheKey + ".json");
        if (!Files.exists(cachedPath)) {
            return null;
        }

        try {
            FileTime lastModifiedTime = Files.getLastModifiedTime(cachedPath);
            Duration between = Duration.between(lastModifiedTime.toInstant(), Instant.now());
            long betweenHours = between.toHours();

            if (betweenHours > 0L) {
                if (verbose) {
                    System.out.println("Cache entry expired");
                }
                return null;
            }

            return new JSONObject(new String(Files.readAllBytes(cachedPath), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
