package com.scapes.core;

import com.scapes.model.WallpaperImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProviderManager {
    private static final Logger logger = LoggerFactory.getLogger(ProviderManager.class);
    private final Map<String, WallpaperProvider> providers = new HashMap<>();
    private WallpaperProvider currentProvider;

    public void registerProvider(WallpaperProvider provider) {
        logger.info("Registering provider: " + provider.getProviderName());
        providers.put(provider.getProviderName(), provider);
        if (currentProvider == null) {
            currentProvider = provider;
        }
    }

    public void setActiveProvider(String name) {
        if (providers.containsKey(name)) {
            this.currentProvider = providers.get(name);
            logger.info("Active provider set to: " + name);
        }
    }

    public CompletableFuture<List<WallpaperImage>> search(String query, double minWidth, double minHeight) {
        if (currentProvider == null) {
            logger.error("No active provider set!");
            return CompletableFuture.failedFuture(new RuntimeException("No provider registered!"));
        }
        return currentProvider.searchImages(query, minWidth, minHeight);
    }
    
    public List<String> getAvailableProviders() {
        return List.copyOf(providers.keySet());
    }
}