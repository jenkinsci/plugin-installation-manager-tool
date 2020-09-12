package io.jenkins.tools.pluginmanager.impl;

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

    private Path cache;
    private boolean verbose;
    private Clock clock;

    public CacheManager(Path cache, boolean verbose) {
        this(cache, verbose, Clock.systemDefaultZone());
    }

    CacheManager(Path cache, boolean verbose, Clock clock) {
        this.cache = cache;
        this.verbose = verbose;
        this.clock = clock;
    }

    void createCache() {
        if (!Files.exists(cache)) {
            try {
                Path parent = cache.getParent();
                if (parent != null && !Files.exists(parent)) {
                    Files.createDirectory(parent);
                }
                Files.createDirectory(cache);
                if (verbose) {
                    System.out.println("Created cache at: " + cache);
                }
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
                if (verbose) {
                    System.out.println("Cache entry expired");
                }
                return null;
            }

            JSONTokener tokener = new JSONTokener(newInputStream(cachedPath));
            return new JSONObject(tokener);
        } catch (NoSuchFileException e) {
            return null;
        } catch (RuntimeException e) {
            if (verbose) {
                System.err.println(
                        "Cache ignored invalid file " + filename + ".");
                e.printStackTrace();
            }
            return null;
        } catch (IOException e) {
            if (verbose) {
                System.err.println(
                        "Cache ignored file " + filename + " because it cannot be read.");
                e.printStackTrace();
            }
            return null;
        }
    }
}
