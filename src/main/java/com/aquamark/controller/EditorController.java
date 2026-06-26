package com.aquamark.controller;

import com.aquamark.model.ResolutionPreset;
import com.aquamark.model.WatermarkConfig;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.File;

public class EditorController {

    // Rotation
    @FXML private Slider sliderRotation;
    @FXML private Label  lblRotationValue;

    // Resolution
    @FXML private ToggleGroup   resolutionGroup;
    @FXML private ToggleButton  btnResOriginal;
    @FXML private ToggleButton  btnResVertical;
    @FXML private ToggleButton  btnResHorizontal;
    @FXML private ToggleButton  btnResSquare;
    @FXML private HBox          hboxLetterbox;
    @FXML private ColorPicker   colorLetterbox;

    // Watermark
    @FXML private Label    lblWatermarkFile;
    @FXML private Slider   sliderWatermarkSize;
    @FXML private Label    lblWatermarkSize;
    @FXML private Slider   sliderWatermarkOpacity;
    @FXML private Label    lblWatermarkOpacity;
    @FXML private CheckBox chkWmPositionIndividual;
    @FXML private Pane     watermarkPositionPane;

    @FXML private Button btnExport;
    @FXML private Button btnExportAll;

    private File     watermarkFile;
    private Runnable onExportCallback;
    private Runnable onExportAllCallback;
    private Runnable onWatermarkChanged;
    private Runnable onPreviewParamsChanged;
    private Runnable onWmPositionModeChanged;

    // Quando true, setters programáticos (restauração de estado) não disparam callbacks.
    private boolean suppressNotify = false;

    private double wmX = 0.95;
    private double wmY = 0.05;
    private double previewRatio = 16.0 / 9.0;

    private Rectangle previewRect;
    private Circle    wmCircle;

    @FXML
    public void initialize() {
        sliderRotation.valueProperty().addListener((obs, o, n) ->
            lblRotationValue.setText(String.format("%.0f°", n.doubleValue())));
        sliderRotation.setOnMouseReleased(e -> notifyPreviewParamsChanged());

        sliderWatermarkSize.valueProperty().addListener((obs, o, n) -> {
            lblWatermarkSize.setText(String.format("%.0f%%", n.doubleValue()));
            notifyWatermarkChanged();
        });

        sliderWatermarkOpacity.valueProperty().addListener((obs, o, n) -> {
            lblWatermarkOpacity.setText(String.format("%.0f%%", n.doubleValue()));
            notifyWatermarkChanged();
        });

        resolutionGroup.selectedToggleProperty().addListener((obs, o, n) -> {
            boolean isOriginal = (n == btnResOriginal || n == null);
            hboxLetterbox.setVisible(!isOriginal);
            hboxLetterbox.setManaged(!isOriginal);

            if      (n == btnResVertical)   previewRatio = 9.0 / 16.0;
            else if (n == btnResSquare)     previewRatio = 1.0;
            else                            previewRatio = 16.0 / 9.0;
            rebuildPreview();
            notifyPreviewParamsChanged();
        });

        colorLetterbox.valueProperty().addListener((obs, o, n) -> notifyPreviewParamsChanged());

        chkWmPositionIndividual.selectedProperty().addListener((obs, o, n) -> {
            if (!suppressNotify && onWmPositionModeChanged != null) onWmPositionModeChanged.run();
        });

        watermarkPositionPane.widthProperty().addListener((o, a, b) -> rebuildPreview());
        watermarkPositionPane.heightProperty().addListener((o, a, b) -> rebuildPreview());
        Tooltip.install(watermarkPositionPane,
            new Tooltip("Arraste para posicionar a marca d'água no vídeo"));
    }

    // ── Preview drag ──────────────────────────────────────────────

