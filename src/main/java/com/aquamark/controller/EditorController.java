package com.aquamark.controller;

import com.aquamark.model.ResolutionPreset;
import com.aquamark.model.WatermarkConfig;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
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
    @FXML private Label  lblWatermarkFile;
    @FXML private Slider sliderWatermarkSize;
    @FXML private Label  lblWatermarkSize;
    @FXML private Slider sliderWatermarkOpacity;
    @FXML private Label  lblWatermarkOpacity;
    @FXML private Pane   watermarkPositionPane;

    @FXML private Button btnExport;
    @FXML private Button btnExportAll;

    private File     watermarkFile;
    private Runnable onExportCallback;
    private Runnable onWatermarkChanged;
    private int      totalVideos = 1;

    private double wmX = 0.95;
    private double wmY = 0.05;
    // Razão de aspecto do preview (largura/altura)
    private double previewRatio = 16.0 / 9.0;

    private Rectangle previewRect;
    private Circle    wmCircle;

    @FXML
    public void initialize() {
        sliderRotation.valueProperty().addListener((obs, o, n) ->
            lblRotationValue.setText(String.format("%.0f°", n.doubleValue())));

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
        });

        watermarkPositionPane.widthProperty().addListener((o, a, b) -> rebuildPreview());
        watermarkPositionPane.heightProperty().addListener((o, a, b) -> rebuildPreview());
        Tooltip.install(watermarkPositionPane, new Tooltip("Arraste para posicionar a marca d'água no vídeo"));
    }

    // ── Preview drag ──────────────────────────────────────────

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

    // ── Rotation shortcuts ────────────────────────────────────

    @FXML private void onRotateMinus90() { shiftRotation(-90); }
    @FXML private void onRotatePlus90()  { shiftRotation(90); }
    @FXML private void onState0()        { sliderRotation.setValue(0); }

    private void shiftRotation(double delta) {
      double currentValue = sliderRotation.getValue();
      double newValue = currentValue + delta;

      if (newValue > 180) {
          sliderRotation.setValue(180);

      } else  if (newValue < -180) {
        sliderRotation.setValue(-180);

      } else {
        sliderRotation.setValue(newValue);
        
      }
    }

    // ── Watermark file ────────────────────────────────────────

    @FXML
    private void onChooseWatermark() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Escolher Marca d'Agua");
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

    // ── Export ────────────────────────────────────────────────

    @FXML
    private void onExport() {
        openExportDialog(false);
    }

    @FXML
    private void onExportAll() {
        openExportDialog(true);
    }

    public void setOnExport(Runnable callback)          { this.onExportCallback    = callback; }
    public void setOnWatermarkChanged(Runnable callback){ this.onWatermarkChanged  = callback; }
    public void setTotalVideos(int count)               { this.totalVideos = count; }

    public void setVideoLoaded(boolean loaded) {
        btnExport.setDisable(!loaded);
        btnExportAll.setDisable(!loaded);
    }

    private void notifyWatermarkChanged() {
        if (onWatermarkChanged != null) onWatermarkChanged.run();
    }

    public File   getWatermarkFile()          { return watermarkFile; }
    public double getWmX()                    { return wmX; }
    public double getWmY()                    { return wmY; }
    public double getWatermarkSizePercent()   { return sliderWatermarkSize.getValue(); }
    public double getWatermarkOpacityPercent(){ return sliderWatermarkOpacity.getValue(); }

    private void openExportDialog(boolean exportAll) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/ExportDialog.fxml"));
            Parent root = loader.load();

            ExportDialogController ctrl = loader.getController();
            ctrl.setExportAll(exportAll, exportAll ? totalVideos : 1);

            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner((Stage) btnExport.getScene().getWindow());
            dialog.initStyle(StageStyle.UNDECORATED);
            dialog.setResizable(false);

            Scene scene = new Scene(root);
            scene.getStylesheets().add(
                getClass().getResource("/css/dark-theme.css").toExternalForm());
            dialog.setScene(scene);

            dialog.setOnShown(e -> centerOnOwner(dialog));
            dialog.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void centerOnOwner(Stage dialog) {
        Stage owner = (Stage) btnExport.getScene().getWindow();
        dialog.setX(owner.getX() + (owner.getWidth()  - dialog.getWidth())  / 2);
        dialog.setY(owner.getY() + (owner.getHeight() - dialog.getHeight()) / 2);
    }

    // ── Getters ───────────────────────────────────────────────

    public double getRotation() { return sliderRotation.getValue(); }

    public ResolutionPreset getResolution() {
        Toggle sel = resolutionGroup.getSelectedToggle();
        if (sel == btnResVertical)   return ResolutionPreset.VERTICAL_9_16;
        if (sel == btnResHorizontal) return ResolutionPreset.HORIZONTAL_16_9;
        if (sel == btnResSquare)     return ResolutionPreset.SQUARE_1_1;
        return ResolutionPreset.ORIGINAL;
    }

    public String getLetterboxColor() {
        javafx.scene.paint.Color c = colorLetterbox.getValue();
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
}
