package com.scapes.core;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.scapes.model.WallpaperImage;

public interface SystemHandler {
    CompletableFuture<File> downloadImage(String url, String filename);
    
    boolean setWallpaper(File imageFile);

    List<WallpaperImage> getDownloadedImages();
}