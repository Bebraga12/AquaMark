package com.aquamark.controller;

import com.aquamark.service.ExportService;
import com.aquamark.util.TimeFormatter;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.io.File;
import java.util.List;

public class MainController {

    // Sub-controllers injetados via fx:include
    @FXML private VideoListController videoListPanelController;
    @FXML private EditorController    editorPanelController;
    @FXML private TimelineController  timelinePanelController;

    // Layout
    @FXML private SplitPane mainSplit;

    // Player
    @FXML private StackPane    playerStackPane;
    @FXML private VBox         paneEmpty;
    @FXML private MediaView    mediaView;
    @FXML private Button       btnPlayPause;
    @FXML private Slider       sliderSeek;
    @FXML private Label        lblCurrentTime;
    @FXML private Label        lblTotalTime;
    @FXML private ToggleButton btnMute;
    @FXML private Slider       sliderVolume;

    private MediaPlayer mediaPlayer;
    private boolean     isPlaying    = false;
    private boolean     seekDragging = false;

    // Dividers salvos antes do fullscreen via menu (stage.setFullScreen)
    private double[] savedDividers = null;

    private final ExportService exportService = new ExportService();

    @FXML
    public void initialize() {
        videoListPanelController.setOnVideoSelected(this::loadVideo);
        editorPanelController.setOnExport(this::doExportCurrent);

        // Usar listener em vez de bind evita ciclo de dependência de prefHeight/minHeight
        playerStackPane.widthProperty().addListener((obs, o, n) -> mediaView.setFitWidth(n.doubleValue()));
        playerStackPane.heightProperty().addListener((obs, o, n) -> mediaView.setFitHeight(n.doubleValue()));
        mediaView.setPreserveRatio(true);

        sliderSeek.setOnMousePressed(e -> seekDragging = true);
        sliderSeek.setOnMouseReleased(e -> {
            seekDragging = false;
            if (mediaPlayer != null) mediaPlayer.seek(Duration.seconds(sliderSeek.getValue()));
        });

        sliderVolume.valueProperty().addListener((obs, o, n) -> {
            if (mediaPlayer != null) mediaPlayer.setVolume(n.doubleValue());
        });

        playerStackPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) return;
            newScene.windowProperty().addListener((wObs, oldWin, newWin) -> {
                if (!(newWin instanceof Stage stage)) return;
                stage.fullScreenProperty().addListener((fsObs, wasFS, isNowFS) -> {
                    if (isNowFS) {
                        savedDividers = mainSplit.getDividerPositions().clone();
                    } else {
                        Platform.runLater(() -> {
                            if (savedDividers != null) {
                                mainSplit.setDividerPositions(savedDividers);
                            }
                            newScene.getRoot().requestLayout();
                            Platform.runLater(() -> {
                                if (savedDividers != null) {
                                    mainSplit.setDividerPositions(savedDividers);
                                }
                                newScene.getRoot().applyCss();
                                newScene.getRoot().layout();
                            });
                        });
                    }
                });
            });
        });
    }

    private void loadVideo(File file) {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
        }

        Media media = new Media(file.toURI().toString());
        mediaPlayer = new MediaPlayer(media);
        mediaView.setMediaPlayer(mediaPlayer);

        mediaPlayer.setOnReady(() -> {
            Duration total = mediaPlayer.getTotalDuration();
            sliderSeek.setMax(total.toSeconds());
            lblTotalTime.setText(TimeFormatter.format(total.toSeconds()));
            timelinePanelController.setTotalDuration(total.toSeconds());
            paneEmpty.setVisible(false);
            paneEmpty.setManaged(false);
        });

        mediaPlayer.currentTimeProperty().addListener((obs, o, n) -> {
            if (!seekDragging) sliderSeek.setValue(n.toSeconds());
            lblCurrentTime.setText(TimeFormatter.format(n.toSeconds()));
        });

        mediaPlayer.statusProperty().addListener((obs, o, n) -> {
            isPlaying = (n == MediaPlayer.Status.PLAYING);
            btnPlayPause.setText(isPlaying ? "⏸" : "▶");
        });

        mediaPlayer.setVolume(sliderVolume.getValue());
        mediaPlayer.play();
    }

    // ── MenuBar ──────────────────────────────────────────────

    @FXML private void onAddVideos() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Adicionar Videos");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Videos", "*.mp4", "*.mov", "*.avi", "*.mkv", "*.webm", "*.m4v")
        );
        Stage stage = (Stage) playerStackPane.getScene().getWindow();
        List<File> files = fc.showOpenMultipleDialog(stage);
        if (files != null) videoListPanelController.addVideos(files);
    }

    @FXML private void onExit() {
        ((Stage) playerStackPane.getScene().getWindow()).close();
    }

    @FXML private void onUndo()            { /* TODO */ }
    @FXML private void onRedo()            { /* TODO */ }
    @FXML private void onSelectAll()       { /* TODO */ }
    @FXML private void onClearSelection()  { /* TODO */ }
    @FXML private void onFullScreen() {
        Stage stage = (Stage) playerStackPane.getScene().getWindow();
        stage.setFullScreen(!stage.isFullScreen());
    }
    @FXML private void onProjectSettings() { /* TODO */ }
    @FXML private void onExportSelected()  { doExportCurrent(); }
    @FXML private void onExportAll()       { /* TODO: exportacao em lote */ }

    @FXML private void onAbout() {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Sobre");
        a.setHeaderText(null);
        a.setContentText("Editor de video com marca d’agua.\nAdicione videos, configure edicoes e exporte via FFmpeg.");
        a.showAndWait();
    }

    // ── Player controls ──────────────────────────────────────

    @FXML private void onPlayPause() {
        if (mediaPlayer == null) return;
        if (isPlaying) mediaPlayer.pause();
        else           mediaPlayer.play();
    }

    @FXML private void onMute() {
        if (mediaPlayer == null) return;
        boolean muted = btnMute.isSelected();
        mediaPlayer.setMute(muted);
        btnMute.setText(muted ? "✕" : "♪");
    }

    private void doExportCurrent() {
        // TODO: coletar VideoProject do estado atual e chamar exportService.exportSingle(...)
    }
}
