package com.scapes.model;

public class WallpaperImage {
    private final String id;
    private final String imageUrl;
    private final String thumbnailUrl;
    private final String description;
    private final String sourceName;

    public WallpaperImage(String id, String imageUrl, String thumbnailUrl, String description, String sourceName) {
        this.id = id;
        this.imageUrl = imageUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.description = description != null ? description : "No Description";
        this.sourceName = sourceName;
    }

    public String getId() { return id; }
    public String getImageUrl() { return imageUrl; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public String getDescription() { return description; }
    public String getSourceName() { return sourceName; }
}