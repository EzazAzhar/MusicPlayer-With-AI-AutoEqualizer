import javafx.animation.*; // Import animation utilities (not used in snippet yet)
import javafx.application.Application; // Base class for JavaFX applications
import javafx.application.Platform;
import javafx.collections.FXCollections; // Utilities for observable collections
import javafx.collections.ObservableList; // ObservableList interface for list data binding
import javafx.geometry.Insets; // For padding/margin settings
import javafx.geometry.Orientation; // To specify horizontal/vertical orientation
import javafx.geometry.Pos; // Positioning enums like CENTER, LEFT, RIGHT
import javafx.scene.Scene; // JavaFX scene container
import javafx.scene.control.*; // All JavaFX UI controls (buttons, labels, sliders, etc.)
import javafx.scene.image.Image; // For loading images
import javafx.scene.image.ImageView; // For displaying images
import javafx.scene.input.MouseButton; // Mouse button types for mouse events
import javafx.scene.layout.*; // Layout panes like VBox, HBox, BorderPane, etc.
import javafx.scene.media.*; // Media and media player for audio playback
import javafx.scene.paint.Color; // Colors for styling
import javafx.scene.text.Font; // Font settings
import javafx.scene.text.FontWeight; // Font weight constants
import javafx.stage.*; // Stage and dialog windows
import javafx.util.Duration; // Time durations for animations and media

import java.io.BufferedReader;
import java.io.File; // File handling
import java.io.InputStreamReader;
import java.util.*; // Utilities like ArrayList, LinkedList, Map, HashMap
import java.io.File;
import java.sql.*;

public class MusicPlayerApp extends Application {
    private ListView<String> playlistView; // UI list displaying song names in the playlist
    private ArrayList<File> songFiles = new ArrayList<>(); // List storing File objects for songs
    private LinkedList<File> playQueue = new LinkedList<>(); // Queue to manage next songs to play
    private MediaPlayer mediaPlayer; // JavaFX media player object to play audio
    private Label nowPlaying; // Label to show current song info
    private Slider progressSlider; // Slider showing playback progress and seeking
    private Label timeLabel; // Label showing elapsed and total time of current song
    private ArrayList<Slider> eqSliders = new ArrayList<>(); // Sliders for equalizer bands
    private final double[] centerFrequencies = { 60, 230, 910, 3600, 14000 }; // Frequencies for EQ bands (Hz)
    private final EqualizerBand[] bands = new EqualizerBand[5]; // EqualizerBand objects for audio equalizer
    private ImageView playIcon, pauseIcon, nextIcon, prevIcon; // Icons for play, pause, next, previous controls

    private TreeView<String> libraryTreeView; // TreeView UI to show music library folder structure
    private Map<TreeItem<String>, File> treeItemFileMap = new HashMap<>(); // Map linking tree items to actual Files

    private Button nextBtn, prevBtn; // Buttons to skip to next or previous song
    private int currentIndex = -1; // Index of currently playing song in the playlist (-1 means none selected)
    private Connection dbConnection;

