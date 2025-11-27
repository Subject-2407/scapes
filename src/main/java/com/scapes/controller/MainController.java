package com.scapes.controller;

import com.scapes.core.ProviderManager;
import com.scapes.core.SystemHandler;
import com.scapes.model.WallpaperImage;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;

// controller for main UI

public class MainController {

    // --- FXML Components ---
    @FXML private TextField searchField;
    @FXML private ComboBox<String> providerCombo;
    @FXML private TilePane imageGrid;

    // --- Dependencies ---
    private ProviderManager providerManager;
    private SystemHandler systemHandler;

    private static final HttpClient client = HttpClient.newHttpClient();

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

        System.out.println("Searching '" + query + "' using " + providerCombo.getValue());
        
        imageGrid.getChildren().clear();
        imageGrid.getChildren().add(new Label("Loading..."));

        // call provider to search
        providerManager.search(query)
            .thenAccept(this::displayImages) // update UI with results
            .exceptionally(ex -> { // handle error
                ex.printStackTrace();
                return null;
            });
    }

    private void displayImages(List<WallpaperImage> images) {
        Platform.runLater(() -> {
            imageGrid.getChildren().clear();
            
            if (images.isEmpty()) {
                imageGrid.getChildren().add(new Label("Tidak ada hasil ditemukan."));
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
        imageView.setFitWidth(220);
        imageView.setFitHeight(150);
        loadImageAsync(data.getThumbnailUrl(), imageView);
        
        Label descLabel = new Label(data.getDescription());
        descLabel.setMaxWidth(200);
        descLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #555;");

        card.getChildren().addAll(imageView, descLabel);

        // handle click to set wallpaper
        card.setOnMouseClicked(e -> {
            System.out.println("User choose: " + data.getImageUrl());
            // TODO: call system handler to download & set wallpaper
        });

        return card;
    }

    // is actually a workaround specific for Pexels thumbnails loading issue
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
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}