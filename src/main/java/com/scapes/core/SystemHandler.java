package com.scapes.core;

import java.io.File;
import java.util.List;

import com.scapes.model.WallpaperImage;

public interface SystemHandler {
    File downloadImage(String url, String filename);
    
    boolean setWallpaper(File imageFile);

    List<WallpaperImage> getDownloadedImages();
}