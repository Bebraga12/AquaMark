package com.aquamark.controller;

import com.aquamark.model.VideoProject;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import java.io.File;
import java.util.List;

public class ExportDialogController {

    @FXML private Label             lblTitle;
    @FXML private TextField         fieldOutputDir;
    @FXML private ChoiceBox<String> choiceFormat;
    @FXML private ToggleGroup       qualityGroup;
    @FXML private ToggleButton      btnQualityLow;
    @FXML private ToggleButton      btnQualityMid;
    @FXML private ToggleButton      btnQualityHigh;
    @FXML private Button            btnExport;

    private List<VideoProject> projects;

    @FXML
    public void initialize() {
        choiceFormat.getItems().addAll("MP4 (H.264)", "MP4 (H.265)", "MOV");
        choiceFormat.getSelectionModel().selectFirst();

        btnExport.setDisable(true);
        fieldOutputDir.textProperty().addListener((obs, o, n) ->
            btnExport.setDisable(n == null || n.isBlank()));
    }

    public void setProjects(List<VideoProject> projects) {
        this.projects = projects;
        int n = projects.size();
        lblTitle.setText(n == 1 ? "Exportar Vídeo" : "Exportar " + n + " vídeos");
    }

    @FXML
    private void onPickOutputDir() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Pasta de Saída");
        File dir = chooser.showDialog(fieldOutputDir.getScene().getWindow());
        if (dir != null) fieldOutputDir.setText(dir.getAbsolutePath());
    }

    @FXML
    private void onExport() {
        File outputDir = new File(fieldOutputDir.getText());
        if (!outputDir.isDirectory()) {
            showError("Pasta de saída inválida.");
            return;
        }
        String quality = selectedQuality();
        String format  = selectedFormat();

        Stage dialogStage = (Stage) btnExport.getScene().getWindow();
        Stage owner       = (Stage) dialogStage.getOwner();
        dialogStage.close();

        openProgressWindow(owner, projects, outputDir, quality, format);
    }

    private void openProgressWindow(Stage owner, List<VideoProject> projects,
                                    File outputDir, String quality, String format) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/ExportProgress.fxml"));
            Parent root = loader.load();
            ExportProgressController ctrl = loader.getController();

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(owner);
            stage.initStyle(StageStyle.UNDECORATED);
            stage.setResizable(false);

            Scene scene = new Scene(root);
            scene.getStylesheets().add(
                getClass().getResource("/css/dark-theme.css").toExternalForm());
            stage.setScene(scene);
            stage.setOnShown(e -> {
                stage.sizeToScene();
                double x = owner.getX() + (owner.getWidth()  - stage.getWidth())  / 2;
                double y = owner.getY() + (owner.getHeight() - stage.getHeight()) / 2;
                // Garante que o dialog fica dentro da tela
                Rectangle2D screen = Screen.getPrimary().getVisualBounds();
                stage.setX(Math.max(0, Math.min(x, screen.getMaxX() - stage.getWidth())));
                stage.setY(Math.max(0, Math.min(y, screen.getMaxY() - stage.getHeight())));
                ctrl.startExport(projects, outputDir, quality, format);
            });
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML private void onCancel() {
        ((Stage) btnExport.getScene().getWindow()).close();
    }

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private String selectedQuality() {
        Toggle sel = qualityGroup.getSelectedToggle();
        if (sel == btnQualityHigh) return "high";
        if (sel == btnQualityLow)  return "low";
        return "medium";
    }

    private String selectedFormat() {
        String choice = choiceFormat.getValue();
        if (choice == null) return "h264";
        if (choice.contains("H.265")) return "h265";
        if (choice.contains("MOV"))   return "mov";
        return "h264";
    }
}
