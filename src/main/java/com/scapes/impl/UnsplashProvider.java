package com.scapes.impl;

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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.scapes.core.WallpaperProvider;
import com.scapes.model.WallpaperImage;

public class UnsplashProvider implements WallpaperProvider {
    private static final Logger logger = LoggerFactory.getLogger(UnsplashProvider.class);
    private static final String API_KEY = "8pMXKEp0lhIA8HZhywGmJe2ogcma9J4VTFmq7vTrc2k";
    private final HttpClient client = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    @Override
    public String getProviderName() { return "Unsplash"; }

    @Override
    public CompletableFuture<List<WallpaperImage>> searchImages(String query, int page, double minWidth, double minHeight) {
        logger.info("Searching Unsplash for query: " + query);
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);

        return CompletableFuture.supplyAsync(() -> {
            List<WallpaperImage> results = new ArrayList<>();
            try {
                logger.info("Sending request to Unsplash API...");
                String url = "https://api.unsplash.com/search/photos?query=" + encodedQuery + "&client_id=" + API_KEY + "&per_page=30&orientation=landscape" + "&page=" + page;
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
                var resp = client.send(req, HttpResponse.BodyHandlers.ofString());

                if (resp.statusCode() != 200) {
                    logger.error("Unsplash API request failed with status: " + resp.statusCode());
                    return results;
                }
                
                JsonObject json = gson.fromJson(resp.body(), JsonObject.class);
                logger.info("Parsing Unsplash API response...");
                json.getAsJsonArray("results").forEach(elem -> {
                    JsonObject item = elem.getAsJsonObject();

                    long imgW = item.get("width").getAsLong();
                    long imgH = item.get("height").getAsLong();

                    // skip images smaller than minimum dimensions
                    if (imgW < minWidth || imgH < minHeight) {
                        return; 
                    }

                    String id = item.get("id").getAsString();
                    String desc = item.has("description") && !item.get("description").isJsonNull() 
                                ? item.get("description").getAsString() : "Untitled";
                    
                    JsonObject urls = item.getAsJsonObject("urls");
                    String full = urls.get("regular").getAsString();
                    String thumb = urls.get("small").getAsString();

                    results.add(new WallpaperImage(id, full, thumb, desc, "Unsplash", imgW, imgH));
                });
            } catch (Exception e) {
                logger.error("Failed to search Unsplash for query: " + query, e); 
            }
            return results;
        });
    }
}
