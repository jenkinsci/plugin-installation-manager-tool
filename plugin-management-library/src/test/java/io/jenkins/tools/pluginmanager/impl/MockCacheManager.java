package io.jenkins.tools.pluginmanager.impl;

import org.json.JSONObject;

public class MockCacheManager extends CacheManager {
    public MockCacheManager() {
        super(null, false);
    }

    @Override
    void createCache() {
    }

    @Override
    void addToCache(String cacheKey, String value) {
    }

    @Override
    JSONObject retrieveFromCache(String cacheKey) {
        return null;
    }
}
