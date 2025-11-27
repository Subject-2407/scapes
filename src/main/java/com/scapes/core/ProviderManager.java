package com.scapes.core;

import com.scapes.model.WallpaperImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ProviderManager {
    private final Map<String, WallpaperProvider> providers = new HashMap<>();
    private WallpaperProvider currentProvider;

    public void registerProvider(WallpaperProvider provider) {
        providers.put(provider.getProviderName(), provider);
        if (currentProvider == null) {
            currentProvider = provider;
        }
    }

    public void setActiveProvider(String name) {
        if (providers.containsKey(name)) {
            this.currentProvider = providers.get(name);
            System.out.println("Switched source to: " + name);
        }
    }

    public CompletableFuture<List<WallpaperImage>> search(String query) {
        if (currentProvider == null) {
            return CompletableFuture.failedFuture(new RuntimeException("No provider registered!"));
        }
        return currentProvider.searchImages(query);
    }
    
    public List<String> getAvailableProviders() {
        return List.copyOf(providers.keySet());
    }
}