package com.scapes;

import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.scapes.controller.MainController;
import com.scapes.core.ProviderManager;
import com.scapes.core.SystemHandler;
import com.scapes.impl.PexelsProvider;
import com.scapes.impl.UnsplashProvider;
import com.scapes.impl.WindowsSystemHandler;
import com.scapes.util.ResizeHelper;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

public class MainApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(MainApp.class);
    private static final String APP_TITLE = "Scapes";
    private static final String APP_ICON = "/com/scapes/icon/scapes.png";
    private static final String APP_ICON_DARK = "/com/scapes/icon/scapes_light.png";
    private static final String APP_ICON_LIGHT = "/com/scapes/icon/scapes_dark.png";
    private static final String MAIN_VIEW_FXML = "/com/scapes/view/main_view.fxml";
    private static final String APP_STYLE = "/com/scapes/view/style.css";

    @Override
    public void start(Stage stage) throws Exception {
        logger.info("Starting Scapes...");

        // center the stage on screen
        stage.centerOnScreen();

        // system handler setup
        logger.info("Detecting operating system...");
        SystemHandler system = null;
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            system = new WindowsSystemHandler();
        } else {
            Exception ex = new UnsupportedOperationException("Only Windows OS is supported currently.");
            Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle(APP_TITLE);
            alert.setHeaderText("Startup Error");
            alert.setContentText("Unsupported OS detected: " + osName + "\n" + ex.getMessage());
            alert.showAndWait();
            Platform.exit();
            logger.error("Unsupported OS: {}", osName);
            return;
        }

        // load view
        logger.info("Loading main view...");
        FXMLLoader loader = new FXMLLoader(getClass().getResource(MAIN_VIEW_FXML));
        String css = this.getClass().getResource(APP_STYLE).toExternalForm();
        Scene scene = new Scene(loader.load());
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        // favicon
        Image appIcon = new Image(getClass().getResourceAsStream(APP_ICON));
        if (appIcon != null) {
            stage.getIcons().add(appIcon);
        }

        scene.getStylesheets().add(css);
        stage.initStyle(javafx.stage.StageStyle.TRANSPARENT);
        
        // wallpaper provider setup
        logger.info("Setting up wallpaper providers...");
        ProviderManager manager = new ProviderManager();
        manager.registerProvider(new UnsplashProvider()); // source 1: Unsplash
        manager.registerProvider(new PexelsProvider());   // source 2: Pexels

        // inject dependencies into controller
        logger.info("Initializing main controller...");
        MainController controller = loader.getController();
        controller.init(manager, system); 

        // load app icon for app top bar
        try {
            Image imgWhite = null;
            Image imgBlack = null;

            InputStream streamWhite = getClass().getResourceAsStream(APP_ICON_LIGHT);
            if (streamWhite != null) imgWhite = new Image(streamWhite);
 
            InputStream streamBlack = getClass().getResourceAsStream(APP_ICON_DARK);
            if (streamBlack != null) imgBlack = new Image(streamBlack);

            if (imgWhite != null && imgBlack != null) {
                controller.initAppIcons(imgWhite, imgBlack);
            } else {
                logger.error("Failed loading app icons.");
            }

        } catch (Exception e) {
            logger.error("Error loading app icons: ", e);
        }

        // show the stage
        // set stage min size with respecting current scene size

        stage.setTitle(APP_TITLE);
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        ResizeHelper.addResizeListener(stage);
        stage.show();

        logger.info("Scapes started successfully.");
    }

    public static void main(String[] args) {
        logger.info("Launching application...");
        launch();
    }
}