    private void rebuildPreview() {
        double pW = watermarkPositionPane.getWidth();
        double pH = watermarkPositionPane.getHeight();
        if (pW < 10 || pH < 10) return;

        double margin = 8;
        double maxW = pW - margin * 2;
        double maxH = pH - margin * 2;

        double rectW, rectH;
        if (previewRatio >= 1.0) {
            rectW = maxW;
            rectH = rectW / previewRatio;
            if (rectH > maxH) { rectH = maxH; rectW = rectH * previewRatio; }
        } else {
            rectH = maxH;
            rectW = rectH * previewRatio;
            if (rectW > maxW) { rectW = maxW; rectH = rectW / previewRatio; }
        }

        double rx = (pW - rectW) / 2.0;
        double ry = (pH - rectH) / 2.0;

        if (previewRect == null) {
            previewRect = new Rectangle();
            previewRect.setFill(Color.web("#2b2b2b"));
            previewRect.setStroke(Color.web("#5a5a5a"));
            previewRect.setStrokeWidth(1);
            previewRect.setArcWidth(3);
            previewRect.setArcHeight(3);

            wmCircle = new Circle(7);
            wmCircle.setFill(Color.web("#3b8eea"));
            wmCircle.setStroke(Color.WHITE);
            wmCircle.setStrokeWidth(1.5);
            wmCircle.setCursor(Cursor.HAND);

            watermarkPositionPane.getChildren().addAll(previewRect, wmCircle);
        }

        previewRect.setX(rx);
        previewRect.setY(ry);
        previewRect.setWidth(rectW);
        previewRect.setHeight(rectH);

        final double fRx = rx, fRy = ry, fRW = rectW, fRH = rectH;
        updateCircle(fRx, fRy, fRW, fRH);

        EventHandler<MouseEvent> moveHandler = e -> {
            Point2D p = watermarkPositionPane.sceneToLocal(e.getSceneX(), e.getSceneY());
            wmX = Math.max(0, Math.min(1, (p.getX() - fRx) / fRW));
            wmY = Math.max(0, Math.min(1, (p.getY() - fRy) / fRH));
            updateCircle(fRx, fRy, fRW, fRH);
            notifyWatermarkChanged();
            e.consume();
        };

        previewRect.setOnMousePressed(moveHandler);
        previewRect.setOnMouseDragged(moveHandler);
        wmCircle.setOnMousePressed(moveHandler);
        wmCircle.setOnMouseDragged(moveHandler);
    }

    private void updateCircle(double rx, double ry, double rW, double rH) {
        wmCircle.setCenterX(rx + wmX * rW);
        wmCircle.setCenterY(ry + wmY * rH);
    }

    // ── Rotation shortcuts ─────────────────────────────────────────

    @FXML private void onRotateMinus90() { shiftRotation(-90); notifyPreviewParamsChanged(); }
    @FXML private void onRotatePlus90()  { shiftRotation(90);  notifyPreviewParamsChanged(); }
    @FXML private void onState0()        { sliderRotation.setValue(0); notifyPreviewParamsChanged(); }

    private void shiftRotation(double delta) {
        double v = sliderRotation.getValue() + delta;
        sliderRotation.setValue(Math.max(-180, Math.min(180, v)));
    }

    // ── Watermark file ─────────────────────────────────────────────

