package com.scapes.controller;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.scapes.core.ProviderManager;
import com.scapes.core.SystemHandler;
import com.scapes.model.WallpaperImage;

import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.util.Duration;

public class MainController {

    // --- FXML Components ---
    @FXML private TextField searchField;
    @FXML private ComboBox<String> providerCombo;
    @FXML private ScrollPane scrollPane;
    @FXML private HBox topBar;
    @FXML private BorderPane rootPane;
    @FXML private Label welcomeLabel;
    @FXML private Label logoLabel;
    @FXML private Button themeBtn;
    @FXML private Button btnMin, btnMax, btnClose;
    
    // --- State Variables ---
    private boolean isDarkMode = true; // Default Dark Mode

    // --- Dependencies & Layout ---
    private ProviderManager providerManager;
    private SystemHandler systemHandler;
    private HBox masonryContainer;
    private List<VBox> masonryColumns = new ArrayList<>();
    private Map<VBox, Double> columnHeights = new HashMap<>();
    private Map<VBox, VBox> cardColumnMap = new HashMap<>();
    private static final HttpClient client = HttpClient.newHttpClient();
    private double xOffset = 0;
    private double yOffset = 0;

    public void init(ProviderManager manager, SystemHandler system) {
        this.providerManager = manager;
        this.systemHandler = system;

        // Setup Provider
        if (manager.getAvailableProviders() != null) {
            providerCombo.getItems().addAll(manager.getAvailableProviders());
        }
        if (!providerCombo.getItems().isEmpty()) {
            providerCombo.getSelectionModel().select(0);
            manager.setActiveProvider(providerCombo.getItems().get(0));
        }
        providerCombo.setOnAction(e -> manager.setActiveProvider(providerCombo.getValue()));

        // Setup Awal
        applyCurrentTheme(); 
        
        if (welcomeLabel != null) {
            welcomeLabel.setVisible(true);
            welcomeLabel.setManaged(true);
        }
        scrollPane.setVisible(false);

        setupMasonryLayout();

        // --- UPDATE BARU: LISTENER STATUS WINDOW (Maximize/Restore Icon) ---
        // Kita gunakan Platform.runLater agar stage sudah siap sebelum diakses
        Platform.runLater(() -> {
            javafx.stage.Stage stage = (javafx.stage.Stage) rootPane.getScene().getWindow();
            
            // Listener: Setiap kali window berubah ukuran (Maximized/Normal), ganti ikon tombol
            stage.maximizedProperty().addListener((obs, wasMaximized, isNowMaximized) -> {
                if (isNowMaximized) {
                    btnMax.setText("❐"); // Icon Restore (Tumpuk)
                } else {
                    btnMax.setText("⬜"); // Icon Maximize (Kotak)
                }
            });
        });
        // -------------------------------------------------------------------

        // logika drag window
        topBar.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });

        // Saat mouse ditarik, pindahkan window
        topBar.setOnMouseDragged(event -> {
            javafx.stage.Stage stage = (javafx.stage.Stage) topBar.getScene().getWindow();
            
            // Jika window sedang maximized dan ditarik, kembalikan ke normal
            if (stage.isMaximized()) {
                stage.setMaximized(false);
                // Icon akan otomatis berubah karena listener di atas
            } else {
                stage.setX(event.getScreenX() - xOffset);
                stage.setY(event.getScreenY() - yOffset);
            }
        });
    }

    // --- THEME LOGIC ---

    @FXML
    private void closeApp() {
        Platform.exit();
        System.exit(0);
    }

    @FXML
    private void minimizeApp() {
        javafx.stage.Stage stage = (javafx.stage.Stage) rootPane.getScene().getWindow();
        stage.setIconified(true);
    }

    @FXML
    private void maximizeApp() {
        javafx.stage.Stage stage = (javafx.stage.Stage) rootPane.getScene().getWindow();
        stage.setMaximized(!stage.isMaximized()); // Toggle Maximize
        // Tidak perlu set text manual disini, listener di init() yang akan mengurusnya
    }

    @FXML
    private void toggleTheme() {
        isDarkMode = !isDarkMode; // Switch status
        applyCurrentTheme();      // Terapkan warna baru ke UI utama
        updateExistingCards();    // Update warna kartu yang sudah ada
    }

    private void applyCurrentTheme() {
        // Tentukan Palette Warna
        String bgRoot = isDarkMode ? "#000000" : "#f4f4f4"; 
        String bgBar  = isDarkMode ? "#121212" : "#ffffff"; 
        String bgInput= isDarkMode ? "#1e1e1e" : "#eeeeee"; 
        String textCol= isDarkMode ? "white"   : "black";
        String subText= isDarkMode ? "#aaaaaa" : "#555555";
        String accent = "#0078d7"; 

        // --- WARNA SCROLLBAR ---
        String scrollColor = isDarkMode ? "#333333" : "#C0C0C0";

        // 1. Root: Set Background DAN Definisikan Variabel Scrollbar disini
        // Memindahkan definisi variabel ke root akan memperbaiki error "String cannot be cast to Paint"
        rootPane.setBackground(new Background(new BackgroundFill(Color.web(bgRoot), CornerRadii.EMPTY, Insets.EMPTY)));
        rootPane.setStyle("-fx-thumb-color: " + scrollColor + ";"); // <--- PINDAH KE SINI

        // 2. ScrollPane: Hapus variabel dari sini, cukup transparan saja
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;"); // <--- LEBIH BERSIH

        // 3. Top Bar
        topBar.setBackground(new Background(new BackgroundFill(Color.web(bgBar), CornerRadii.EMPTY, Insets.EMPTY)));
        topBar.setPadding(new Insets(14));
        topBar.setEffect(new DropShadow(5, Color.rgb(0,0,0, isDarkMode ? 0.5 : 0.1)));

        // Logo
        if(logoLabel != null) logoLabel.setTextFill(Color.web(textCol));

        // 4. Search Field
        searchField.setBackground(new Background(new BackgroundFill(Color.web(bgInput), new CornerRadii(8), Insets.EMPTY)));
        searchField.setPadding(new Insets(10));
        searchField.setStyle("-fx-text-fill: " + textCol + "; -fx-prompt-text-fill: " + subText + "; -fx-font-size: 14px;");

        // 5. Toggle Button Style
        themeBtn.setText(isDarkMode ? "☀" : "🌙");
        themeBtn.setBackground(new Background(new BackgroundFill(Color.web(bgInput), new CornerRadii(8), Insets.EMPTY)));
        themeBtn.setTextFill(Color.web(textCol));
        themeBtn.setPadding(new Insets(8, 12, 8, 12));
        themeBtn.setStyle("-fx-font-size: 16px; -fx-cursor: hand;");

        // 6. Welcome Label
        if (welcomeLabel != null) {
            welcomeLabel.setTextFill(Color.web(textCol));
            welcomeLabel.setStyle("-fx-font-size: 64px; -fx-font-family: 'Segoe UI Light', 'Arial'; -fx-opacity: 0.9;");
        }

        // 7. Tombol Biasa (Cari)
        topBar.getChildren().stream()
                .filter(n -> n instanceof Button && n != themeBtn && n != btnMin && n != btnMax && n != btnClose)
                .forEach(n -> {
                    Button b = (Button) n;
                    b.setBackground(new Background(new BackgroundFill(Color.web(accent), new CornerRadii(8), Insets.EMPTY)));
                    b.setTextFill(Color.WHITE);
                    b.setPadding(new Insets(9, 20, 9, 20));
                    b.setStyle("-fx-font-weight: bold; -fx-cursor: hand;");
                });

        // 8. ComboBox Styling
        styleComboBox(bgInput, textCol, subText);

        // 9. Styling Window Controls
        String winBtnColor = isDarkMode ? "white" : "black";
        String hoverColor = isDarkMode ? "#333333" : "#e0e0e0";
        String baseStyle = "-fx-background-color: transparent; -fx-text-fill: " + winBtnColor + "; -fx-font-size: 14px; -fx-font-weight: bold;";
        
        if (btnMin != null) {
            btnMin.setStyle(baseStyle);
            btnMin.setOnMouseEntered(e -> btnMin.setStyle("-fx-background-color: " + hoverColor + "; -fx-text-fill: " + winBtnColor + "; -fx-font-size: 14px;"));
            btnMin.setOnMouseExited(e -> btnMin.setStyle(baseStyle));
        }
        if (btnMax != null) {
            btnMax.setStyle(baseStyle);
            btnMax.setOnMouseEntered(e -> btnMax.setStyle("-fx-background-color: " + hoverColor + "; -fx-text-fill: " + winBtnColor + "; -fx-font-size: 14px;"));
            btnMax.setOnMouseExited(e -> btnMax.setStyle(baseStyle));
        }
        if (btnClose != null) {
            btnClose.setStyle(baseStyle);
            btnClose.setOnMouseEntered(e -> btnClose.setStyle("-fx-background-color: #e81123; -fx-text-fill: white; -fx-font-size: 14px;"));
            btnClose.setOnMouseExited(e -> btnClose.setStyle(baseStyle));
        }
    }

    private void styleComboBox(String bgInput, String textCol, String subText) {
        // --- SETUP WARNA ---
        String borderColor = isDarkMode ? "#333333" : "#cccccc"; 
        String arrowColor  = isDarkMode ? "#aaaaaa" : "#333333"; 
        
        // Shadow Popup
        String shadowEffect = isDarkMode 
            ? "dropshadow(three-pass-box, rgba(0,0,0,0.6), 8, 0, 0, 4)" 
            : "dropshadow(three-pass-box, rgba(0,0,0,0.1), 8, 0, 0, 2)";

        // --- 1. STYLING TOMBOL UTAMA ---
        providerCombo.setStyle(
            "-fx-background-color: " + bgInput + ";" +
            "-fx-background-radius: 8;" +
            "-fx-border-color: " + borderColor + ";" +
            "-fx-border-radius: 8;" +
            "-fx-border-width: 1px;" +
            "-fx-text-fill: " + textCol + ";" + 
            "-fx-font-size: 14px;" +
            "-fx-font-family: 'Segoe UI';" +
            "-fx-padding: 2 6 2 10;"
        );

        // --- 2. STYLING PANAH ---
        Platform.runLater(() -> {
            Node arrow = providerCombo.lookup(".arrow");
            Node arrowBtn = providerCombo.lookup(".arrow-button");
            if (arrow != null) arrow.setStyle("-fx-background-color: " + arrowColor + ";");
            if (arrowBtn != null) arrowBtn.setStyle("-fx-background-insets: 0; -fx-padding: 8; -fx-background-color: transparent;");
        });

        // --- 3. STYLING LIST CELLS (ISI DROPDOWN) ---
        providerCombo.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                
                setText(null);
                setGraphic(null);
                
                if (empty || item == null) {
                    setStyle("-fx-background-color: transparent;");
                } else {
                    setText(item);
                    String baseFont = "-fx-font-family: 'Segoe UI'; -fx-font-size: 14px; -fx-padding: 8 12 8 12; ";
                    String currentBg;
                    String currentTextFill;

                    if (isSelected() || isHover()) {
                        currentBg = "#0078d7";
                        currentTextFill = "white";
                    } else {
                        currentBg = bgInput;
                        currentTextFill = textCol; 
                    }

                    setStyle(baseFont + "-fx-background-color: " + currentBg + "; -fx-text-fill: " + currentTextFill + ";");

                    setOnMouseEntered(e -> setStyle(baseFont + "-fx-background-color: #0078d7; -fx-text-fill: white;"));
                    setOnMouseExited(e -> {
                        String bg = isSelected() ? "#0078d7" : bgInput;
                        String tf = isSelected() ? "white" : textCol;
                        setStyle(baseFont + "-fx-background-color: " + bg + "; -fx-text-fill: " + tf + ";");
                    });
                }
            }
        });

        // --- 4. BUTTON CELL ---
        providerCombo.setButtonCell(new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty && item != null) {
                    setText(item);
                    setStyle("-fx-text-fill: " + textCol + "; -fx-font-size: 14px; -fx-font-family: 'Segoe UI'; -fx-background-color: transparent;");
                } else { setText(null); }
            }
        });

        // --- 5. POPUP BORDER ---
        providerCombo.setOnShowing(event -> {
            Node listView = providerCombo.lookup(".list-view");
            if (listView != null) {
                listView.setStyle(
                    "-fx-background-color: " + bgInput + ";" +
                    "-fx-background-insets: 0;" +
                    "-fx-padding: 0;" +
                    "-fx-background-radius: 8;" +
                    "-fx-border-color: " + borderColor + ";" +
                    "-fx-border-radius: 8;" +
                    "-fx-border-width: 1px;" +
                    "-fx-effect: " + shadowEffect + ";"
                );
            }
        });
        
        providerCombo.skinProperty().addListener((obs, old, skin) -> {
            if(skin != null) {
                Node listView = providerCombo.lookup(".list-view");
                if (listView != null) {
                    listView.setStyle(
                        "-fx-background-color: " + bgInput + ";" +
                        "-fx-background-insets: 0;" +
                        "-fx-padding: 0;" +
                        "-fx-background-radius: 8;" +
                        "-fx-border-color: " + borderColor + ";" +
                        "-fx-border-radius: 8;" +
                        "-fx-border-width: 1px;" +
                        "-fx-effect: " + shadowEffect + ";"
                    );
                }
            }
        });
    }

    // Update kartu yang sudah tampil saat tema diganti
    private void updateExistingCards() {
        for (VBox col : masonryColumns) {
            for (Node node : col.getChildren()) {
                if (node instanceof VBox) {
                    styleCardVBox((VBox) node);
                }
            }
        }
    }

    // Fungsi styling single card
    private void styleCardVBox(VBox card) {
        String cardBg    = isDarkMode ? "#181818" : "#ffffff";
        String textColor = isDarkMode ? "#dddddd" : "#333333"; 
        String shadowCol = isDarkMode ? "rgba(0,0,0,0.5)" : "rgba(0,0,0,0.15)";

        BackgroundFill fill = new BackgroundFill(Color.web(cardBg), new CornerRadii(12), Insets.EMPTY);
        card.setBackground(new Background(fill));

        DropShadow shadow = new DropShadow();
        shadow.setRadius(10); 
        shadow.setOffsetY(4);
        shadow.setColor(Color.web(shadowCol));
        card.setEffect(shadow);

        for (Node n : card.getChildren()) {
            if (n instanceof Label) {
                Label lbl = (Label) n;
                lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: " + textColor + ";");
            }
        }
    }

    // --- MASONRY & SEARCH LOGIC ---

    private void setupMasonryLayout() {
        rootPane.widthProperty().addListener((obs, o, n) -> ensureMasonryColumns());
        scrollPane.setFitToWidth(true); scrollPane.setPannable(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        masonryContainer = new HBox(16);
        masonryContainer.setPadding(new Insets(20, 35, 20, 20));
        masonryContainer.setAlignment(Pos.TOP_CENTER);
        scrollPane.setContent(masonryContainer);
        ensureMasonryColumns();
    }

    private void ensureMasonryColumns() {
        double w = rootPane.getWidth();
        int cols = Math.max(1, (int) (w / 260));
        if (cols == masonryColumns.size() && !masonryColumns.isEmpty()) return;

        List<VBox> existingCards = new ArrayList<>();
        for (VBox col : masonryColumns) {
            for (Node node : col.getChildren()) if (node instanceof VBox) existingCards.add((VBox) node);
        }

        masonryContainer.getChildren().clear();
        masonryColumns.clear();
        columnHeights.clear();

        for (int i = 0; i < cols; i++) {
            VBox col = new VBox(12);
            col.setFillWidth(true);
            masonryColumns.add(col);
            columnHeights.put(col, 0.0);
            masonryContainer.getChildren().add(col);
        }
        for (VBox card : existingCards) addCardToMasonry(card);
    }

    @FXML
    public void onSearchAction() {
        String query = searchField.getText();
        if (query.isEmpty() || providerManager == null) return;
        
        if (welcomeLabel != null) {
            welcomeLabel.setVisible(false);
            welcomeLabel.setManaged(false);
        }
        scrollPane.setVisible(true);

        for(VBox col : masonryColumns) col.getChildren().clear();
        columnHeights.replaceAll((k, v) -> 0.0);

        providerManager.search(query)
            .thenAccept(this::displayImages)
            .exceptionally(ex -> { ex.printStackTrace(); return null; });
    }

    private void displayImages(List<WallpaperImage> images) {
        Platform.runLater(() -> {
            if (images.isEmpty()) return;
            for (WallpaperImage img : images) {
                VBox card = createCard(img);
                addCardToMasonry(card);
            }
        });
    }

    private void addCardToMasonry(VBox card) {
        if (masonryColumns.isEmpty()) ensureMasonryColumns();
        VBox targetCol = masonryColumns.get(0);
        double min = columnHeights.getOrDefault(targetCol, 0.0);
        for (VBox c : masonryColumns) {
            double h = columnHeights.getOrDefault(c, 0.0);
            if (h < min) { min = h; targetCol = c; }
        }
        targetCol.getChildren().add(card);
        double est = card.prefHeight(-1); 
        if (est <= 0) est = 300; 
        columnHeights.put(targetCol, columnHeights.getOrDefault(targetCol, 0.0) + est + 12);
        cardColumnMap.put(card, targetCol);
    }

    private VBox createCard(WallpaperImage data) {
        VBox card = new VBox(8);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPadding(new Insets(0));

        // Setup Gambar
        ImageView imageView = new ImageView();
        double w = rootPane.getWidth();
        int cols = Math.max(1, (int) (w / 260));
        double colWidth = (w - 80 - (cols * 16)) / cols;
        
        imageView.setFitWidth(colWidth); 
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);

        Rectangle clip = new Rectangle(colWidth, 10);
        clip.setArcWidth(24); 
        clip.setArcHeight(24);
        imageView.setClip(clip);

        loadImageAsync(data.getThumbnailUrl(), imageView, clip);

        // Setup Label
        Label descLabel = new Label(data.getDescription());
        descLabel.setMaxWidth(colWidth - 10);
        descLabel.setPadding(new Insets(0, 10, 10, 10));
        
        // Setup Hover Effect
        ScaleTransition stEnter = new ScaleTransition(Duration.millis(150), card);
        stEnter.setToX(1.02); stEnter.setToY(1.02);
        ScaleTransition stExit = new ScaleTransition(Duration.millis(150), card);
        stExit.setToX(1.0); stExit.setToY(1.0);

        card.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> {
            stExit.stop(); stEnter.playFromStart();
            card.setStyle("-fx-cursor: hand;");
        });
        card.addEventHandler(MouseEvent.MOUSE_EXITED, e -> {
            stEnter.stop(); stExit.playFromStart();
        });
        card.setOnMouseClicked(e -> {
            System.out.println("User choose: " + data.getImageUrl());
        });

        // MASUKKAN ELEMEN KE KARTU DULU
        card.getChildren().addAll(imageView, descLabel);

        // --- PENTING: PANGGIL STYLING DI AKHIR ---
        styleCardVBox(card); 

        return card;
    }

    private void loadImageAsync(String url, ImageView target, Rectangle clip) {
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("User-Agent", "Mozilla/5.0").GET().build();
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() == 200) {
                    Image image = new Image(response.body());
                    Platform.runLater(() -> {
                        target.setImage(image);
                        double w = target.getFitWidth(); if (w <= 0) w = 200;
                        double h = (image.getHeight() / image.getWidth()) * w;
                        clip.setWidth(w); clip.setHeight(h);
                        if (target.getParent() instanceof VBox) {
                            VBox card = (VBox) target.getParent();
                            card.setMaxWidth(w);
                            VBox col = cardColumnMap.get(card);
                            if (col != null) {
                                double sum = 0;
                                for (Node n : col.getChildren()) sum += ((Region) n).prefHeight(-1) + 12;
                                columnHeights.put(col, sum);
                            }
                        }
                    });
                }
            } catch (Exception e) {}
        });
    }
}