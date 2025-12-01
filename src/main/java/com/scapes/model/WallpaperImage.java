package com.scapes.model;

public class WallpaperImage {
    private final String id;
    private final String imageUrl;
    private final String thumbnailUrl;
    private final String description;
    private final String sourceName;
    private final double width;
    private final double height;

    public WallpaperImage(String id, String imageUrl, String thumbnailUrl, String description, String sourceName, double width, double height) {
        this.id = id;
        this.imageUrl = imageUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.description = description != null ? description : "No Description";
        this.sourceName = sourceName;
        this.width = width;
        this.height = height;
    }

    public String getId() { return id; }
    public String getImageUrl() { return imageUrl; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public String getDescription() { return description; }
    public String getSourceName() { return sourceName; }
    public double getWidth() { return width; }
    public double getHeight() { return height; }
}