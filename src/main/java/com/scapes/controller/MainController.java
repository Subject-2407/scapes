package com.scapes.controller;

import com.scapes.core.ProviderManager;
import com.scapes.core.SystemHandler;
import com.scapes.model.WallpaperImage;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Screen;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
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

public class MainController {
    // --- Logger ---
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    // --- FXML Components ---
    @FXML private TabPane mainTabPane;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> providerCombo;
    @FXML private HBox topBar;
    @FXML private BorderPane rootPane;
    @FXML private Label welcomeLabel;
    @FXML private Label logoLabel;
    @FXML private Button themeBtn;
    @FXML private Button btnMin, btnMax, btnClose;

    // --- Online Tab Components ---
    @FXML private ScrollPane scrollPane; // Online ScrollPane

    // --- Local Tab Components ---
    @FXML private ScrollPane localScrollPane; // GANTI TilePane dengan ini di FXML!

    // --- State Variables ---
    private boolean isDarkMode = true;

    // --- Dependencies ---
    private ProviderManager providerManager;
    private SystemHandler systemHandler;

    // --- MASONRY STATE (ONLINE) ---
    private HBox masonryContainer;
    private List<VBox> masonryColumns = new ArrayList<>();
    private Map<VBox, Double> columnHeights = new HashMap<>();

    // --- MASONRY STATE (LOCAL) ---
    private HBox localMasonryContainer;
    private List<VBox> localMasonryColumns = new ArrayList<>();
    private Map<VBox, Double> localColumnHeights = new HashMap<>();

    // Shared Map (Card -> Column)
    private Map<VBox, VBox> cardColumnMap = new HashMap<>();

    // --- Desktop resolution ---
    private Rectangle2D screenBounds = Screen.getPrimary().getBounds();
    private double screenWidth = screenBounds.getWidth() * 0.8;
    private double screenHeight = screenBounds.getHeight() * 0.8;

    // --- HTTP Client ---
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();

    private double xOffset = 0;
    private double yOffset = 0;

    private Image iconWhite;
    private Image iconBlack;
    private ImageView headerIconView;

    private double storedX, storedY, storedWidth, storedHeight;
    private boolean isMaximized = false;

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

        applyCurrentTheme();

        if (welcomeLabel != null) {
            welcomeLabel.setVisible(true);
            welcomeLabel.setManaged(true);
        }
        scrollPane.setVisible(false);

        // --- SETUP MASONRY UNTUK KEDUA TAB ---
        masonryContainer = setupMasonryContainer(scrollPane);
        localMasonryContainer = setupMasonryContainer(localScrollPane);

        // Listener Resize untuk Responsive Columns
        rootPane.widthProperty().addListener((obs, o, n) -> {
            ensureMasonryColumns(masonryContainer, masonryColumns, columnHeights);
            ensureMasonryColumns(localMasonryContainer, localMasonryColumns, localColumnHeights);
        });
        
        // Initial Columns
        Platform.runLater(() -> {
            ensureMasonryColumns(masonryContainer, masonryColumns, columnHeights);
            ensureMasonryColumns(localMasonryContainer, localMasonryColumns, localColumnHeights);
        });