    @FXML
    private void onChooseWatermark() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Escolher Marca d'Água");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Imagens", "*.png", "*.jpg", "*.jpeg", "*.gif"));
        Stage stage = (Stage) btnExport.getScene().getWindow();
        File file = chooser.showOpenDialog(stage);
        if (file != null) {
            watermarkFile = file;
            lblWatermarkFile.setText(file.getName());
            notifyWatermarkChanged();
        }
    }

    // ── Export buttons → callbacks (MainController abre o dialog) ──

    @FXML private void onExport()    { if (onExportCallback    != null) onExportCallback.run();    }
    @FXML private void onExportAll() { if (onExportAllCallback != null) onExportAllCallback.run(); }

    public void setOnExport(Runnable cb)             { this.onExportCallback       = cb; }
    public void setOnExportAll(Runnable cb)          { this.onExportAllCallback    = cb; }
    public void setOnWatermarkChanged(Runnable cb)   { this.onWatermarkChanged     = cb; }
    public void setOnPreviewParamsChanged(Runnable cb){ this.onPreviewParamsChanged = cb; }
    public void setOnWmPositionModeChanged(Runnable cb){ this.onWmPositionModeChanged = cb; }

    // ── Restauração de estado por vídeo ────────────────────────────

    public boolean isWatermarkPositionIndividual() { return chkWmPositionIndividual.isSelected(); }

    /** Move a marca d'água no preview sem disparar callbacks de mudança. */
    public void setWatermarkPosition(double x, double y) {
        wmX = x;
        wmY = y;
        if (previewRect != null) {
            updateCircle(previewRect.getX(), previewRect.getY(),
                         previewRect.getWidth(), previewRect.getHeight());
        }
    }

    /** Define posição, tamanho e opacidade da marca sem disparar callbacks. */
    public void setWatermarkAppearance(double x, double y, double size, double opacity) {
        suppressNotify = true;
        setWatermarkPosition(x, y);
        sliderWatermarkSize.setValue(size);
        sliderWatermarkOpacity.setValue(opacity);
        suppressNotify = false;
    }

    /** Aplica o estado salvo de um vídeo (rotação + marca d'água) sem disparar callbacks. */
    public void applyState(double rotation, boolean wmIndividual,
                           double wmX, double wmY, double wmSize, double wmOpacity) {
        suppressNotify = true;
        sliderRotation.setValue(rotation);
        chkWmPositionIndividual.setSelected(wmIndividual);
        setWatermarkPosition(wmX, wmY);
        sliderWatermarkSize.setValue(wmSize);
        sliderWatermarkOpacity.setValue(wmOpacity);
        suppressNotify = false;
    }

    /** Atualiza o texto do botão Original com a resolução detectada. */
    public void setOriginalResolution(int w, int h) {
        btnResOriginal.setText("Original (" + w + "×" + h + ")");
    }

    // ── Getters ────────────────────────────────────────────────────

    public double getRotation() { return sliderRotation.getValue(); }

    public ResolutionPreset getResolution() {
        Toggle sel = resolutionGroup.getSelectedToggle();
        if (sel == btnResVertical)   return ResolutionPreset.VERTICAL_9_16;
        if (sel == btnResHorizontal) return ResolutionPreset.HORIZONTAL_16_9;
        if (sel == btnResSquare)     return ResolutionPreset.SQUARE_1_1;
        return ResolutionPreset.ORIGINAL;
    }

    public String getLetterboxColor() {
        Color c = colorLetterbox.getValue();
        return String.format("#%02x%02x%02x",
            (int)(c.getRed()   * 255),
            (int)(c.getGreen() * 255),
            (int)(c.getBlue()  * 255));
    }

    public WatermarkConfig getWatermarkConfig() {
        if (watermarkFile == null) return null;
        WatermarkConfig cfg = new WatermarkConfig();
        cfg.setFile(watermarkFile);
        cfg.setSizePercent((int) sliderWatermarkSize.getValue());
        cfg.setOpacityPercent((int) sliderWatermarkOpacity.getValue());
        cfg.setPositionX(wmX);
        cfg.setPositionY(wmY);
        return cfg;
    }

    public File   getWatermarkFile()           { return watermarkFile; }
    public double getWmX()                     { return wmX; }
    public double getWmY()                     { return wmY; }
    public double getWatermarkSizePercent()    { return sliderWatermarkSize.getValue(); }
    public double getWatermarkOpacityPercent() { return sliderWatermarkOpacity.getValue(); }

    private void notifyWatermarkChanged() {
        if (!suppressNotify && onWatermarkChanged != null) onWatermarkChanged.run();
    }

    private void notifyPreviewParamsChanged() {
        if (!suppressNotify && onPreviewParamsChanged != null) onPreviewParamsChanged.run();
    }
}
