package com.aquamark.controller;

import com.aquamark.util.TimeFormatter;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;

public class TimelineController {

    @FXML private Label lblStart;
    @FXML private Label lblEnd;
    @FXML private Pane trimTrack;

    private double totalDuration = 0;
    private double startRatio = 0.0;
    private double endRatio   = 1.0;
    private Runnable onTrimChanged;

    private Rectangle trackBg;
    private Rectangle selectionRect;
    private Rectangle leftHandle;
    private Rectangle rightHandle;
    private final Group ticks = new Group();

    private static final double HANDLE_W = 8;
    private static final double HANDLE_PAD = 8;
    private static final double TICK_SPACING = 9; // px entre tracinhos da régua

    @FXML
    public void initialize() {
        buildTrack();
        trimTrack.widthProperty().addListener((obs, o, n) -> { rebuildTicks(); updatePositions(); });
        trimTrack.heightProperty().addListener((obs, o, n) -> rebuildTicks());
    }

    private void buildTrack() {
        trackBg = new Rectangle();
        trackBg.setFill(Color.web("#2b2b2b"));
        trackBg.setArcWidth(4);
        trackBg.setArcHeight(4);
        trackBg.widthProperty().bind(trimTrack.widthProperty());
        trackBg.heightProperty().bind(trimTrack.heightProperty().subtract(HANDLE_PAD * 2));
        trackBg.yProperty().bind(trimTrack.heightProperty().subtract(trackBg.heightProperty()).divide(2));

        selectionRect = new Rectangle();
        selectionRect.setFill(Color.web("#1a4f8a"));
        selectionRect.heightProperty().bind(trackBg.heightProperty());
        selectionRect.yProperty().bind(trackBg.yProperty());

        leftHandle = new Rectangle(HANDLE_W, 0);
        leftHandle.setFill(Color.web("#3b8eea"));
        leftHandle.setArcWidth(3);
        leftHandle.setArcHeight(3);
        leftHandle.heightProperty().bind(trimTrack.heightProperty());
        leftHandle.setCursor(Cursor.H_RESIZE);

        rightHandle = new Rectangle(HANDLE_W, 0);
        rightHandle.setFill(Color.web("#3b8eea"));
        rightHandle.setArcWidth(3);
        rightHandle.setArcHeight(3);
        rightHandle.heightProperty().bind(trimTrack.heightProperty());
        rightHandle.setCursor(Cursor.H_RESIZE);

        leftHandle.setOnMouseDragged(e -> {
            double ratio = clamp(e.getX() / trimTrack.getWidth());
            startRatio = Math.min(ratio, endRatio - 0.01);
            updatePositions();
            updateLabels();
        });

        rightHandle.setOnMouseDragged(e -> {
            double ratio = clamp(e.getX() / trimTrack.getWidth());
            endRatio = Math.max(ratio, startRatio + 0.01);
            updatePositions();
            updateLabels();
        });

        ticks.setMouseTransparent(true);

        trimTrack.getChildren().addAll(trackBg, selectionRect, ticks, leftHandle, rightHandle);
    }

    /** Desenha a régua: tracinho grande seguido de pequeno, repetindo ao longo da trilha. */
    private void rebuildTicks() {
        ticks.getChildren().clear();
        double w = trimTrack.getWidth();
        double h = trimTrack.getHeight();
        if (w <= 0 || h <= 0) return;

        double midY = h / 2.0;
        int i = 0;
        for (double x = HANDLE_W; x <= w - HANDLE_W; x += TICK_SPACING, i++) {
            boolean major = (i % 2 == 0);
            double halfLen = (major ? h * 0.28 : h * 0.14);
            Line line = new Line(x, midY - halfLen, x, midY + halfLen);
            line.setStroke(Color.web(major ? "#ffffff" : "#ffffff", major ? 0.30 : 0.16));
            line.setStrokeWidth(1);
            ticks.getChildren().add(line);
        }
    }

    private void updatePositions() {
        double w = trimTrack.getWidth();
        if (w <= 0) return;

        double lx = startRatio * w;
        double rx = endRatio * w - HANDLE_W;

        leftHandle.setX(lx);
        rightHandle.setX(rx);
        selectionRect.setX(lx + HANDLE_W);
        selectionRect.setWidth(Math.max(0, rx - lx - HANDLE_W));

        if (onTrimChanged != null) onTrimChanged.run();
    }

    private void updateLabels() {
        lblStart.setText("Inicio: " + TimeFormatter.format(startRatio * totalDuration));
        lblEnd.setText("Fim: " + TimeFormatter.format(endRatio * totalDuration));
    }

    @FXML
    private void onReset() {
        startRatio = 0.0;
        endRatio   = 1.0;
        updatePositions();
        updateLabels();
    }

    public void setTotalDuration(double seconds) {
        this.totalDuration = seconds;
        startRatio = 0.0;
        endRatio   = 1.0;
        updatePositions();
        updateLabels();
    }

    /** Restaura o corte salvo de um vídeo. endSeconds &lt;= 0 significa "até o fim". */
    public void setTrim(double startSeconds, double endSeconds) {
        if (totalDuration <= 0) return;
        double sr = startSeconds <= 0 ? 0.0 : Math.min(1.0, startSeconds / totalDuration);
        double er = endSeconds   <= 0 ? 1.0 : Math.min(1.0, endSeconds   / totalDuration);
        if (er <= sr) { sr = 0.0; er = 1.0; }
        startRatio = sr;
        endRatio   = er;
        updatePositions();
        updateLabels();
    }

    public double getStartSeconds()  { return startRatio * totalDuration; }
    public double getEndSeconds()    { return endRatio   * totalDuration; }
    public double getStartRatio()    { return startRatio; }
    public double getEndRatio()      { return endRatio;   }
    public void setOnTrimChanged(Runnable cb) { this.onTrimChanged = cb; }

    private double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
