package com.scapes.controller;

import com.scapes.core.ProviderManager;
import com.scapes.core.SystemHandler;
import com.scapes.model.WallpaperImage;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// controller for main UI

public class MainController {
    // --- Logger ---
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    // --- FXML Components ---
    @FXML private TextField searchField;
    @FXML private ComboBox<String> providerCombo;
    @FXML private TilePane imageGrid;

    // --- Dependencies ---
    private ProviderManager providerManager;
    private SystemHandler systemHandler;

    // --- Desktop resolution ---
    private Rectangle2D screenBounds = Screen.getPrimary().getBounds();
    private double screenWidth = screenBounds.getWidth() * 0.8; // use 80% of screen width
    private double screenHeight = screenBounds.getHeight() * 0.8; // use 80% of screen height

    // --- HTTP Client ---
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();

    public void init(ProviderManager manager, SystemHandler system) {
        this.providerManager = manager;
        this.systemHandler = system;

        providerCombo.getItems().addAll(manager.getAvailableProviders());
        
        if (!providerCombo.getItems().isEmpty()) {
            providerCombo.getSelectionModel().select(0);
            manager.setActiveProvider(providerCombo.getItems().get(0));
        }

        // handle provider selection change
        providerCombo.setOnAction(e -> {
            String selected = providerCombo.getValue();
            manager.setActiveProvider(selected);
        });
    }

    @FXML
    public void onSearchAction() {
        String query = searchField.getText();
        if (query.isEmpty() || providerManager == null) return;
        
        imageGrid.getChildren().clear();
        Label loadingLabel = new Label("Loading...");
        loadingLabel.setStyle("-fx-text-fill: white; -fx-font-size: 14px;");
        imageGrid.getChildren().add(loadingLabel);

        // call provider to search
        providerManager.search(query, screenWidth, screenHeight)
            .thenAccept(this::displayImages) // update UI with results
            .exceptionally(ex -> {
                return null;
            });
    }

    private void displayImages(List<WallpaperImage> images) {
        Platform.runLater(() -> {
            imageGrid.getChildren().clear();
            
            if (images.isEmpty()) {
                imageGrid.getChildren().add(new Label("No images found."));
                return;
            }

            for (WallpaperImage img : images) {
                VBox card = createCard(img);
                imageGrid.getChildren().add(card);
            }
        });
    }

    private VBox createCard(WallpaperImage data) {
        VBox card = new VBox(5);
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-background-color: white; -fx-padding: 10; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);");
        
        ImageView imageView = new ImageView();
        imageView.setFitHeight(150);
        imageView.setFitWidth(200);
        imageView.setPreserveRatio(true);
        loadImageAsync(data.getThumbnailUrl(), imageView);
        
        Label descLabel = new Label(data.getDescription());
        descLabel.setMaxWidth(200);
        descLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #555;");

        card.getChildren().addAll(imageView, descLabel);

        // handle click to set wallpaper
        card.setOnMouseClicked(e -> {
            File downloadedImage = systemHandler.downloadImage(data.getImageUrl(), data.getId() + ".jpg");

            systemHandler.setWallpaper(downloadedImage);
        });

        return card;
    }

    private void loadImageAsync(String url, ImageView target) {
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .GET()
                        .build();

                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() == 200) {
                    Image image = new Image(response.body());   
                    Platform.runLater(() -> target.setImage(image));
                } else {
                    logger.error("Failed to load thumbnail: " + url + " (Status: " + response.statusCode() + ")");
                }
            } catch (Exception e) {
                logger.error("Error loading thumbnail: " + url, e);
            }
        });
    }
}