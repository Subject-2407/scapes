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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.scapes.core.WallpaperProvider;
import com.scapes.model.WallpaperImage;

public class UnsplashProvider implements WallpaperProvider {
    private static final String API_KEY = "8pMXKEp0lhIA8HZhywGmJe2ogcma9J4VTFmq7vTrc2k";
    private final HttpClient client = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    @Override
    public String getProviderName() { return "Unsplash"; }

    @Override
    public CompletableFuture<List<WallpaperImage>> searchImages(String query) {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        return CompletableFuture.supplyAsync(() -> {
            List<WallpaperImage> results = new ArrayList<>();
            try {
                String url = "https://api.unsplash.com/search/photos?query=" + encodedQuery + "&client_id=" + API_KEY;
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
                var resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                
                JsonObject json = gson.fromJson(resp.body(), JsonObject.class);
                
                json.getAsJsonArray("results").forEach(elem -> {
                    JsonObject item = elem.getAsJsonObject();
                    String id = item.get("id").getAsString();
                    String desc = item.has("description") && !item.get("description").isJsonNull() 
                                ? item.get("description").getAsString() : "Untitled";
                    
                    JsonObject urls = item.getAsJsonObject("urls");
                    String full = urls.get("regular").getAsString();
                    String thumb = urls.get("small").getAsString();

                    // convert to universal model
                    results.add(new WallpaperImage(id, full, thumb, desc, "Unsplash"));
                });
            } catch (Exception e) { e.printStackTrace(); }
            return results;
        });
    }
}
