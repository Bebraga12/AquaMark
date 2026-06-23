package com.aquamark.controller;

import com.aquamark.model.VideoProject;
import com.aquamark.service.ExportService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
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
    private final ExportService exportService = new ExportService();

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
        if (projects == null || projects.isEmpty()) {
            showError("Nenhum vídeo para exportar.");
            return;
        }

        String quality = selectedQuality();
        String format  = selectedFormat();

        btnExport.setDisable(true);
        btnExport.setText("Exportando…");

        Thread t = new Thread(() -> {
            try {
                exportService.exportAll(projects, outputDir, quality, format);
                Platform.runLater(() -> {
                    close();
                    int n = projects.size();
                    Alert a = new Alert(Alert.AlertType.INFORMATION);
                    a.setTitle("Exportação concluída");
                    a.setHeaderText(null);
                    a.setContentText(n == 1
                        ? "Vídeo exportado com sucesso!"
                        : n + " vídeos exportados com sucesso!");
                    a.showAndWait();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    btnExport.setDisable(false);
                    btnExport.setText("Exportar");
                    showError("Erro na exportação:\n" + ex.getMessage());
                });
            }
        }, "aquamark-export");
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void onCancel() { close(); }

    private void close() {
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
