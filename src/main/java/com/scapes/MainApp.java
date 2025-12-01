package com.scapes;

import com.scapes.controller.MainController;
import com.scapes.core.ProviderManager;
import com.scapes.impl.PexelsProvider;
import com.scapes.impl.UnsplashProvider;
import com.scapes.impl.WindowsSystemHandler;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class MainApp extends Application {
    private static final String APP_TITLE = "4scapes";
    private static final String APP_ICON = "/com/scapes/icon/scapes.png";
    private static final String MAIN_VIEW_FXML = "/com/scapes/view/main_view.fxml";
    private static final String APP_STYLE = "/com/scapes/view/style.css";

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(MAIN_VIEW_FXML));
        String css = this.getClass().getResource(APP_STYLE).toExternalForm();
        Scene scene = new Scene(loader.load());
        Image appIcon = new Image(getClass().getResourceAsStream(APP_ICON));
        if (appIcon != null) {
            stage.getIcons().add(appIcon);
        }

        scene.getStylesheets().add(css);
        stage.initStyle(javafx.stage.StageStyle.UNDECORATED);
        
        // wallpaper provider setup
        ProviderManager manager = new ProviderManager();
        manager.registerProvider(new UnsplashProvider()); // source 1: Unsplash
        manager.registerProvider(new PexelsProvider());   // source 2: Pexels

        // system handler setup (for Windows)
        WindowsSystemHandler system = new WindowsSystemHandler();

        // inject dependencies into controller
        MainController controller = loader.getController();
        controller.init(manager, system); 

        stage.setTitle(APP_TITLE);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}