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

public class PexelsProvider implements WallpaperProvider {
    private static final String API_KEY = "GmgK9CKW7xIJelmpDPSBaNfYunq0cjFRGfuYBq70ba2uVYFtAe7gv33x"; 
    private final HttpClient client = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    @Override
    public String getProviderName() { return "Pexels"; }

    @Override
    public CompletableFuture<List<WallpaperImage>> searchImages(String query) {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        return CompletableFuture.supplyAsync(() -> {
            List<WallpaperImage> results = new ArrayList<>();
            try {
                String url = "https://api.pexels.com/v1/search?query=" + encodedQuery + "&per_page=15";
                
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", API_KEY) 
                        .header("User-Agent", "ScapeApp/1.0") // PERBAIKAN 1: Tambah User-Agent agar tidak diblokir
                        .GET()
                        .build();
                        
                var resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                
                // Debugging: Cek jika error dari server (misal 401 Unauthorized)
                if (resp.statusCode() != 200) {
                    System.err.println("Pexels Error: " + resp.statusCode() + " - " + resp.body());
                    return results;
                }

                JsonObject json = gson.fromJson(resp.body(), JsonObject.class);

                if (json.has("photos")) {
                    json.getAsJsonArray("photos").forEach(elem -> {
                        JsonObject item = elem.getAsJsonObject();
                        
                        // PERBAIKAN 2: Gunakan getAsString() langsung agar aman meskipun ID-nya angka
                        String id = item.get("id").getAsString(); 
                        
                        // PERBAIKAN 3: Null Safety (Ini penyebab utama error sebelumnya)
                        String desc = item.has("alt") && !item.get("alt").isJsonNull() 
                                    ? item.get("alt").getAsString() 
                                    : "Untitled";
                        
                        JsonObject src = item.getAsJsonObject("src");
                        String full = src.get("original").getAsString();
                        
                        // PERBAIKAN 4: Ganti 'landscape' (gede) ke 'medium' (ringan)
                        String thumb = src.get("medium").getAsString(); 

                        results.add(new WallpaperImage(id, full, thumb, desc, "Pexels"));
                    });
                }
            } catch (Exception e) { 
                e.printStackTrace(); 
            }
            return results;
        });
    }
}