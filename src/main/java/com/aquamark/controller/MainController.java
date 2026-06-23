package com.aquamark.controller;

import com.aquamark.service.ExportService;
import com.aquamark.util.TimeFormatter;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.SplitPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.io.File;
import java.util.List;

public class MainController {

    @FXML private VideoListController videoListPanelController;
    @FXML private EditorController    editorPanelController;
    @FXML private TimelineController  timelinePanelController;

    @FXML private SplitPane  mainSplit;

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

    // Video name bar
    @FXML private HBox  videoNameBar;
    @FXML private Label lblVideoName;

    // Watermark overlay
    @FXML private Pane watermarkOverlayPane;
    private ImageView  wmOverlay;
    private File       cachedWmFile;
    private Image      cachedWmImage;

    // Seek trim overlay
    @FXML private Pane      seekTrimPane;
    private Rectangle       seekTrimRect;

    private MediaPlayer mediaPlayer;
    private boolean     isPlaying    = false;
    private boolean     seekDragging = false;
    private boolean     videoLoaded  = false;

    private double[] savedDividers = null;

    private final ExportService exportService = new ExportService();

    @FXML
    public void initialize() {
        videoListPanelController.setOnVideoSelected(this::loadVideo);
        editorPanelController.setOnExport(this::doExportCurrent);
        editorPanelController.setOnWatermarkChanged(this::updateWatermarkOverlay);
        timelinePanelController.setOnTrimChanged(this::updateSeekTrim);

        // MediaView preenche o StackPane preservando proporção
        playerStackPane.widthProperty().addListener((obs, o, n)  -> mediaView.setFitWidth(n.doubleValue()));
        playerStackPane.heightProperty().addListener((obs, o, n) -> mediaView.setFitHeight(n.doubleValue()));
        mediaView.setPreserveRatio(true);

        // Watermark overlay
        wmOverlay = new ImageView();
        wmOverlay.setPreserveRatio(true);
        wmOverlay.setMouseTransparent(true);
        wmOverlay.setVisible(false);
        watermarkOverlayPane.getChildren().add(wmOverlay);
        watermarkOverlayPane.widthProperty().addListener((o, a, b)  -> updateWatermarkOverlay());
        watermarkOverlayPane.heightProperty().addListener((o, a, b) -> updateWatermarkOverlay());

        // Seek trim highlight
        seekTrimRect = new Rectangle();
        seekTrimRect.setFill(Color.web("#3b8eea55"));
        seekTrimRect.setMouseTransparent(true);
        seekTrimRect.setVisible(false);
        seekTrimPane.getChildren().add(seekTrimRect);
        seekTrimPane.widthProperty().addListener((o, a, b)  -> updateSeekTrim());
        seekTrimPane.heightProperty().addListener((o, a, b) -> updateSeekTrim());

        // Seek / volume
        sliderSeek.setOnMousePressed(e -> seekDragging = true);
        sliderSeek.setOnMouseReleased(e -> {
            seekDragging = false;
            if (mediaPlayer != null) mediaPlayer.seek(Duration.seconds(sliderSeek.getValue()));
        });
        sliderVolume.valueProperty().addListener((obs, o, n) -> {
            if (mediaPlayer != null) mediaPlayer.setVolume(n.doubleValue());
        });

        // Fullscreen handling
        playerStackPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) return;
            newScene.windowProperty().addListener((wObs, oldWin, newWin) -> {
                if (!(newWin instanceof Stage stage)) return;
                stage.fullScreenProperty().addListener((fsObs, wasFS, isNowFS) -> {
                    if (isNowFS) {
                        savedDividers = mainSplit.getDividerPositions().clone();
                    } else {
                        Platform.runLater(() -> {
                            if (savedDividers != null) mainSplit.setDividerPositions(savedDividers);
                            newScene.getRoot().requestLayout();
                            Platform.runLater(() -> {
                                if (savedDividers != null) mainSplit.setDividerPositions(savedDividers);
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

        // Oculta o empty-state imediatamente — independente de codec/GStreamer
        paneEmpty.setVisible(false);
        paneEmpty.setManaged(false);

        // Atualiza nome do vídeo na barra superior
        lblVideoName.setText(file.getName());
        videoNameBar.setVisible(true);
        videoNameBar.setManaged(true);

        // Habilita exportar
        videoLoaded = true;
        editorPanelController.setVideoLoaded(true);
        editorPanelController.setTotalVideos(videoListPanelController.getVideos().size());

        Media media = new Media(file.toURI().toString());
        mediaPlayer = new MediaPlayer(media);
        mediaView.setMediaPlayer(mediaPlayer);

        mediaPlayer.setOnReady(() -> {
            Duration total = mediaPlayer.getTotalDuration();
            sliderSeek.setMax(total.toSeconds());
            lblTotalTime.setText(TimeFormatter.format(total.toSeconds()));
            timelinePanelController.setTotalDuration(total.toSeconds());
            updateSeekTrim();
        });

        mediaPlayer.setOnError(() -> {
            MediaPlayer.Status st = mediaPlayer.getStatus();
            System.err.println("[AquaMark] Erro ao carregar mídia: "
                + mediaPlayer.getError() + " | status=" + st);
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

    // ── Watermark overlay ─────────────────────────────────────

    private void updateWatermarkOverlay() {
        File file = editorPanelController.getWatermarkFile();
        if (file == null) {
            wmOverlay.setVisible(false);
            return;
        }

        double pW = watermarkOverlayPane.getWidth();
        double pH = watermarkOverlayPane.getHeight();
        if (pW <= 0 || pH <= 0) return;

        if (!file.equals(cachedWmFile)) {
            cachedWmFile  = file;
            cachedWmImage = new Image(file.toURI().toString()); // síncrono
            wmOverlay.setImage(cachedWmImage);
        }

        double size    = Math.min(pW, pH) * (editorPanelController.getWatermarkSizePercent() / 100.0);
        double opacity = editorPanelController.getWatermarkOpacityPercent() / 100.0;
        double wmX     = editorPanelController.getWmX();
        double wmY     = editorPanelController.getWmY();

        wmOverlay.setFitWidth(size);
        wmOverlay.setFitHeight(size);
        wmOverlay.setOpacity(opacity);

        // Calcula o tamanho real renderizado respeitando o aspect ratio da imagem
        double imgW = cachedWmImage.getWidth();
        double imgH = cachedWmImage.getHeight();
        double renderedW, renderedH;
        if (imgW <= 0 || imgH <= 0) {
            renderedW = size;
            renderedH = size;
        } else if (imgW >= imgH) {
            renderedW = size;
            renderedH = size * (imgH / imgW);
        } else {
            renderedH = size;
            renderedW = size * (imgW / imgH);
        }

        // Posiciona usando o tamanho real — wmX=0 cola à esquerda, wmX=1 cola à direita
        wmOverlay.setLayoutX(wmX * (pW - renderedW));
        wmOverlay.setLayoutY(wmY * (pH - renderedH));
        wmOverlay.setVisible(true);
    }

    // ── Seek trim highlight ───────────────────────────────────

    private void updateSeekTrim() {
        if (!videoLoaded) { seekTrimRect.setVisible(false); return; }
        double w = seekTrimPane.getWidth();
        double h = seekTrimPane.getHeight();
        if (w <= 0 || h <= 0) return;

        double pad     = 10; // margem aproximada do thumb
        double usableW = w - pad * 2;
        double startX  = pad + timelinePanelController.getStartRatio() * usableW;
        double endX    = pad + timelinePanelController.getEndRatio()   * usableW;
        double trackH  = 4;
        double trackY  = (h - trackH) / 2.0;

        seekTrimRect.setX(startX);
        seekTrimRect.setY(trackY);
        seekTrimRect.setWidth(Math.max(0, endX - startX));
        seekTrimRect.setHeight(trackH);
        seekTrimRect.setVisible(true);
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
    @FXML private void onExportAll()       { /* TODO */ }

    @FXML private void onAbout() {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Sobre");
        a.setHeaderText(null);
        a.setContentText("Editor de video com marca d'agua.\nAdicione videos, configure edicoes e exporte via FFmpeg.");
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

    private void doExportCurrent() { /* TODO */ }
}
