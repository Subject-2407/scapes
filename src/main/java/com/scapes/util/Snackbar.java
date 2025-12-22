package com.scapes.util;

import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class Snackbar {
    private static final double SNACKBAR_WIDTH = 300;
    private static final double SNACKBAR_HEIGHT = 50;
    private static final double MARGIN_BOTTOM = 20;
    private static final double MARGIN_RIGHT = 20;
    private static final Duration ANIMATION_DURATION = Duration.millis(300);
    private static final Duration DISPLAY_DURATION = Duration.seconds(3);

    public enum Type {
        INFO("#0c6370", "#ffffff"),      // Blue - downloading
        SUCCESS("#28a745", "#ffffff"),   // Green - success
        ERROR("#dc3545", "#ffffff");     // Red - error

        private final String backgroundColor;
        private final String textColor;

        Type(String backgroundColor, String textColor) {
            this.backgroundColor = backgroundColor;
            this.textColor = textColor;
        }

        public String getBackgroundColor() { return backgroundColor; }
        public String getTextColor() { return textColor; }
    }

    public static void show(Scene scene, String message, Type type) {
        Platform.runLater(() -> {
            // Create snackbar container
            HBox snackbar = new HBox();
            snackbar.setPrefSize(SNACKBAR_WIDTH, SNACKBAR_HEIGHT);
            snackbar.setMaxSize(SNACKBAR_WIDTH, SNACKBAR_HEIGHT);
            snackbar.setStyle(String.format(
                "-fx-background-color: %s; -fx-background-radius: 8; -fx-border-radius: 8; " +
                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 6, 0, 0, 2);",
                type.getBackgroundColor()
            ));
            snackbar.setPadding(new Insets(12, 16, 12, 16));
            snackbar.setAlignment(Pos.CENTER_LEFT);

            // Create indicator circle
            Circle indicator = new Circle(6);
            indicator.setFill(Color.web(type.getTextColor()));
            indicator.setOpacity(0.8);
            HBox.setMargin(indicator, new Insets(0, 12, 0, 0));

            // Create message label
            Label messageLabel = new Label(message);
            messageLabel.setStyle(String.format(
                "-fx-text-fill: %s; -fx-font-size: 14px; -fx-font-weight: 500; " +
                "-fx-font-family: 'Segoe UI', sans-serif;",
                type.getTextColor()
            ));
            messageLabel.setWrapText(true);
            messageLabel.setMaxWidth(SNACKBAR_WIDTH - 60); // Account for padding and indicator

            snackbar.getChildren().addAll(indicator, messageLabel);

            // Create popup for snackbar
            Popup popup = new Popup();
            popup.getContent().add(snackbar);
            popup.setAutoHide(false);

            // Position snackbar at bottom right
            double sceneWidth = scene.getWidth();
            double sceneHeight = scene.getHeight();
            popup.setX(scene.getWindow().getX() + sceneWidth - SNACKBAR_WIDTH - MARGIN_RIGHT);
            popup.setY(scene.getWindow().getY() + sceneHeight - SNACKBAR_HEIGHT - MARGIN_BOTTOM);

            // Handle window resize to reposition snackbar
            scene.widthProperty().addListener((obs, oldVal, newVal) -> {
                if (popup.isShowing()) {
                    popup.setX(scene.getWindow().getX() + newVal.doubleValue() - SNACKBAR_WIDTH - MARGIN_RIGHT);
                }
            });

            scene.heightProperty().addListener((obs, oldVal, newVal) -> {
                if (popup.isShowing()) {
                    popup.setY(scene.getWindow().getY() + newVal.doubleValue() - SNACKBAR_HEIGHT - MARGIN_BOTTOM);
                }
            });

            scene.getWindow().xProperty().addListener((obs, oldVal, newVal) -> {
                if (popup.isShowing()) {
                    popup.setX(newVal.doubleValue() + scene.getWidth() - SNACKBAR_WIDTH - MARGIN_RIGHT);
                }
            });

            scene.getWindow().yProperty().addListener((obs, oldVal, newVal) -> {
                if (popup.isShowing()) {
                    popup.setY(newVal.doubleValue() + scene.getHeight() - SNACKBAR_HEIGHT - MARGIN_BOTTOM);
                }
            });

            // Show popup
            popup.show(scene.getWindow());

            // Animate in
            snackbar.setTranslateX(SNACKBAR_WIDTH);
            snackbar.setOpacity(0);

            TranslateTransition slideIn = new TranslateTransition(ANIMATION_DURATION, snackbar);
            slideIn.setToX(0);

            FadeTransition fadeIn = new FadeTransition(ANIMATION_DURATION, snackbar);
            fadeIn.setToValue(1);

            slideIn.play();
            fadeIn.play();

            // Auto hide after display duration
            FadeTransition fadeOut = new FadeTransition(ANIMATION_DURATION, snackbar);
            fadeOut.setToValue(0);
            fadeOut.setDelay(DISPLAY_DURATION);

            TranslateTransition slideOut = new TranslateTransition(ANIMATION_DURATION, snackbar);
            slideOut.setToX(SNACKBAR_WIDTH);
            slideOut.setDelay(DISPLAY_DURATION);

            fadeOut.setOnFinished(e -> popup.hide());
            slideOut.setOnFinished(e -> popup.hide());

            fadeOut.play();
            slideOut.play();
        });
    }

    // Convenience methods for different notification types
    public static void showDownloading(Scene scene) {
        show(scene, "Downloading image...", Type.INFO);
    }

    public static void showDownloadSuccess(Scene scene) {
        show(scene, "Image downloaded successfully", Type.SUCCESS);
    }

    public static void showWallpaperSetSuccess(Scene scene) {
        show(scene, "Wallpaper set successfully", Type.SUCCESS);
    }

    public static void showDownloadFailed(Scene scene) {
        show(scene, "Failed to download image", Type.ERROR);
    }

    public static void showWallpaperSetFailed(Scene scene) {
        show(scene, "Failed to set wallpaper", Type.ERROR);
    }
}