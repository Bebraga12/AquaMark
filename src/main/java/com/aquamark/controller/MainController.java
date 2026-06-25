package com.aquamark.controller;

import com.aquamark.model.ResolutionPreset;
import com.aquamark.model.TrimRange;
import com.aquamark.model.VideoProject;
import com.aquamark.service.PreviewService;
import com.aquamark.util.TimeFormatter;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
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
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

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

    // FFmpeg pipe preview (fallback quando MediaPlayer não funciona)
    @FXML private ImageView      previewImageView;
    private final PreviewService previewService    = new PreviewService();
    private boolean              ffmpegPreviewMode = false;
    private volatile double      ffmpegPosition    = 0;   // escrito pelo reader, lido pelo FX timer
    private double               ffmpegDuration    = 0;
    private double               ffmpegFps         = 30;
    private double               pipeStartTime     = 0;
    private volatile Image       latestFrame       = null; // handoff reader → FX thread
    private Process              pipeProcess       = null;
    private Thread               readerThread      = null;
    private javafx.animation.AnimationTimer displayTimer = null;

    private Process     audioPipeProcess = null;
    private String      currentVfFilter  = null; // filtro de rotação/resolução para o pipe

    private MediaPlayer mediaPlayer;
    private boolean     isPlaying    = false;
    private boolean     seekDragging = false;
    private boolean     videoLoaded  = false;
    private File        currentFile  = null;

    private double[] savedDividers = null;

    @FXML
    public void initialize() {
        videoListPanelController.setOnVideoSelected(this::loadVideo);
        editorPanelController.setOnExport(this::doExportCurrent);
        editorPanelController.setOnExportAll(this::doExportAll);
        editorPanelController.setOnWatermarkChanged(this::updateWatermarkOverlay);
        editorPanelController.setOnPreviewParamsChanged(this::onPreviewParamsChanged);
        timelinePanelController.setOnTrimChanged(this::updateSeekTrim);

        // MediaView e previewImageView preenchem o StackPane preservando proporção
        playerStackPane.widthProperty().addListener((obs, o, n) -> {
            mediaView.setFitWidth(n.doubleValue());
            previewImageView.setFitWidth(n.doubleValue());
        });
        playerStackPane.heightProperty().addListener((obs, o, n) -> {
            mediaView.setFitHeight(n.doubleValue());
            previewImageView.setFitHeight(n.doubleValue());
        });
        mediaView.setPreserveRatio(true);
        previewImageView.setPreserveRatio(true);

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
            double val = sliderSeek.getValue();
            if (ffmpegPreviewMode)          ffmpegSeekTo(val);
            else if (mediaPlayer != null)   mediaPlayer.seek(Duration.seconds(val));
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
        // Para e descarta player/preview anterior
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
        stopPipe();
        ffmpegPreviewMode = false;
        ffmpegPosition    = 0;
        previewImageView.setVisible(false);
        previewImageView.setImage(null);
        isPlaying = false;
        btnPlayPause.setText("▶");

        // Oculta o empty-state imediatamente — independente de codec/GStreamer
        paneEmpty.setVisible(false);
        paneEmpty.setManaged(false);

        // Atualiza nome do vídeo na barra superior
        lblVideoName.setText(file.getName());
        videoNameBar.setVisible(true);
        videoNameBar.setManaged(true);

        videoLoaded = true;
        currentFile = file;

        try {
            Media media = new Media(file.toURI().toString());
            mediaPlayer = new MediaPlayer(media);
            mediaView.setMediaPlayer(mediaPlayer);

            mediaPlayer.setOnReady(() -> {
                Duration total = mediaPlayer.getTotalDuration();
                sliderSeek.setMax(total.toSeconds());
                lblTotalTime.setText(TimeFormatter.format(total.toSeconds()));
                timelinePanelController.setTotalDuration(total.toSeconds());
                updateSeekTrim();
                applyTrimToMediaPlayer();
            });

            // Quando chega ao fim do corte, volta ao início do corte
            mediaPlayer.setOnStopped(() -> {
                double trimStart = timelinePanelController.getStartSeconds();
                mediaPlayer.seek(Duration.seconds(trimStart));
            });

            mediaPlayer.setOnError(() ->
                System.err.println("[AquaMark] Erro de mídia: " + mediaPlayer.getError()));

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
        } catch (Exception e) {
            // JavaFX Media não suporta o codec — usa preview via FFmpeg
            System.err.println("[AquaMark] MediaPlayer falhou, usando preview FFmpeg: " + e.getMessage());
            startFFmpegPreview(file);
        }
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

    // ── Preview: rotação e resolução ─────────────────────────

    /** Constrói o filtro -vf para o pipe de preview com base nas seleções atuais do editor. */
    private String buildVfFilter() {
        StringBuilder vf = new StringBuilder();

        // Rotação
        double rot = editorPanelController.getRotation();
        if (Math.abs(rot - 90) < 0.5)                    vf.append("transpose=1");
        else if (Math.abs(rot + 90) < 0.5)               vf.append("transpose=2");
        else if (Math.abs(Math.abs(rot) - 180) < 0.5)    vf.append("transpose=1,transpose=1");
        else if (Math.abs(rot) > 0.5)
            vf.append(String.format("rotate=%.4f*PI/180:fillcolor=black", rot));

        // Resolução
        ResolutionPreset preset = editorPanelController.getResolution();
        if (preset != ResolutionPreset.ORIGINAL) {
            int w = preset.getWidth(), h = preset.getHeight();
            String color = editorPanelController.getLetterboxColor().replace("#", "");
            String res = String.format(
                "scale=%d:%d:force_original_aspect_ratio=decrease,pad=%d:%d:(ow-iw)/2:(oh-ih)/2:color=0x%s",
                w, h, w, h, color);
            if (vf.length() > 0) vf.append(",");
            vf.append(res);
        }

        return vf.length() > 0 ? vf.toString() : null;
    }

    /** Chamado quando rotação ou resolução muda no editor. */
    private void onPreviewParamsChanged() {
        if (!ffmpegPreviewMode || currentFile == null) return;
        currentVfFilter = buildVfFilter();
        if (isPlaying) {
            // Reinicia o pipe com o novo filtro mantendo a posição atual
            boolean wasPlaying = isPlaying;
            ffmpegPause();
            if (wasPlaying) ffmpegPlay();
        } else {
            // Só atualiza o frame estático parado
            final double pos = ffmpegPosition;
            final String filter = currentVfFilter;
            Thread t = new Thread(() -> {
                try {
                    Image img = previewService.extractFrame(currentFile, pos, filter);
                    Platform.runLater(() -> { if (img != null) previewImageView.setImage(img); });
                } catch (Exception ignored) {}
            }, "preview-params-frame");
            t.setDaemon(true);
            t.start();
        }
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

        // Atualiza stop time do MediaPlayer quando o corte muda
        applyTrimToMediaPlayer();
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
    @FXML private void onExportAll()       { doExportAll();     }

    @FXML private void onAbout() {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Sobre");
        a.setHeaderText(null);
        a.setContentText("Editor de video com marca d'agua.\nAdicione videos, configure edicoes e exporte via FFmpeg.");
        a.showAndWait();
    }

    // ── FFmpeg pipe preview ───────────────────────────────────
    //
    // Um único processo ffmpeg emite frames MJPEG pelo stdout.
    // Um thread leitor parseia os bytes (SOI=FF D8 … EOI=FF D9),
    // decodifica o JPEG e armazena em `latestFrame`.
    // O AnimationTimer (roda no FX thread a ~60fps) aplica o frame
    // ao ImageView e atualiza o seek slider.

    private void startFFmpegPreview(File file) {
        ffmpegPreviewMode = true;
        ffmpegPosition    = 0;
        previewImageView.setVisible(true);

        Thread init = new Thread(() -> {
            try {
                double dur  = previewService.getDuration(file);
                double fps  = previewService.getFps(file);
                int[]  size = previewService.getVideoSize(file);
                Image  frm  = previewService.extractFrame(file, 0, null);
                Platform.runLater(() -> {
                    ffmpegDuration = dur;
                    ffmpegFps      = fps > 0 ? fps : 30;
                    sliderSeek.setMax(dur);
                    lblTotalTime.setText(TimeFormatter.format(dur));
                    timelinePanelController.setTotalDuration(dur);
                    updateSeekTrim();
                    if (size != null) editorPanelController.setOriginalResolution(size[0], size[1]);
                    if (frm != null) previewImageView.setImage(frm);
                });
            } catch (Exception e) {
                System.err.println("[AquaMark] ffprobe falhou: " + e.getMessage());
            }
        }, "preview-init");
        init.setDaemon(true);
        init.start();
    }

    private void stopPipe() {
        if (displayTimer     != null) { displayTimer.stop(); displayTimer = null; }
        if (readerThread     != null) { readerThread.interrupt(); readerThread = null; }
        if (pipeProcess      != null) { pipeProcess.destroyForcibly(); pipeProcess = null; }
        if (audioPipeProcess != null) { audioPipeProcess.destroyForcibly(); audioPipeProcess = null; }
        latestFrame = null;
    }

    private void ffmpegPlay() {
        stopPipe();

        double trimStart = timelinePanelController.getStartSeconds();
        double trimEnd   = timelinePanelController.getEndSeconds();

        // Se estiver fora do corte, volta para o início do corte
        if (ffmpegPosition < trimStart || ffmpegPosition >= trimEnd) {
            ffmpegPosition = trimStart;
            sliderSeek.setValue(ffmpegPosition);
            lblCurrentTime.setText(TimeFormatter.format(ffmpegPosition));
        }

        pipeStartTime = ffmpegPosition;
        long[] frameIdx = {0};

        try {
            pipeProcess = previewService.startPipe(currentFile, pipeStartTime, currentVfFilter);
        } catch (Exception e) {
            System.err.println("[AquaMark] Erro ao iniciar pipe: " + e.getMessage());
            return;
        }

        InputStream stream = pipeProcess.getInputStream();

        readerThread = new Thread(() -> {
            ByteArrayOutputStream buf = new ByteArrayOutputStream(1 << 17);
            int prev = -1, b;
            boolean inFrame = false;
            try {
                while ((b = stream.read()) != -1 && !Thread.currentThread().isInterrupted()) {
                    if (!inFrame) {
                        if (prev == 0xFF && b == 0xD8) {
                            inFrame = true;
                            buf.reset();
                            buf.write(0xFF); buf.write(0xD8);
                        }
                    } else {
                        buf.write(b);
                        if (prev == 0xFF && b == 0xD9) {
                            byte[] jpeg = buf.toByteArray();
                            frameIdx[0]++;
                            ffmpegPosition = pipeStartTime + frameIdx[0] / ffmpegFps;
                            latestFrame = new Image(new ByteArrayInputStream(jpeg));
                            buf.reset();
                            inFrame = false;
                        }
                    }
                    prev = b;
                }
            } catch (Exception ignored) {}
        }, "pipe-reader");
        readerThread.setDaemon(true);
        readerThread.start();

        displayTimer = new AnimationTimer() {
            @Override public void handle(long now) {
                Image frame = latestFrame;
                if (frame != null) {
                    latestFrame = null;
                    previewImageView.setImage(frame);
                }
                double pos = ffmpegPosition;
                if (!seekDragging && pos > 0) {
                    sliderSeek.setValue(Math.min(pos, ffmpegDuration));
                    lblCurrentTime.setText(TimeFormatter.format(pos));
                }
                // Para ao atingir o fim do corte
                if (pos >= timelinePanelController.getEndSeconds()) {
                    ffmpegPause();
                    ffmpegPosition = timelinePanelController.getStartSeconds();
                    Platform.runLater(() -> {
                        sliderSeek.setValue(ffmpegPosition);
                        lblCurrentTime.setText(TimeFormatter.format(ffmpegPosition));
                    });
                }
            }
        };
        displayTimer.start();
        try {
            audioPipeProcess = previewService.startAudioPlayer(currentFile, pipeStartTime);
        } catch (Exception e) {
            System.err.println("[AquaMark] Áudio falhou: " + e.getMessage());
        }
        isPlaying = true;
        btnPlayPause.setText("⏸");
    }

    private void ffmpegPause() {
        stopPipe();
        isPlaying = false;
        btnPlayPause.setText("▶");
    }

    private void ffmpegSeekTo(double seconds) {
        boolean wasPlaying = isPlaying;
        ffmpegPause();
        ffmpegPosition = seconds;
        lblCurrentTime.setText(TimeFormatter.format(seconds));
        // Mostra frame estático no ponto do seek
        Thread t = new Thread(() -> {
            try {
                Image img = previewService.extractFrame(currentFile, seconds, currentVfFilter);
                Platform.runLater(() -> { if (img != null) previewImageView.setImage(img); });
            } catch (Exception ignored) {}
        }, "seek-frame");
        t.setDaemon(true);
        t.start();
        if (wasPlaying) ffmpegPlay();
    }

    private void applyTrimToMediaPlayer() {
        if (mediaPlayer == null) return;
        double trimStart = timelinePanelController.getStartSeconds();
        double trimEnd   = timelinePanelController.getEndSeconds();
        mediaPlayer.setStartTime(Duration.seconds(trimStart));
        if (trimEnd > 0) mediaPlayer.setStopTime(Duration.seconds(trimEnd));
    }

    // ── Player controls ──────────────────────────────────────

    @FXML private void onPlayPause() {
        if (ffmpegPreviewMode) {
            if (isPlaying) ffmpegPause(); else ffmpegPlay();
            return;
        }
        if (mediaPlayer == null) return;
        if (isPlaying) {
            mediaPlayer.pause();
        } else {
            applyTrimToMediaPlayer();
            double trimStart = timelinePanelController.getStartSeconds();
            double trimEnd   = timelinePanelController.getEndSeconds();
            double pos       = mediaPlayer.getCurrentTime().toSeconds();
            if (pos < trimStart || pos >= trimEnd)
                mediaPlayer.seek(Duration.seconds(trimStart));
            mediaPlayer.play();
        }
    }

    @FXML private void onMute() {
        boolean muted = btnMute.isSelected();
        btnMute.setText(muted ? "✕" : "♪");
        if (mediaPlayer != null) mediaPlayer.setMute(muted);
    }

    private void doExportCurrent() {
        if (currentFile == null) return;
        openExportDialog(List.of(buildProject(currentFile)));
    }

    private void doExportAll() {
        List<File> files = videoListPanelController.getVideos();
        if (files.isEmpty()) return;
        List<VideoProject> projects = files.stream()
            .map(this::buildProject)
            .collect(Collectors.toList());
        openExportDialog(projects);
    }

    private VideoProject buildProject(File file) {
        VideoProject p = new VideoProject(file);
        p.setRotation(editorPanelController.getRotation());
        p.setResolution(editorPanelController.getResolution());
        p.setLetterboxColor(editorPanelController.getLetterboxColor());
        p.setWatermarkConfig(editorPanelController.getWatermarkConfig());
        p.setTrimRange(new TrimRange(
            timelinePanelController.getStartSeconds(),
            timelinePanelController.getEndSeconds()));
        return p;
    }

    private void openExportDialog(List<VideoProject> projects) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ExportDialog.fxml"));
            Parent root = loader.load();
            ExportDialogController ctrl = loader.getController();
            ctrl.setProjects(projects);

            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner((Stage) playerStackPane.getScene().getWindow());
            dialog.initStyle(StageStyle.UNDECORATED);
            dialog.setResizable(false);

            Scene scene = new Scene(root);
            scene.getStylesheets().add(
                getClass().getResource("/css/dark-theme.css").toExternalForm());
            dialog.setScene(scene);
            dialog.setOnShown(e -> centerDialog(dialog));
            dialog.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void centerDialog(Stage dialog) {
        Stage owner = (Stage) playerStackPane.getScene().getWindow();
        dialog.setX(owner.getX() + (owner.getWidth()  - dialog.getWidth())  / 2);
        dialog.setY(owner.getY() + (owner.getHeight() - dialog.getHeight()) / 2);
    }
}
