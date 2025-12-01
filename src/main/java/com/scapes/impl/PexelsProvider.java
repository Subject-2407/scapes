package com.scapes.impl; // Sesuaikan package kamu

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.scapes.core.WallpaperProvider;
import com.scapes.model.WallpaperImage;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PexelsProvider implements WallpaperProvider {
    private static final Logger logger = LoggerFactory.getLogger(PexelsProvider.class);
    private static final String API_KEY = "GmgK9CKW7xIJelmpDPSBaNfYunq0cjFRGfuYBq70ba2uVYFtAe7gv33x"; 
    private final HttpClient client = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    @Override
    public String getProviderName() { return "Pexels"; }

    @Override
    public CompletableFuture<List<WallpaperImage>> searchImages(String query, double minWidth, double minHeight) {
        logger.info("Searching Pexels for query: " + query);
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        
        return CompletableFuture.supplyAsync(() -> {
            List<WallpaperImage> results = new ArrayList<>();
            try {
                logger.info("Sending request to Pexels API...");
                String url = "https://api.pexels.com/v1/search?query=" + encodedQuery + "&per_page=30&orientation=landscape";
                
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", API_KEY) 
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .GET()
                        .build();
                        
                var resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                
                if (resp.statusCode() != 200) {
                    logger.error("Pexels API request failed with status: " + resp.statusCode());
                    return results;
                }

                JsonObject json = gson.fromJson(resp.body(), JsonObject.class);
                logger.info("Parsing Pexels API response...");
                if (json.has("photos")) {
                    json.getAsJsonArray("photos").forEach(elem -> {
                        JsonObject item = elem.getAsJsonObject();

                        long imgW = item.get("width").getAsLong();
                        long imgH = item.get("height").getAsLong();

                        // skip images smaller than minimum dimensions
                        if (imgW < minWidth || imgH < minHeight) {
                            return;
                        }

                        String id = item.get("id").getAsString(); 
                        String desc = item.has("alt") && !item.get("alt").isJsonNull() 
                                    ? item.get("alt").getAsString() 
                                    : "Untitled";
                        
                        JsonObject src = item.getAsJsonObject("src");
                        String full = src.get("original").getAsString();
                        String thumb = src.get("medium").getAsString(); 

                        results.add(new WallpaperImage(id, full, thumb, desc, "Pexels"));
                    });
                }
            } catch (Exception e) { 
                logger.error("Failed to search Pexels for query: " + query, e);
            }
            return results;
        });
    }
}