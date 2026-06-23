package com.aquamark.controller;

import com.aquamark.util.TimeFormatter;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
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

    private static final double HANDLE_W = 8;
    private static final double HANDLE_PAD = 8;

    @FXML
    public void initialize() {
        buildTrack();
        trimTrack.widthProperty().addListener((obs, o, n) -> updatePositions());
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

        trimTrack.getChildren().addAll(trackBg, selectionRect, leftHandle, rightHandle);
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

    public double getStartSeconds()  { return startRatio * totalDuration; }
    public double getEndSeconds()    { return endRatio   * totalDuration; }
    public double getStartRatio()    { return startRatio; }
    public double getEndRatio()      { return endRatio;   }
    public void setOnTrimChanged(Runnable cb) { this.onTrimChanged = cb; }

    private double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
