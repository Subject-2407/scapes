package com.scapes.impl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SuggestionService {
    private static final Logger logger = LoggerFactory.getLogger(SuggestionService.class);
    // endpoints
    private static final String LOG_API_URL = "https://lte-lyzer.atmadja.id/app/api/scapes-logger/log_search.php";
    private static final String ML_API_URL  = "http://103.147.46.234:8000/recommend";

    private final HttpClient client;
    private final Gson gson;
    private String deviceId;

    public SuggestionService() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.gson = new Gson();

        this.deviceId = System.getProperty("user.name", "unknown_user");
        fetchPublicIp();
    }

    private void fetchPublicIp() {
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.ipify.org")) 
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    this.deviceId = response.body();
                    logger.info("Device ID set to Public IP: " + this.deviceId);
                }
            } catch (Exception e) {
                logger.error("Failed to fetch Public IP, using fallback instead for Device ID: " + this.deviceId, e);
            }
        });
    }

    public void logSearch(String query) {
        CompletableFuture.runAsync(() -> {
            try {
                JsonObject json = new JsonObject();
                json.addProperty("device_id", deviceId);
                json.addProperty("query", query);
                String requestBody = gson.toJson(json);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(LOG_API_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                client.send(request, HttpResponse.BodyHandlers.ofString());
                logger.info("Query log sent: " + query);

            } catch (Exception e) {
                logger.error("Failed to log user query: " + e.getMessage(), e);
            }
        });
    }

    public CompletableFuture<List<String>> getRecommendations(String keyword) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> suggestions = new ArrayList<>();
            try {
                String url = ML_API_URL + "?keyword=" + keyword.replace(" ", "%20");
                
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject json = gson.fromJson(response.body(), JsonObject.class);

                    if (json.has("recommendations")) {
                        JsonArray array = json.getAsJsonArray("recommendations");
                        array.forEach(item -> suggestions.add(item.getAsString()));
                    }
                }
            } catch (Exception e) {
                logger.error("Recommender Service offline: " + e.getMessage(), e);
            }
            return suggestions;
        });
    }
}