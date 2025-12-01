package com.scapes.core;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.scapes.model.WallpaperImage;

public interface WallpaperProvider {
    String getProviderName();
    CompletableFuture<List<WallpaperImage>> searchImages(String query, int page, double minWidth, double minHeight);
}
