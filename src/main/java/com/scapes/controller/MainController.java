package com.scapes.controller;

import com.scapes.core.ProviderManager;
import com.scapes.core.SystemHandler;
import com.scapes.impl.SuggestionService;
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
    private boolean isDarkMode = isWindowsDarkMode();

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

    // Suggestion Service
    private SuggestionService suggestionService = new SuggestionService();
    private ContextMenu suggestionsPopup;

    // --- Pagination State ---
    private int currentPage = 1;
    private boolean isLoading = false;
    private String currentQuery = "";
    private List<Node> activeSkeletons = new ArrayList<>();

    // --- Hybrid Explore Tab ---
    private VBox exploreContainer;
    private HBox searchMasonry;

    public void init(ProviderManager manager, SystemHandler system) {
        this.providerManager = manager;
        this.systemHandler = system;

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.trim().isEmpty()) {
                toggleViewMode(false);
            }
        });

        // splashStatusLabel.setText("Menyiapkan galeri...");

        loadExploreSections().thenRun(() -> {
            Platform.runLater(() -> {
                // Tampilkan mode explore
                toggleViewMode(false);
                
                // // Hide splash
                // new java.util.Timer().schedule(new java.util.TimerTask() {
                //     @Override public void run() { Platform.runLater(() -> hideSplashScreen()); }
                // }, 500);
            });
        });

        // Setup Provider
        if (manager.getAvailableProviders() != null) {
            providerCombo.getItems().addAll(manager.getAvailableProviders());
        }
        if (!providerCombo.getItems().isEmpty()) {
            providerCombo.getSelectionModel().select(0);
            manager.setActiveProvider(providerCombo.getItems().get(0));
        }
        providerCombo.setOnAction(e -> manager.setActiveProvider(providerCombo.getValue()));

        // 1. Setup Container Explore (VBox)
        exploreContainer = new VBox(20); // Jarak antar kategori
        exploreContainer.setPadding(new Insets(20, 35, 50, 20));
        exploreContainer.setAlignment(Pos.TOP_LEFT);
        exploreContainer.setFillWidth(true);

        // 2. Setup Container Search Masonry (HBox)
        searchMasonry = new HBox(16);
        searchMasonry.setPadding(new Insets(20, 35, 20, 20));
        searchMasonry.setAlignment(Pos.TOP_CENTER);

        if (welcomeLabel != null) {
            welcomeLabel.setVisible(true);
            welcomeLabel.setManaged(true);
        }
        scrollPane.setVisible(false);

        localMasonryContainer = setupMasonryContainer(localScrollPane);

        // Listener Resize untuk Responsive Columns
        rootPane.widthProperty().addListener((obs, o, n) -> {
            ensureMasonryColumns(searchMasonry, masonryColumns, columnHeights);
            ensureMasonryColumns(localMasonryContainer, localMasonryColumns, localColumnHeights);
        });

        Platform.runLater(() -> {
            ensureMasonryColumns(searchMasonry, masonryColumns, columnHeights);
            ensureMasonryColumns(localMasonryContainer, localMasonryColumns, localColumnHeights);
        });

        setupSearchSuggestions();
        setupWindowControls();
        setupFocusTraversal();

        scrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
            // Cek apakah konten scrollPane saat ini adalah searchMasonry (bukan explore)
            if (scrollPane.getContent() == searchMasonry) {
                if (newVal.doubleValue() > 0.8 && !isLoading && !currentQuery.isEmpty()) {
                    loadNextPage();
                }
            }
        });

        applyCurrentTheme();
    }

    private void toggleViewMode(boolean isSearchMode) {
        if (welcomeLabel != null) {
            welcomeLabel.setVisible(false); 
            welcomeLabel.setManaged(false);
        }
        scrollPane.setVisible(true);

        if (isSearchMode) {
            // Mode Pencarian: Tampilkan Masonry Grid
            scrollPane.setContent(searchMasonry);
            // Reset scroll ke atas
            scrollPane.setVvalue(0);
        } else {
            // Mode Explore: Tampilkan Kategori (VBox)
            scrollPane.setContent(exploreContainer);
            // Reset scroll ke atas
            scrollPane.setVvalue(0);
        }
    }

    private CompletableFuture<Void> loadExploreSections() {
        return CompletableFuture.runAsync(() -> {
            Platform.runLater(() -> exploreContainer.getChildren().clear());

            suggestionService.getTrendingKeywords().thenAccept(trending -> {
                String topTrend = trending.isEmpty() ? "Nature" : trending.get(0);
                
                // trending category
                buildCategorySection("🔥 Trending This Week", topTrend, true);

                // common wallpaper categories
                runDelayed(() -> buildCategorySection("Nature", "Nature", false), 200);
                runDelayed(() -> buildCategorySection("Sky", "Sky", false), 400);
                runDelayed(() -> buildCategorySection("Automotive", "Car", false), 600);
                runDelayed(() -> buildCategorySection("Anime & Japan", "Anime", false), 800);
                runDelayed(() -> buildCategorySection("Minimalist", "Minimalist", false), 1000);
            });
        });
    }

    private void runDelayed(Runnable r, int ms) {
        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override public void run() { Platform.runLater(r); }
        }, ms);
    }

    private void buildCategorySection(String title, String query, boolean isTrendingSection) {
        VBox sectionBox = new VBox(10);
        sectionBox.setPadding(new Insets(0, 0, 10, 0));

        // Header Section
        HBox header = new HBox(15);
        header.setAlignment(Pos.CENTER_LEFT);
        
        Label lblTitle = new Label(title);
        lblTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + (isDarkMode ? "white" : "#333333") + ";");
        
        Button btnSeeAll = new Button("See All");
        btnSeeAll.setStyle("-fx-background-color: transparent; -fx-text-fill: #0c6370; -fx-font-weight: bold; -fx-cursor: hand;");
        btnSeeAll.setOnAction(e -> {
            searchField.setText(query);
            onSearchAction();
        });

        header.getChildren().addAll(lblTitle, btnSeeAll);

        ScrollPane hScroll = new ScrollPane();
        hScroll.setFitToHeight(true);
        hScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        hScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); 
        hScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        hScroll.setPannable(true);

        HBox contentBox = new HBox(15);
        contentBox.setPadding(new Insets(10));

        contentBox.setAlignment(Pos.TOP_LEFT); 
        contentBox.setFillHeight(false);
        if (isTrendingSection) {
            contentBox.setAlignment(Pos.CENTER_LEFT); 
        } else {
            contentBox.setAlignment(Pos.TOP_LEFT);
        }
        
        contentBox.setFillHeight(false);

        hScroll.setContent(contentBox);

        Label loadingLbl = new Label("Loading...");
        loadingLbl.setStyle("-fx-text-fill: gray;");
        contentBox.getChildren().add(loadingLbl);

        sectionBox.getChildren().addAll(header, hScroll);
        
        Platform.runLater(() -> exploreContainer.getChildren().add(sectionBox));

        providerManager.search(query, 1, screenWidth, 800)
            .thenAccept(images -> {
                Platform.runLater(() -> {
                    contentBox.getChildren().clear();
                    
                    int limit = Math.min(images.size(), 10);
                    for (int i = 0; i < limit; i++) {
                        WallpaperImage img = images.get(i);

                        boolean isFeatured = isTrendingSection && (i == 0);
                        
                        VBox card = createHorizontalCard(img, isFeatured, isTrendingSection);
                        contentBox.getChildren().add(card);
                    }
                });
            });
    }

    private VBox createHorizontalCard(WallpaperImage data, boolean isFeatured, boolean isTrendingSection) {
        VBox card = new VBox(0);
        card.setAlignment(Pos.TOP_LEFT);
        card.setPadding(Insets.EMPTY);
        
        double targetWidth;
        
        if (isFeatured) {
            targetWidth = 400; 
        } else if (isTrendingSection) {
            targetWidth = 330;
        } else {
            targetWidth = 220;
        }

        double targetHeight = 150; // Default fallback
        
        if (data.getWidth() > 0 && data.getHeight() > 0) {
            // Rumus: (Tinggi Asli / Lebar Asli) * Lebar Target
            targetHeight = (data.getHeight() / data.getWidth()) * targetWidth;
            
            // LOGIKA PENTING: Batasi tinggi (Clamping)
            // Maksimal 1.4x lebar (biar ga jadi tiang listrik)
            // Minimal 0.6x lebar (biar ga terlalu gepeng)
            double maxHeight = targetWidth * 1.4; 
            double minHeight = targetWidth * 0.6;
            
            if (targetHeight > maxHeight) targetHeight = maxHeight;
            if (targetHeight < minHeight) targetHeight = minHeight;
        }

        // --- 3. SETUP IMAGEVIEW ---
        ImageView imageView = new ImageView();
        imageView.setFitWidth(targetWidth);
        imageView.setFitHeight(targetHeight); // Paksa tinggi hasil hitungan
        imageView.setPreserveRatio(false);    // Matikan rasio, paksa ikut fitHeight kita
        imageView.setSmooth(true);

 
        Rectangle clip = new Rectangle(targetWidth, targetHeight);
        double radius = isFeatured ? 24 : (isTrendingSection ? 20 : 16);
        clip.setArcWidth(radius);
        clip.setArcHeight(radius);
        imageView.setClip(clip);

        // Load Thumbnail
        loadImageAsync(data.getThumbnailUrl(), imageView);

        // --- 4. LABEL DENGAN PADDING ---
        Label descLabel = new Label(data.getDescription());
        descLabel.setMaxWidth(targetWidth); 
        
        // Tambahkan Padding agar teks tidak mepet pinggir (Sama seperti Masonry)
        descLabel.setPadding(new Insets(8, 12, 12, 12)); 
        
        String fontSize = isFeatured ? "14px" : (isTrendingSection ? "12px" : "11px");
        descLabel.setStyle("-fx-font-size: " + fontSize + "; -fx-text-fill: " + (isDarkMode ? "#dddddd" : "#333333") + ";");
        
        if (isFeatured) descLabel.setStyle(descLabel.getStyle() + "-fx-font-weight: bold;");

        // --- 5. HOVER & CLICK ---
        ScaleTransition st = new ScaleTransition(Duration.millis(150), card);
        card.setOnMouseEntered(e -> {
            st.setToX(1.02); st.setToY(1.02); st.playFromStart(); // Zoom lebih halus (1.02)
            card.setStyle("-fx-cursor: hand;");
        });
        card.setOnMouseExited(e -> {
            st.setToX(1.0); st.setToY(1.0); st.playFromStart();
        });

        card.setOnMouseClicked(e -> {
             card.setOpacity(0.5);
             CompletableFuture.runAsync(() -> {
                 File dl = systemHandler.downloadImage(data.getImageUrl(), data.getId() + ".jpg");
                 if(dl != null) { systemHandler.setWallpaper(dl); Platform.runLater(() -> card.setOpacity(1.0)); }
             });
        });

        card.getChildren().addAll(imageView, descLabel);
        
        // PENTING: Style background agar tidak transparan
        styleCardVBox(card); 

        // Override Shadow untuk Featured
        if (isFeatured) {
            card.setEffect(new DropShadow(20, Color.web("#0078d744")));
        }
        
        return card;
    }

    // search suggestion
    private void setupSearchSuggestions() {
        suggestionsPopup = new ContextMenu();

        suggestionsPopup.setStyle(
                "-fx-background-color: " + (isDarkMode ? "#1e1e1e" : "#ffffff") + ";" +
                "-fx-text-fill: " + (isDarkMode ? "white" : "black") + ";" +
                "-fx-background-radius: 0 0 8 8;" +
                "-fx-border-color: " + (isDarkMode ? "#333333" : "#cccccc") + ";" +
                "-fx-border-width: 0 1 1 1;" +
                "-fx-border-radius: 0 0 8 8;" +
                "-fx-width: 500px;"
            );

        // Listener saat mengetik
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.trim().length() < 2) {
                suggestionsPopup.hide();
                return;
            }

            suggestionService.getRecommendations(newVal)
                .thenAccept(suggestions -> {
                    Platform.runLater(() -> {
                        if (suggestions.isEmpty()) {
                            suggestionsPopup.hide();
                        } else {
                            populateSuggestions(suggestions);

                            double currentWidth = searchField.getWidth();
                            
                            suggestionsPopup.setMinWidth(currentWidth);
                            suggestionsPopup.setPrefWidth(currentWidth);
                            suggestionsPopup.setMaxWidth(currentWidth);
                            if (!suggestionsPopup.isShowing()) {
                                suggestionsPopup.show(searchField, javafx.geometry.Side.BOTTOM, 0, 0);
                            }
                        }
                    });
                });
        });

        searchField.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (suggestionsPopup.isShowing()) {
                suggestionsPopup.setMinWidth(newVal.doubleValue());
                suggestionsPopup.setPrefWidth(newVal.doubleValue());
                suggestionsPopup.setMaxWidth(newVal.doubleValue());
            }
        });
    }

    private void populateSuggestions(List<String> suggestions) {
        suggestionsPopup.getItems().clear();

        suggestionsPopup.getItems().add(new SeparatorMenuItem());

        for (String suggestion : suggestions) {
            MenuItem item = new MenuItem(suggestion);

            String textColor = isDarkMode ? "white" : "black";
            item.setStyle("-fx-text-fill: " + textColor + "; -fx-padding: 5 10;");

            item.setOnAction(e -> {
                searchField.setText(suggestion);
                suggestionsPopup.hide();
                onSearchAction();
            });
            
            suggestionsPopup.getItems().add(item);
        }
    }

    // pagination load next page
    private void loadNextPage() {
        isLoading = true;
        currentPage++;
        System.out.println("Loading page: " + currentPage);

        // 1. Tampilkan Skeleton di bawah
        showSkeletons();

        // 2. Fetch Data
        providerManager.search(currentQuery, currentPage, screenWidth, screenHeight)
            .thenAccept(images -> {
                Platform.runLater(() -> {
                    // 3. Hapus Skeleton
                    removeSkeletons();
                    
                    if (images.isEmpty()) {
                        // Stop infinite scroll jika data habis
                        // Opsional: Tampilkan label "End of results"
                    } else {
                        // 4. Append gambar baru ke Masonry
                        for (WallpaperImage img : images) {
                            VBox card = createMasonryCard(img);
                            addCardToMasonry(card, masonryColumns, columnHeights);
                        }
                    }
                    isLoading = false;
                });
            })
            .exceptionally(ex -> {
                Platform.runLater(() -> {
                    removeSkeletons();
                    isLoading = false;
                });
                return null;
            });
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
        if (suggestionsPopup != null && suggestionsPopup.isShowing()) {
            suggestionsPopup.hide();
        }

        String query = searchField.getText();
        if (query.isEmpty() || providerManager == null) return;

        if (query.isEmpty()) {
            toggleViewMode(false);
            return;
        }

        suggestionService.logSearch(query);

        mainTabPane.getSelectionModel().select(0);

        toggleViewMode(true);

        if (welcomeLabel != null) {
            welcomeLabel.setVisible(false);
            welcomeLabel.setManaged(false);
        }
        scrollPane.setVisible(true);

        currentQuery = query;
        currentPage = 1;
        isLoading = true;

        // Clear Online Columns
        for (VBox col : masonryColumns) col.getChildren().clear();
        columnHeights.replaceAll((k, v) -> 0.0);

        showSkeletons();

        providerManager.search(query, currentPage, screenWidth, screenHeight)
                .thenAccept(images -> {
                Platform.runLater(() -> {
                    removeSkeletons();
                    if (images.isEmpty()) {
                        // Tampilkan pesan "Tidak ditemukan"
                    } else {
                        for (WallpaperImage img : images) {
                            VBox card = createMasonryCard(img);
                            addCardToMasonry(card, masonryColumns, columnHeights);
                        }
                    }
                    isLoading = false;
                });
            })
                .exceptionally(ex -> { 
                    Platform.runLater(() -> {
                        removeSkeletons();
                        isLoading = false;
                    });
                    logger.error("Search failed", ex); return null; });
    }

    private void showSkeletons() {
        // Tampilkan 10 skeleton card dummy
        for (int i = 0; i < 10; i++) {
            VBox skeleton = createSkeletonCard();
            addCardToMasonry(skeleton, masonryColumns, columnHeights);
            activeSkeletons.add(skeleton); // Simpan referensi biar bisa dihapus
        }
    }

    private void removeSkeletons() {
        // Hapus semua skeleton dari kolomnya masing-masing
        for (Node node : activeSkeletons) {
            if (node.getParent() instanceof VBox) {
                VBox parentCol = (VBox) node.getParent();
                parentCol.getChildren().remove(node);
                
                // Kurangi tinggi kolom agar kalkulasi masonry berikutnya akurat
                if (columnHeights.containsKey(parentCol)) {
                    double currentH = columnHeights.get(parentCol);
                    // Kurangi estimasi tinggi skeleton (rata-rata 200 + 12 margin)
                    columnHeights.put(parentCol, Math.max(0, currentH - 212)); 
                }
            }
        }
        activeSkeletons.clear();
    }

    private VBox createSkeletonCard() {
        VBox card = new VBox(8);
        card.setPadding(Insets.EMPTY);
        
        double colWidth = getCurrentColWidth();
        
        // 1. Kotak Abu-abu (Pengganti Gambar)
        // Tinggi acak antara 150 - 300 biar mirip masonry asli
        double randomHeight = 150 + Math.random() * 150; 
        
        Region box = new Region();
        box.setPrefSize(colWidth, randomHeight);
        
        // Warna Skeleton (Sesuaikan tema)
        String color = isDarkMode ? "#333333" : "#e0e0e0";
        box.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 12;");

        // 2. Baris Teks Dummy
        Region textBar = new Region();
        textBar.setPrefSize(colWidth * 0.6, 12); // Lebar 60%
        textBar.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 4;");
        
        // 3. Animasi Denyut (Breathing Effect)
        javafx.animation.FadeTransition fade = new javafx.animation.FadeTransition(Duration.millis(800), card);
        fade.setFromValue(0.5);
        fade.setToValue(1.0);
        fade.setCycleCount(javafx.animation.Animation.INDEFINITE);
        fade.setAutoReverse(true);
        fade.play();

        card.getChildren().addAll(box, textBar);
        return card;
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

    private VBox createMasonryCard(WallpaperImage data) {
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

        double targetHeight = 200; // Default jika data 0
        if (data.getWidth() > 0 && data.getHeight() > 0) {
            targetHeight = (data.getHeight() / data.getWidth()) * colWidth;
        }

        ImageView imageView = new ImageView();
        imageView.setFitWidth(colWidth);
        imageView.setFitHeight(targetHeight);
        imageView.setPreserveRatio(false); // Default true agar aman
        imageView.setSmooth(true);

        Rectangle clip = new Rectangle(colWidth, targetHeight);
        clip.setArcWidth(24); clip.setArcHeight(24);
        imageView.setClip(clip);

        // Panggil Loader yang sesuai
        if (isLocal) {
            loadLocalImageAsync(data.getThumbnailUrl(), imageView, colWidth);
        } else {
            loadImageAsync(data.getThumbnailUrl(), imageView);
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

    // --- THUMBNAIL LOADERS ---

    // Online Loader
    private void loadImageAsync(String url, ImageView target) {
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("User-Agent", "Mozilla/5.0").GET().build();
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() == 200) {
                    Image image = new Image(response.body());
                    Platform.runLater(() -> target.setImage(image));
                }
            } catch (Exception e) { logger.error("Error loading online thumbnail: ", e); }
        });
    }

    // Loader Local
    private void loadLocalImageAsync(String url, ImageView target, double reqWidth) {
        CompletableFuture.runAsync(() -> {
            try {
                double widthToLoad = (reqWidth > 0) ? reqWidth : 300;
                Image image = new Image(url, widthToLoad, 0, true, false);
                if (image.isError()) return;
                Platform.runLater(() -> target.setImage(image));
            } catch (Exception e) { logger.error("Error loading local thumbnail: ", e); }
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
        // Online Search Cards
        for (VBox col : masonryColumns) {
            for (Node n : col.getChildren()) {
                if (n instanceof VBox) styleCardVBox((VBox) n);
            }
        }
        
        // Local Collection Cards
        for (VBox col : localMasonryColumns) {
            for (Node n : col.getChildren()) {
                if (n instanceof VBox) styleCardVBox((VBox) n);
            }
        }

        // Explore Cards
        if (exploreContainer != null) {
            for (Node section : exploreContainer.getChildren()) {
                if (section instanceof VBox) {
                    VBox sectionBox = (VBox) section;
                    // Struktur: VBox -> [Header(HBox), ScrollPane]
                    if (sectionBox.getChildren().size() > 1 && sectionBox.getChildren().get(1) instanceof ScrollPane) {
                        ScrollPane hScroll = (ScrollPane) sectionBox.getChildren().get(1);
                        if (hScroll.getContent() instanceof HBox) {
                            HBox contentBox = (HBox) hScroll.getContent();
                            // Loop semua kartu horizontal di dalam HBox
                            for (Node card : contentBox.getChildren()) {
                                if (card instanceof VBox) {
                                    styleCardVBox((VBox) card);
                                    
                                    // PENTING: Kembalikan efek shadow khusus jika ini Featured Card
                                    // Cek apakah ukurannya besar (lebar 400)
                                    VBox vCard = (VBox) card;
                                    if (!vCard.getChildren().isEmpty() && vCard.getChildren().get(0) instanceof ImageView) {
                                        ImageView img = (ImageView) vCard.getChildren().get(0);
                                        if (img.getFitWidth() > 300) { // Asumsi featured width 400
                                            vCard.setEffect(new DropShadow(20, Color.web("#0078d744")));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void applyCurrentTheme() {
        // Tentukan Palette Warna
        String bgRoot = isDarkMode ? "#0e0e0eff" : "#f4f4f4"; 
        String bgBar  = isDarkMode ? "#121212" : "#ffffff"; 
        String bgInput= isDarkMode ? "#1e1e1e" : "#eeeeee"; 
        String textCol= isDarkMode ? "white"   : "black";
        String subText= isDarkMode ? "#aaaaaa" : "#555555";
        String accent = "#0c6370"; 

        // --- WARNA SCROLLBAR ---
        String scrollColor = isDarkMode ? "#333333" : "#C0C0C0";

        String tabText     = isDarkMode ? "#828282ff" : "#777777"; 
        
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

        if (suggestionsPopup != null) {
            suggestionsPopup.setStyle(
                "-fx-background-color: " + (isDarkMode ? "#1e1e1e" : "#ffffff") + ";" +
                "-fx-text-fill: " + (isDarkMode ? "white" : "black") + ";" +
                "-fx-background-radius: 0 0 8 8;" +
                "-fx-border-color: " + (isDarkMode ? "#333333" : "#cccccc") + ";" +
                "-fx-border-width: 0 1 1 1;" +
                "-fx-border-radius: 0 0 8 8;" +
                "-fx-width: 500px;"
            );
        }

        if (exploreContainer != null) {
            String titleColor = isDarkMode ? "white" : "#333333";
            for (Node section : exploreContainer.getChildren()) {
                if (section instanceof VBox) {
                    VBox box = (VBox) section;
                    if (!box.getChildren().isEmpty() && box.getChildren().get(0) instanceof HBox) {
                        HBox header = (HBox) box.getChildren().get(0);
                        if (!header.getChildren().isEmpty() && header.getChildren().get(0) instanceof Label) {
                            ((Label) header.getChildren().get(0)).setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + titleColor + ";");
                        }
                    }
                }
            }
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

    private boolean isWindowsDarkMode() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (!os.contains("win")) {
                return true; 
            }

            ProcessBuilder pb = new ProcessBuilder(
                "reg", "query", 
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", 
                "/v", "AppsUseLightTheme"
            );
            
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            
            return output.contains("0x0");

        } catch (Exception e) {
            return true; 
        }
    }

    private void setupFocusTraversal() {
        for (Node node : topBar.getChildren()) {
            node.setFocusTraversable(false);
        }

        mainTabPane.setFocusTraversable(false);
        scrollPane.setFocusTraversable(false);
        if (localScrollPane != null) localScrollPane.setFocusTraversable(false);

        if (btnMin != null) btnMin.setFocusTraversable(false);
        if (btnMax != null) btnMax.setFocusTraversable(false);
        if (btnClose != null) btnClose.setFocusTraversable(false);
        if (themeBtn != null) themeBtn.setFocusTraversable(false);
        if (providerCombo != null) providerCombo.setFocusTraversable(false);

        searchField.setFocusTraversable(true);
        
        Platform.runLater(() -> searchField.requestFocus());
    }
}