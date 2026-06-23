package com.aquamark.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import java.io.File;

public class ExportDialogController {

    @FXML private Label      lblTitle;
    @FXML private TextField  fieldOutputDir;
    @FXML private ChoiceBox<String> choiceFormat;
    @FXML private ToggleGroup qualityGroup;
    @FXML private Button     btnExport;

    private boolean exportAll;
    private int     videoCount = 1;

    @FXML
    public void initialize() {
        choiceFormat.getItems().addAll("MP4 (H.264)", "MP4 (H.265)", "MOV");
        choiceFormat.getSelectionModel().selectFirst();

        btnExport.disableProperty().bind(
            fieldOutputDir.textProperty().isEmpty()
        );
    }

    public void setExportAll(boolean exportAll, int count) {
        this.exportAll  = exportAll;
        this.videoCount = count;
        if (exportAll) {
            lblTitle.setText("Exportar " + count + (count == 1 ? " vídeo" : " vídeos"));
        } else {
            lblTitle.setText("Exportar Vídeo");
        }
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
        // TODO: coletar configurações e chamar ExportService
        close();
    }

    @FXML
    private void onCancel() {
        close();
    }

    private void close() {
        ((Stage) btnExport.getScene().getWindow()).close();
    }
}
