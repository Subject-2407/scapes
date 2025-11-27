package com.scapes.core;

import java.io.File;

public interface SystemHandler {
    File downloadImage(String url, String filename);
    
    boolean setWallpaper(File imageFile);
}