    private void connectToDatabase() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            String url = "jdbc:mysql://mysql-335b7a0f-ezaz-7c99.b.aivencloud.com:13576/defaultdb?ssl-mode=REQUIRED&useSSL=true&requireSSL=true&verifyServerCertificate=false";
            dbConnection = DriverManager.getConnection(url, "avnadmin", "YOUR_AIVEN_PASSWORD_HERE");
            System.out.println("Connected to Aiven MySQL database");
            ensureTableExists();
        } catch (Exception e) {
            System.out.println("Database connection failed");
            e.printStackTrace();
        }
    }

    private void ensureTableExists() {
        String sql = "CREATE TABLE IF NOT EXISTS songs (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "title VARCHAR(255) NOT NULL, " +
                "path TEXT NOT NULL, " +
                "artist VARCHAR(255), " +
                "album VARCHAR(255)" +
                ")";
        try (Statement stmt = dbConnection.createStatement()) {
            stmt.execute(sql);
            System.out.println("Songs table verified/created.");
        } catch (SQLException e) {
            System.out.println("Failed to create table: " + e.getMessage());
        }
    }

    private double[] predictEQFromPython(File songFile) {
        double[] eqValues = new double[5];

        try {
            // Full paths to Python executable and your predict_eq.py script
            String pythonExe = "C:\\Users\\ezaza\\AppData\\Local\\Programs\\Python\\Python312\\python.exe";
            String scriptPath = "C:\\Users\\ezaza\\Downloads\\Project\\Project\\SoundNest\\pythonmodels\\predict_eq.py";

            // Run Python script with song file path as argument
            Process process = Runtime.getRuntime().exec(new String[] {
                    pythonExe,
                    scriptPath,
                    songFile.getAbsolutePath()
            });

            // Read Python script output (expecting comma-separated EQ values)
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    String[] parts = line.trim().split(",");
                    for (int i = 0; i < parts.length && i < 5; i++) {
                        eqValues[i] = Double.parseDouble(parts[i]);
                    }
                }
            }

            process.waitFor();
            reader.close();

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Failed to predict EQ from Python");
        }

        return eqValues;
    }

    public static void main(String[] args) {
        launch(args); // Launch the JavaFX application
    }

    @Override
    public void start(Stage primaryStage) {
        playlistView = new ListView<>();
        connectToDatabase();
        loadSongsFromDatabase(); // Initialize DB connection

        // Initialize the playlist ListView UI component

        // Apply custom CSS styles for background and text colors of the playlist
        playlistView.setStyle(
                "-fx-background-color: #1e1e2f; -fx-control-inner-background: #2b2b3d; -fx-text-fill: #eeeeee;");
        playlistView.setPrefWidth(280); // Set preferred width for the playlist view
        playlistView.setPlaceholder(new Label("No songs added")); // Placeholder text when no songs are present

        // Customize each cell in the ListView to show song name and enable right-click
        // context menu
        playlistView.setCellFactory(lv -> {
            ListCell<String> cell = new ListCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item); // Display the song name unless the cell is empty
                }
            };

            // Create a context menu to remove a song from the playlist when right-clicked
            ContextMenu contextMenu = new ContextMenu();
            MenuItem removeItem = new MenuItem("Remove from Player");
            removeItem.setOnAction(e -> {
                int index = cell.getIndex(); // Get the index of the clicked cell
                if (index >= 0 && index < songFiles.size()) {
                    // Remove the song from the internal song list and the ListView
                    songFiles.remove(index);
                    playlistView.getItems().remove(index);

                    // Update current playing index if the removed song was before or at current
                    if (currentIndex >= index) {
                        currentIndex--;
                    }
                }
            });
            contextMenu.getItems().add(removeItem); // Add remove option to context menu

            cell.setContextMenu(contextMenu); // Attach context menu to the cell

            return cell; // Return customized cell to be used by ListView
        });

        // Create the main header label for the app with a music note emoji and custom
        // font
        Label header = new Label("SoundNest Player");
        header.setFont(Font.font("Segoe UI", FontWeight.BOLD, 24)); // Set bold large font
        header.setPadding(new Insets(10, 0, 20, 10)); // Add padding around the header
        animateSmoothRGB(header); // Apply animated RGB effect to the header

        // Label to display the currently playing song
        nowPlaying = new Label("Now Playing: None");
        nowPlaying.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 14));
        nowPlaying.setStyle("-fx-text-fill: #c0c0c0;"); // Light gray text color

        // Label to show current playback time and total duration
        timeLabel = new Label("0:00 / 0:00");
        timeLabel.setFont(Font.font("Segoe UI", 12));
        timeLabel.setStyle("-fx-text-fill: #a0a0a0;"); // Slightly dimmed text

        // Slider to show and allow seeking in the current song
        progressSlider = new Slider(0, 100, 0); // Min 0, Max 100, Initial 0
        progressSlider.setPrefWidth(280); // Set preferred width
        progressSlider.setStyle("-fx-control-inner-background: #44475a;"); // Custom color
        progressSlider.setOnMouseReleased(e -> {
            if (mediaPlayer != null) {
                // When user releases the slider, seek the media player to the corresponding
                // position
                Duration seekTo = mediaPlayer.getTotalDuration().multiply(progressSlider.getValue() / 100.0);
                mediaPlayer.seek(seekTo);
            }
        });

        // Load icons for media controls (images must exist in correct paths)
        playIcon = new ImageView(new Image("play.png"));
        pauseIcon = new ImageView(new Image("pause.png"));
        ImageView stopIcon = new ImageView(new Image("stop.png"));
        nextIcon = new ImageView(new Image("file:next.png"));
        prevIcon = new ImageView(new Image("file:prev.png"));

        // Apply consistent sizing and effects to each control icon
        configureIcon(playIcon);
        configureIcon(pauseIcon);
        configureIcon(stopIcon);
        configureIcon(nextIcon);
        configureIcon(prevIcon);

        // Create buttons for player controls using the icons
        Button addSongBtn = new Button("Add Song"); // Button to open file chooser and add songs
        Button playPauseBtn = new Button("", playIcon); // Button to toggle play/pause
        Button stopBtn = new Button("", stopIcon); // Button to stop playback
        nextBtn = new Button("", nextIcon); // Button to go to next song
        prevBtn = new Button("", prevIcon); // Button to go to previous song

        // Style the control buttons for uniform appearance
        styleIconButton(playPauseBtn);
        styleIconButton(stopBtn);
        styleIconButton(nextBtn);
        styleIconButton(prevBtn);

        // Style the "Add Song" button with green color and rounded corners
        addSongBtn.setStyle(
                "-fx-background-color: #50fa7b; -fx-text-fill: #282a36; -fx-font-weight: bold; -fx-background-radius: 8;");

        // Change button color slightly when hovered for a smooth effect
        addSongBtn.setOnMouseEntered(e -> addSongBtn.setStyle(
                "-fx-background-color: #4cd964; -fx-text-fill: #282a36; -fx-font-weight: bold; -fx-background-radius: 8;"));
        addSongBtn.setOnMouseExited(e -> addSongBtn.setStyle(
                "-fx-background-color: #50fa7b; -fx-text-fill: #282a36; -fx-font-weight: bold; -fx-background-radius: 8;"));

        // Play/Pause button logic
        playPauseBtn.setOnAction(e -> {
            if (mediaPlayer == null) {
                // If no song is loaded yet, play the selected one
                playSelectedSong();
                playPauseBtn.setGraphic(pauseIcon); // Switch icon to pause
            } else {
                MediaPlayer.Status status = mediaPlayer.getStatus();
                if (status == MediaPlayer.Status.PLAYING) {
                    mediaPlayer.pause(); // Pause if currently playing
                    playPauseBtn.setGraphic(playIcon); // Show play icon
                } else {
                    mediaPlayer.play(); // Resume playback
                    playPauseBtn.setGraphic(pauseIcon); // Show pause icon
                }
            }
        });

        // Stop button logic
        stopBtn.setOnAction(e -> {
            stopPlayback(); // Call method to stop media playback and reset UI
            playPauseBtn.setGraphic(playIcon); // Set play icon
        });

        // Next button logic
        nextBtn.setOnAction(e -> {
            if (currentIndex + 1 < songFiles.size()) {
                currentIndex++; // Move to the next song index
                playlistView.getSelectionModel().select(currentIndex); // Highlight it in the list
                playSelectedSong(); // Play the next song
            }
        });

        // Previous button logic
        prevBtn.setOnAction(e -> {
            if (currentIndex > 0) {
                currentIndex--; // Go to previous song
                playlistView.getSelectionModel().select(currentIndex); // Highlight it
                playSelectedSong(); // Play the selected song
            }
        });

        // Double-click on playlist item to play it
        playlistView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                playSelectedSong(); // Play the song that was double-clicked
                playPauseBtn.setGraphic(pauseIcon); // Change icon to pause
            }
        });

        // Add Song button logic to open file chooser and add MP3s
        addSongBtn.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select MP3 Song(s)");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("MP3 Files", "*.mp3")); // Allow only
                                                                                                          // MP3s
            // Open file dialog allowing multiple selections
            List<File> selectedFiles = fileChooser.showOpenMultipleDialog(primaryStage);

            if (selectedFiles != null) {
                System.out.println("User selected " + selectedFiles.size() + " files.");
                for (File file : selectedFiles) {
                    if (!songFiles.contains(file)) {
                        songFiles.add(file);
                        playlistView.getItems().add(file.getName());

                        // Insert into database
                        System.out.println("Pushing to Aiven: " + file.getName());
                        insertSongIntoDatabase(file.getName(), file.getAbsolutePath(), "Unknown Artist",
                                "Unknown Album");
                    } else {
                        System.out.println("Song already in playlist: " + file.getName());
                    }
                }
            } else {
                System.out.println("No files selected.");
            }
        });

        // Equalizer Setup
        HBox equalizerBox = new HBox(15); // Horizontal box to hold each equalizer band control
        equalizerBox.setPadding(new Insets(10)); // Padding around the equalizer box
        equalizerBox.setStyle("-fx-background-color: #282a36; -fx-background-radius: 10;"); // Background styling
        equalizerBox.setAlignment(Pos.CENTER); // Center align sliders horizontally

        // Labels for each frequency band
        String[] bandLabels = {
                "Bass\n(60 Hz)",
                "Low Mid\n(230 Hz)",
                "Midrange\n(910 Hz)",
                "Presence\n(3.6 kHz)",
                "Treble\n(14 kHz)"
        };

        // Loop to create sliders for each equalizer band
        for (int i = 0; i < centerFrequencies.length; i++) {
            VBox bandBox = new VBox(8); // Vertical box to hold label and slider
            bandBox.setAlignment(Pos.CENTER); // Center align contents vertically

            Label label = new Label(bandLabels[i]); // Frequency label
            label.setFont(Font.font("Segoe UI", 12));
            label.setStyle("-fx-text-fill: #f8f8f2;");

            Slider slider = new Slider(-24.0, 12.0, 0.0); // Equalizer slider range: -24dB to +12dB
            slider.setOrientation(Orientation.VERTICAL); // Vertical slider
            slider.setPrefHeight(120); // Set height of slider
            slider.setShowTickLabels(true); // Show value labels
            slider.setShowTickMarks(true); // Show tick marks
            slider.setMajorTickUnit(12); // Gap between major ticks
            slider.setStyle("-fx-control-inner-background: #44475a; -fx-tick-label-fill: #f8f8f2;");

            eqSliders.add(slider); // Add to equalizer slider list for later control
            bandBox.getChildren().addAll(label, slider); // Add label and slider to VBox
            equalizerBox.getChildren().add(bandBox); // Add VBox to the HBox
        }

        // Library TreeView Setup
        libraryTreeView = new TreeView<>(); // Tree view for browsing folders/files
        libraryTreeView.setStyle("-fx-background-color: #1e1e2f; -fx-text-fill: #eeeeee;");
        libraryTreeView.setShowRoot(false); // Hide root node
        libraryTreeView.setPrefWidth(280); // Set fixed width

        // Customize cells in TreeView to allow right-click menu for mp3 files
        libraryTreeView.setCellFactory(tv -> {
            TreeCell<String> cell = new TreeCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null); // Clear cell if empty
                        setContextMenu(null);
                    } else {
                        setText(item); // Set item name
                        File file = treeItemFileMap.get(getTreeItem());

                        // Check if it's a valid MP3 file
                        if (file != null && file.isFile() && file.getName().toLowerCase().endsWith(".mp3")) {
                            MenuItem addToQueueItem = new MenuItem("Add to Queue"); // Add to queue option
                            addToQueueItem.setOnAction(e -> addFileToQueue(file));
                            setContextMenu(new ContextMenu(addToQueueItem)); // Set right-click context menu
                        } else {
                            setContextMenu(null); // No menu for folders or non-MP3s
                        }
                    }
                }
            };

            // Double-click handler to add and play selected MP3
            cell.setOnMouseClicked(event -> {
                if (!cell.isEmpty() && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    File file = treeItemFileMap.get(cell.getTreeItem());
                    if (file != null && file.isFile() && file.getName().toLowerCase().endsWith(".mp3")) {
                        addFileToPlaylistAndPlay(file); // Add to playlist and start playing
                    }
                }
            });

            return cell;
        });

        Button loadFolderBtn = new Button("Add Music Folder");
        loadFolderBtn.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser(); // Allows user to choose a directory
            chooser.setTitle("Select Music Folder");
            File folder = chooser.showDialog(primaryStage); // Open folder selection dialog
            if (folder != null && folder.isDirectory()) {
                addFolderToLibrary(folder); // Custom method to load folder into library view
            }
        });

        // VBox layout for the Library tab with the "Add Music Folder" button and
        // TreeView
        VBox libraryContent = new VBox(10, loadFolderBtn, libraryTreeView);
        libraryContent.setPadding(new Insets(10));
        libraryContent.setStyle("-fx-background-color: #1e1e2f;");
        Tab libraryTab = new Tab("Library", libraryContent); // Tab with folder tree

        // VBox layout for Playlist tab containing the playlist view
        VBox playlistTabContent = new VBox(10, playlistView);
        playlistTabContent.setPadding(new Insets(10));
        playlistTabContent.setStyle("-fx-background-color: #1e1e2f;");
        Tab playlistTab = new Tab("Player", playlistTabContent); // Tab for player queue

        // VBox layout for Equalizer tab containing equalizer sliders
        VBox equalizerTabContent = new VBox(10, equalizerBox);
        equalizerTabContent.setPadding(new Insets(10));
        equalizerTabContent.setStyle("-fx-background-color: #1e1e2f;");
        Tab equalizerTab = new Tab("Equalizer", equalizerTabContent); // Tab for EQ controls

        // TabPane containing all three tabs (Player, Equalizer, Library)
        TabPane tabPane = new TabPane(playlistTab, equalizerTab, libraryTab);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE); // Disable tab close buttons
        tabPane.setStyle("-fx-background-color: #282a36;");

        // Playback control bar with buttons: Add Song, Prev, Play/Pause, Next, Stop
        HBox controlBar = new HBox(15, addSongBtn, prevBtn, playPauseBtn, nextBtn, stopBtn);
        controlBar.setPadding(new Insets(15, 0, 15, 0));
        controlBar.setAlignment(Pos.CENTER);

        // Bottom section includes Now Playing label, progress bar, time label, and
        // control bar
        VBox bottomBox = new VBox(6, nowPlaying, progressSlider, timeLabel, controlBar);
        bottomBox.setPadding(new Insets(10));
        bottomBox.setStyle("-fx-background-color: #21222c; -fx-background-radius: 0 0 12 12;");

        // Main layout using BorderPane: header (top), tabs (center), controls (bottom)
        BorderPane root = new BorderPane();
        root.setTop(header); // RGB animated app title
        root.setCenter(tabPane); // Tabs: Player, EQ, Library
        root.setBottom(bottomBox); // Playback controls and info
        root.setPadding(new Insets(10));
        root.setStyle("-fx-background-color: linear-gradient(to right, #282a36, #44475a);");

        // Main scene and stage setup
        Scene scene = new Scene(root, 720, 470);
        primaryStage.setTitle("SoundNest Music Player");
        primaryStage.setScene(scene);
        primaryStage.getIcons().add(new Image("file:play.png")); // Set app icon
        primaryStage.show();

        // Run one-time migration from local to Aiven on startup
        new Thread(this::migrateLocalDataToAiven).start();
    }

    // Method to smoothly animate RGB color transitions on the header label
    private void animateSmoothRGB(Label label) {
        Timeline hueTimeline = new Timeline(); // Timeline for RGB hue animation
        hueTimeline.setCycleCount(Animation.INDEFINITE); // Repeat indefinitely

        // Generate keyframes to change hue every few milliseconds
        for (int i = 0; i <= 360; i += 3) {
            final int hue = i;
            hueTimeline.getKeyFrames().add(new KeyFrame(
                    Duration.millis(i * 20),
                    e -> label.setTextFill(Color.hsb(hue, 1.0, 1.0)) // Set label color with HSB model
            ));
        }

        hueTimeline.play(); // Start animation
    }

    private void playSelectedSong() {
        int selectedIndex = playlistView.getSelectionModel().getSelectedIndex();
        if (selectedIndex >= 0) {
            currentIndex = selectedIndex;

            if (mediaPlayer != null)
                mediaPlayer.stop();

            Media media = new Media(songFiles.get(selectedIndex).toURI().toString());
            mediaPlayer = new MediaPlayer(media);

            mediaPlayer.getAudioEqualizer().getBands().clear();

            for (int i = 0; i < centerFrequencies.length; i++) {
                bands[i] = new EqualizerBand(centerFrequencies[i], 100, eqSliders.get(i).getValue());
                mediaPlayer.getAudioEqualizer().getBands().add(bands[i]);

                final int idx = i;
                eqSliders.get(i).valueProperty().addListener((obs, oldVal, newVal) -> {
                    if (bands[idx] != null)
                        bands[idx].setGain(newVal.doubleValue());
                });
            }

            // Show message while predicting
            nowPlaying.setText("Now Playing: " + songFiles.get(selectedIndex).getName() + " (Predicting EQ...)");

            // Update EQ sliders based on Python prediction in a background thread
            new Thread(() -> {
                double[] eqValues = predictEQFromPython(songFiles.get(currentIndex));

                // Update sliders and EQ bands safely on JavaFX thread
                Platform.runLater(() -> {
                    for (int i = 0; i < eqSliders.size(); i++) {
                        double val = eqValues[i];
                        // Clamp value to slider min/max
                        val = Math.max(eqSliders.get(i).getMin(), Math.min(eqSliders.get(i).getMax(), val));
                        eqSliders.get(i).setValue(val); // Move slider
                        bands[i].setGain(val); // Update actual EQ band
                    }
                    // Remove the "Predicting EQ..." message
                    nowPlaying.setText("Now Playing: " + songFiles.get(selectedIndex).getName());
                });
            }).start();

            // **Run Python EQ prediction in background**
            new Thread(() -> {
                double[] eqValues = predictEQFromPython(songFiles.get(currentIndex));
                Platform.runLater(() -> { // Update sliders on FX thread
                    for (int i = 0; i < eqSliders.size(); i++) {
                        double val = eqValues[i];
                        // Clamp value to slider range
                        val = Math.max(eqSliders.get(i).getMin(), Math.min(eqSliders.get(i).getMax(), val));
                        eqSliders.get(i).setValue(val);
                        bands[i].setGain(val);
                    }
                });
            }).start();

            mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                Duration total = mediaPlayer.getTotalDuration();
                if (total != null && !total.isUnknown()) {
                    double progress = newTime.toMillis() / total.toMillis() * 100;
                    progressSlider.setValue(progress);
                    timeLabel.setText(formatDuration(newTime) + " / " + formatDuration(total));
                }
            });

            mediaPlayer.setOnEndOfMedia(() -> {
                if (!playQueue.isEmpty())
                    playNextFromQueue();
                else if (currentIndex + 1 < songFiles.size()) {
                    currentIndex++;
                    playlistView.getSelectionModel().select(currentIndex);
                    playSelectedSong();
                } else
                    stopPlayback();
            });

            mediaPlayer.play();
            nowPlaying.setText("Now Playing: " + songFiles.get(selectedIndex).getName());
        }
    }

    private void stopPlayback() {
        if (mediaPlayer != null)
            mediaPlayer.stop(); // Stop current song
        mediaPlayer = null;
        nowPlaying.setText("Now Playing: None"); // Reset label
        progressSlider.setValue(0); // Reset progress bar
        timeLabel.setText("0:00 / 0:00"); // Reset time label
    }

    private void addFileToPlaylistAndPlay(File file) {
        // Add file to playlist if not already present
        if (!songFiles.contains(file)) {
            songFiles.add(file);
            playlistView.getItems().add(file.getName());
        }

        // Select and play the song
        playlistView.getSelectionModel().select(songFiles.indexOf(file));
        playSelectedSong();
    }

    private void addFileToQueue(File file) {
        playQueue.add(file); // Add song to queue

        // Show confirmation alert to user
        Alert alert = new Alert(Alert.AlertType.INFORMATION, "Added to queue: " + file.getName());
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void playNextFromQueue() {
        if (playQueue.isEmpty()) {
            stopPlayback(); // If nothing is in the queue, stop playback
            return;
        }

        File nextFile = playQueue.poll(); // Get and remove the next song from the queue
        addFileToPlaylistAndPlay(nextFile); // Add it to the playlist and start playing
    }

    // Class-level field to prevent duplicate folder additions
    private Set<String> libraryFolderNames = new HashSet<>();

    private void addFolderToLibrary(File folder) {
        if (libraryFolderNames.contains(folder.getName())) {
            // Folder has already been added; ignore or notify user
            System.out.println("Folder already added: " + folder.getName());
            return;
        }

        // Mark folder as added
        libraryFolderNames.add(folder.getName());

        // Create a root tree item for the folder
        TreeItem<String> rootItem = new TreeItem<>(folder.getName());
        treeItemFileMap.put(rootItem, folder);

        // Recursively build tree structure
        buildFileTree(rootItem, folder);

        // If TreeView root doesn't exist, create a dummy root
        if (libraryTreeView.getRoot() == null) {
            TreeItem<String> fakeRoot = new TreeItem<>("root");
            fakeRoot.setExpanded(true);
            libraryTreeView.setRoot(fakeRoot);
        }

        // Add folder to TreeView
        libraryTreeView.getRoot().getChildren().add(rootItem);
        rootItem.setExpanded(true); // Expand folder by default
    }

    private void buildFileTree(TreeItem<String> parentItem, File folder) {
        File[] files = folder.listFiles(); // List all files in the folder
        if (files == null)
            return;

        Arrays.sort(files); // Optional: sort for consistent display order

        for (File f : files) {
            TreeItem<String> item = new TreeItem<>(f.getName());
            treeItemFileMap.put(item, f);
            parentItem.getChildren().add(item);

            // Recursively process subdirectories
            if (f.isDirectory())
                buildFileTree(item, f);
        }
    }

    private String formatDuration(Duration duration) {
        int seconds = (int) Math.floor(duration.toSeconds());
        return String.format("%d:%02d", seconds / 60, seconds % 60); // e.g., 3:05
    }

    private void configureIcon(ImageView icon) {
        // Resize icons for consistent button sizing
        icon.setFitWidth(25);
        icon.setFitHeight(25);
    }

    private void styleIconButton(Button btn) {
        // Style the button with transparent background and hover effects
        btn.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: #44475a; -fx-cursor: hand;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: transparent; -fx-cursor: hand;"));
    }

    private void insertSongIntoDatabase(String title, String path, String artist, String album) {
        try {
            String query = "INSERT INTO songs (title, path, artist, album) VALUES (?, ?, ?, ?)";
            PreparedStatement stmt = dbConnection.prepareStatement(query);
            stmt.setString(1, title);
            stmt.setString(2, path);
            stmt.setString(3, artist);
            stmt.setString(4, album);
            stmt.executeUpdate();
            stmt.close();
            System.out.println("Inserted: " + title);
        } catch (SQLException e) {
            e.printStackTrace();
            showError("Database Insert Failed", e.getMessage());
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void loadSongsFromDatabase() {
        System.out.println("Loading songs from Aiven database...");
        String sql = "SELECT title, path FROM songs";
        int loadedCount = 0;
        int missingCount = 0;
        try (Statement stmt = dbConnection.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String title = rs.getString("title");
                String path = rs.getString("path");

                File file = new File(path);
                if (file.exists()) {
                    boolean alreadyExists = false;
                    for (File f : songFiles) {
                        if (f.getAbsolutePath().equals(file.getAbsolutePath())) {
                            alreadyExists = true;
                            break;
                        }
                    }
                    if (!alreadyExists) {
                        songFiles.add(file);
                        playlistView.getItems().add(title);
                        loadedCount++;
                    }
                } else {
                    missingCount++;
                    System.out.println("File not found locally (skipped): " + path);
                }
            }

            System.out.println("Database load complete. Songs loaded: " + loadedCount
                    + (missingCount > 0 ? " (Missing: " + missingCount + ")" : ""));

        } catch (SQLException e) {
            System.out.println("Failed to load songs from DB:");
            e.printStackTrace();
        }
    }

    private void migrateLocalDataToAiven() {
        System.out.println("Starting local data migration...");
        try {
            // Local connection details
            String localUrl = "jdbc:mysql://localhost:3306/soundnest";
            Connection localConn = DriverManager.getConnection(localUrl, "root", "");

            String selectSql = "SELECT title, path, artist, album FROM songs";
            Statement stmt = localConn.createStatement();
            ResultSet rs = stmt.executeQuery(selectSql);

            int count = 0;
            while (rs.next()) {
                String title = rs.getString("title");
                String path = rs.getString("path");
                String artist = rs.getString("artist");
                String album = rs.getString("album");

                // Insert into Aiven (using existing dbConnection)
                // Check if already exists in Aiven to avoid duplicates
                String checkSql = "SELECT COUNT(*) FROM songs WHERE path = ?";
                PreparedStatement checkStmt = dbConnection.prepareStatement(checkSql);
                checkStmt.setString(1, path);
                ResultSet checkRs = checkStmt.executeQuery();
                checkRs.next();

                if (checkRs.getInt(1) == 0) {
                    insertSongIntoDatabase(title, path, artist, album);
                    count++;
                }
                checkRs.close();
                checkStmt.close();
            }

            rs.close();
            stmt.close();
            localConn.close();
            System.out.println("Migration complete: " + count + " songs moved from XAMPP to Aiven.");

            // Refresh playlist view on JavaFX thread
            Platform.runLater(this::loadSongsFromDatabase);

        } catch (SQLException e) {
            System.out.println("Migration skipped or failed. (Local MySQL might be off or empty)");
        }
    }

}