        // Window Controls
        setupWindowControls();
    }

    private void setupWindowControls() {
        Platform.runLater(() -> {
            if (rootPane.getScene() != null) {
                javafx.stage.Stage stage = (javafx.stage.Stage) rootPane.getScene().getWindow();
                // stage.maximizedProperty().addListener((obs, wasMaximized, isNowMaximized) -> {
                //     if (btnMax != null) btnMax.setText(isNowMaximized ? "❐" : "⬜");
                // });
            }
        });

        topBar.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });

        topBar.setOnMouseDragged(event -> {
            javafx.stage.Stage stage = (javafx.stage.Stage) topBar.getScene().getWindow();

            if (isMaximized) {
                // Kembalikan ke normal dulu
                isMaximized = false;
                if (btnMax != null) btnMax.setText("⬜");
                
                double currentMouseX = event.getScreenX();
                double ratio = (currentMouseX - stage.getX()) / stage.getWidth();
                
                stage.setWidth(storedWidth);
                stage.setHeight(storedHeight);
                
                stage.setX(currentMouseX - (storedWidth * ratio));
                stage.setY(event.getScreenY() - yOffset);
                
            } else {
                stage.setX(event.getScreenX() - xOffset);
                stage.setY(event.getScreenY() - yOffset);
            }
        });
    }

    // --- TAB LOGIC ---

    @FXML
    public void onLocalTabSelected() {
        if (mainTabPane.getSelectionModel().getSelectedIndex() == 1) {
            loadLocalImages();
        }
    }

    @FXML
    public void loadLocalImages() {
        if (localMasonryContainer == null) return;

        // Reset Local Masonry
        for (VBox col : localMasonryColumns) col.getChildren().clear();
        localColumnHeights.replaceAll((k, v) -> 0.0);
        
        // Tampilkan loading (opsional, bisa ditambah overlay)
        logger.info("Loading local images...");

        CompletableFuture.runAsync(() -> {
            List<WallpaperImage> localFiles = systemHandler.getDownloadedImages();
            Platform.runLater(() -> {
                if (localFiles.isEmpty()) {
                    // Handle empty state if needed
                } else {
                    for (WallpaperImage img : localFiles) {
                        VBox card = createLocalCard(img); // Buat kartu
                        // Masukkan ke Local Masonry
                        addCardToMasonry(card, localMasonryColumns, localColumnHeights);
                    }
                }
            });
        });
    }

    // --- GENERIC MASONRY LOGIC (Dipakai Online & Local) ---

    private HBox setupMasonryContainer(ScrollPane targetScroll) {
        if (targetScroll == null) return null;
        targetScroll.setFitToWidth(true);
        targetScroll.setPannable(true);
        targetScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        
        HBox container = new HBox(16);
        container.setPadding(new Insets(20, 35, 20, 20));
        container.setAlignment(Pos.TOP_CENTER);
        targetScroll.setContent(container);
        return container;
    }

    private void ensureMasonryColumns(HBox container, List<VBox> columns, Map<VBox, Double> heights) {
        if (container == null) return;
        
        double w = rootPane.getWidth();
        int cols = Math.max(1, (int) (w / 260));
        if (cols == columns.size() && !columns.isEmpty()) return;

        // Simpan kartu yang sudah ada agar tidak hilang saat resize
        List<VBox> existingCards = new ArrayList<>();
        for (VBox col : columns) {
            for (Node node : col.getChildren()) if (node instanceof VBox) existingCards.add((VBox) node);
        }

        container.getChildren().clear();
        columns.clear();
        heights.clear();

        for (int i = 0; i < cols; i++) {
            VBox col = new VBox(12);
            col.setFillWidth(true);
            columns.add(col);
            heights.put(col, 0.0);
            container.getChildren().add(col);
        }
        
        // Re-distribute cards
        for (VBox card : existingCards) addCardToMasonry(card, columns, heights);
    }

    private void addCardToMasonry(VBox card, List<VBox> columns, Map<VBox, Double> heights) {
        if (columns.isEmpty()) return; // Should call ensureMasonryColumns first
        
        VBox targetCol = columns.get(0);
        double min = heights.getOrDefault(targetCol, 0.0);
        
        for (VBox c : columns) {
            double h = heights.getOrDefault(c, 0.0);
            if (h < min) { min = h; targetCol = c; }
        }
        
        targetCol.getChildren().add(card);
        
        // Estimasi awal tinggi
        double est = card.prefHeight(-1);
        if (est <= 0) est = 300;
        
        heights.put(targetCol, heights.getOrDefault(targetCol, 0.0) + est + 12);
        cardColumnMap.put(card, targetCol);
    }

    // --- SEARCH LOGIC (ONLINE) ---

    @FXML
    public void onSearchAction() {
        String query = searchField.getText();
        if (query.isEmpty() || providerManager == null) return;

        mainTabPane.getSelectionModel().select(0);

        if (welcomeLabel != null) {
            welcomeLabel.setVisible(false);
            welcomeLabel.setManaged(false);
        }
        scrollPane.setVisible(true);

        // Clear Online Columns
        for (VBox col : masonryColumns) col.getChildren().clear();
        columnHeights.replaceAll((k, v) -> 0.0);

        providerManager.search(query, screenWidth, screenHeight)
                .thenAccept(this::displayOnlineImages)
                .exceptionally(ex -> { logger.error("Search failed", ex); return null; });
    }

    private void displayOnlineImages(List<WallpaperImage> images) {
        Platform.runLater(() -> {
            if (images.isEmpty()) return;
            for (WallpaperImage img : images) {
                VBox card = createOnlineCard(img);
                addCardToMasonry(card, masonryColumns, columnHeights);
            }
        });
    }

    public void initAppIcons(Image imgWhite, Image imgBlack) {
        this.iconWhite = imgWhite;
        this.iconBlack = imgBlack;

        // 1. Buat ImageView satu kali saja
        headerIconView = new ImageView();
        headerIconView.setFitHeight(40);
        headerIconView.setPreserveRatio(true);
        headerIconView.setSmooth(true);
        HBox.setMargin(headerIconView, new Insets(0, 10, 0, 0));

        // 2. Masukkan ke TopBar
        if (topBar != null) {
            // Hapus icon lama jika ada (biar ga numpuk kalau init dipanggil ulang)
            if (!topBar.getChildren().isEmpty() && topBar.getChildren().get(0) instanceof ImageView) {
                topBar.getChildren().remove(0);
            }
            topBar.getChildren().add(0, headerIconView);
        }

        // 3. Set Icon Awal (Sesuai mode saat ini)
        updateHeaderIcon();
    }

    private void updateHeaderIcon() {
        if (headerIconView == null) return;
        
        // Logika: Jika Dark Mode -> Pakai Icon Putih. Jika Light Mode -> Icon Hitam.
        if (isDarkMode) {
            if (iconWhite != null) headerIconView.setImage(iconWhite);
        } else {
            if (iconBlack != null) headerIconView.setImage(iconBlack);
        }
    }

    // --- CARD CREATION (Unified Style) ---

    // Helper untuk hitung lebar kolom saat ini
    private double getCurrentColWidth() {
        double w = rootPane.getWidth();
        int cols = Math.max(1, (int) (w / 260));
        return (w - 80 - (cols * 16)) / cols;
    }

    private VBox createOnlineCard(WallpaperImage data) {
        return createBaseCard(data, false);
    }

    private VBox createLocalCard(WallpaperImage data) {
        return createBaseCard(data, true);
    }

    // Base Method untuk membuat kartu (supaya kodenya tidak duplikat)
    private VBox createBaseCard(WallpaperImage data, boolean isLocal) {
        VBox card = new VBox(8);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPadding(new Insets(0));

        double colWidth = getCurrentColWidth();

        ImageView imageView = new ImageView();
        imageView.setFitWidth(colWidth);
        imageView.setPreserveRatio(true); // Default true agar aman
        imageView.setSmooth(true);

        Rectangle clip = new Rectangle(colWidth, 10);
        clip.setArcWidth(24); clip.setArcHeight(24);
        imageView.setClip(clip);

        // Panggil Loader yang sesuai
        if (isLocal) {
            loadLocalImageAsync(data.getThumbnailUrl(), imageView, clip, colWidth);
        } else {
            loadImageAsync(data.getThumbnailUrl(), imageView, clip);
        }

        Label descLabel = new Label(isLocal ? data.getId() : data.getDescription());
        descLabel.setMaxWidth(colWidth - 10);
        descLabel.setPadding(new Insets(0, 10, 10, 10));

        setupHoverEffect(card);

        // Click Event (Beda logic antara Online dan Local)
        card.setOnMouseClicked(e -> {
            card.setOpacity(0.7);
            CompletableFuture.runAsync(() -> {
                try {
                    if (isLocal) {
                        // Logic Set Local
                        logger.info("Setting local wallpaper: " + data.getId());
                        File file = new File(URI.create(data.getImageUrl()));
                        if (file.exists()) {
                            systemHandler.setWallpaper(file);
                            Platform.runLater(() -> card.setOpacity(1.0));
                        }
                    } else {
                        // Logic Download & Set Online
                        logger.info("Downloading wallpaper: " + data.getId());
                        File downloaded = systemHandler.downloadImage(data.getImageUrl(), data.getId() + ".jpg");
                        if (downloaded != null) {
                            systemHandler.setWallpaper(downloaded);
                            Platform.runLater(() -> card.setOpacity(1.0));
                        }
                    }
                } catch (Exception ex) {
                    logger.error("Error setting wallpaper", ex);
                    Platform.runLater(() -> card.setOpacity(1.0));
                }
            });
        });

        card.getChildren().addAll(imageView, descLabel);
        styleCardVBox(card);
        return card;
    }

    // --- LOADERS ---

    private void loadImageAsync(String url, ImageView target, Rectangle clip) {
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("User-Agent", "Mozilla/5.0").GET().build();
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() == 200) {
                    Image image = new Image(response.body());
                    Platform.runLater(() -> updateImageView(target, image, clip));
                }
            } catch (Exception e) { logger.error("Error online load", e); }
        });
    }

    private void loadLocalImageAsync(String url, ImageView target, Rectangle clip, double reqWidth) {
        CompletableFuture.runAsync(() -> {
            try {
                double widthToLoad = (reqWidth > 0) ? reqWidth : 300;
                // Resize saat loading untuk hemat RAM
                Image image = new Image(url, widthToLoad, 0, true, false);
                
                if (image.isError()) return;

                Platform.runLater(() -> updateImageView(target, image, clip));
            } catch (Exception e) { logger.error("Error local load", e); }
        });
    }

    // Logic Update Layout (Dipakai oleh kedua loader)
    private void updateImageView(ImageView target, Image image, Rectangle clip) {
        target.setImage(image);
        
        // KITA GUNAKAN PRESERVE RATIO AGAR TIDAK MELAR
        target.setPreserveRatio(true);
        
        // Hitung tinggi untuk layout Masonry (bukan untuk paksa ImageView)
        double w = target.getFitWidth();
        if (w <= 0) w = 200;
        
        double h = (image.getHeight() / image.getWidth()) * w;
        
        // Update Clip
        clip.setWidth(w);
        clip.setHeight(h);
        
        // Update Layout Container (Masonry)
        if (target.getParent() instanceof VBox) {
            VBox card = (VBox) target.getParent();
            card.setMaxWidth(w);
            
            // Cari kolom mana kartu ini berada
            VBox col = cardColumnMap.get(card);
            
            // Tentukan kita sedang update map yang mana (Online atau Local)
            Map<VBox, Double> targetHeightMap = null;
            if (masonryColumns.contains(col)) targetHeightMap = columnHeights;
            else if (localMasonryColumns.contains(col)) targetHeightMap = localColumnHeights;
            
            if (col != null && targetHeightMap != null) {
                double sum = 0;
                for (Node n : col.getChildren()) {
                    if (n instanceof Region) sum += ((Region) n).prefHeight(-1) + 12;
                }
                targetHeightMap.put(col, sum);
            }
        }
    }
    
    // --- STYLING METHODS ---
    
    private void setupHoverEffect(VBox card) {
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
    }

    private void styleCardVBox(VBox card) {
        String cardBg = isDarkMode ? "#181818" : "#ffffff";
        String textColor = isDarkMode ? "#dddddd" : "#333333";
        String shadowCol = isDarkMode ? "rgba(0,0,0,0.5)" : "rgba(0,0,0,0.15)";

        card.setBackground(new Background(new BackgroundFill(Color.web(cardBg), new CornerRadii(12), Insets.EMPTY)));
        card.setEffect(new DropShadow(10, 4, 4, Color.web(shadowCol)));

        for (Node n : card.getChildren()) {
            if (n instanceof Label) ((Label) n).setStyle("-fx-font-size: 12px; -fx-text-fill: " + textColor + ";");
        }
    }

    @FXML private void closeApp() { Platform.exit(); System.exit(0); }
    @FXML private void minimizeApp() { ((javafx.stage.Stage) rootPane.getScene().getWindow()).setIconified(true); }
    @FXML
    private void maximizeApp() {
        javafx.stage.Stage stage = (javafx.stage.Stage) rootPane.getScene().getWindow();
        
        // Ambil list layar (monitor) tempat window berada saat ini
        List<Screen> screens = Screen.getScreensForRectangle(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
        Screen screen = screens.isEmpty() ? Screen.getPrimary() : screens.get(0);

        Rectangle2D bounds = screen.getVisualBounds();

        if (isMaximized) {
            // --- RESTORE (Kembali ke ukuran semula) ---
            stage.setX(storedX);
            stage.setY(storedY);
            stage.setWidth(storedWidth);
            stage.setHeight(storedHeight);
            
            // Ganti Icon jadi Kotak (Maximize)
            if (btnMax != null) btnMax.setText("⬜"); 
            isMaximized = false;
        } else {
            // --- MAXIMIZE (Simpan dulu posisi sekarang) ---
            storedX = stage.getX();
            storedY = stage.getY();
            storedWidth = stage.getWidth();
            storedHeight = stage.getHeight();

            // Set ukuran sesuai Visual Bounds (Taskbar aman!)
            stage.setX(bounds.getMinX());
            stage.setY(bounds.getMinY());
            stage.setWidth(bounds.getWidth());
            stage.setHeight(bounds.getHeight());

            // Ganti Icon jadi Tumpuk (Restore)
            if (btnMax != null) btnMax.setText("❐"); 
            isMaximized = true;
        }
    }
    @FXML private void toggleTheme() {
        isDarkMode = !isDarkMode;
        applyCurrentTheme();
        updateExistingCards();
    }

    private void updateExistingCards() {
        // Update Masonry Online
        for (VBox col : masonryColumns) for (Node n : col.getChildren()) if (n instanceof VBox) styleCardVBox((VBox) n);
        // Update Masonry Local
        for (VBox col : localMasonryColumns) for (Node n : col.getChildren()) if (n instanceof VBox) styleCardVBox((VBox) n);
    }

    private void applyCurrentTheme() {
        // Tentukan Palette Warna
        String bgRoot = isDarkMode ? "#000000" : "#f4f4f4"; 
        String bgBar  = isDarkMode ? "#121212" : "#ffffff"; 
        String bgInput= isDarkMode ? "#1e1e1e" : "#eeeeee"; 
        String textCol= isDarkMode ? "white"   : "black";
        String subText= isDarkMode ? "#aaaaaa" : "#555555";
        String accent = "#0c6370"; 

        // --- WARNA SCROLLBAR ---
        String scrollColor = isDarkMode ? "#333333" : "#C0C0C0";

        String tabText     = isDarkMode ? "#ff0000" : "#777777"; 
        
        // Tab Text Selected: Putih (Dark) / Hitam (Light)
        String tabTextSel  = isDarkMode ? "white"   : "black"; 
        
        // Tab Hover BG: Putih transparan (Dark) / Hitam transparan (Light)
        String tabHoverBg  = isDarkMode ? "rgba(255, 255, 255, 0.1)" : "rgba(0, 0, 0, 0.05)";

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

        updateHeaderIcon();

        // Tambahkan warna border
        String borderColor = isDarkMode ? "#333333" : "#cccccc";

        // --- 1. Root Styling (Radius & Border) ---
        // Kita gunakan CSS string yang lengkap disini
        String rootStyle = 
        "-fx-background-color: " + bgRoot + ";" +   // Warna Background
        "-fx-background-radius: 12;" +              // Lengkungan Background
        "-fx-border-color: " + borderColor + ";" +  // Warna Garis
        "-fx-border-width: 1;" +
        "-fx-border-radius: 12;" +                  // Lengkungan Garis (Harus sama dgn background)
        "-fx-thumb-color: " + scrollColor + ";" +
            
            "-theme-tab-text: " + tabText + ";" +
            "-theme-tab-text-selected: " + tabTextSel + ";" +
            "-theme-tab-hover-bg: " + tabHoverBg + ";";

        // PENTING: Reset background JavaFX object agar tidak menimpa CSS
        rootPane.setBackground(Background.EMPTY); 
        rootPane.setStyle(rootStyle);

        topBar.setStyle(
            "-fx-background-color: " + bgBar + ";" +
            "-fx-background-radius: 12 12 0 0;" 
        );
        topBar.setEffect(new DropShadow(5, Color.rgb(0,0,0, isDarkMode ? 0.5 : 0.1)));
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
                        currentBg = "#0c6370";
                        currentTextFill = "white";
                    } else {
                        currentBg = bgInput;
                        currentTextFill = textCol; 
                    }

                    setStyle(baseFont + "-fx-background-color: " + currentBg + "; -fx-text-fill: " + currentTextFill + ";");

                    setOnMouseEntered(e -> setStyle(baseFont + "-fx-background-color: #0c6370; -fx-text-fill: white;"));
                    setOnMouseExited(e -> {
                        String bg = isSelected() ? "#0c6370" : bgInput;
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
}