package com.aquamark.controller;

import com.aquamark.model.VideoProject;
import com.aquamark.service.ExportService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import java.io.File;
import java.util.List;

public class ExportProgressController {

    @FXML private Label       lblTitle;
    @FXML private Label       lblFile;
    @FXML private ProgressBar progressBar;
    @FXML private Label       lblPercent;
    @FXML private Label       lblStatus;
    @FXML private Button      btnCancel;

    private final ExportService exportService = new ExportService();
    private volatile boolean    canceled      = false;

    public void startExport(List<VideoProject> projects, File outputDir,
                            String quality, String format) {
        int n = projects.size();
        lblTitle.setText(n == 1 ? "Exportando vídeo..." : "Exportando " + n + " vídeos...");

        Thread t = new Thread(() -> runExport(projects, outputDir, quality, format, n), "aquamark-export");
        t.setDaemon(true);
        t.start();
    }

    private void runExport(List<VideoProject> projects, File outputDir,
                           String quality, String format, int total) {
        for (int i = 0; i < total && !canceled; i++) {
            final int idx = i;
            String filename = projects.get(i).getVideoFile().getName();
            Platform.runLater(() -> {
                lblFile.setText(filename);
                lblStatus.setText("Vídeo " + (idx + 1) + " de " + total);
            });

            try {
                final int fi = i;
                exportService.exportSingle(projects.get(i), outputDir, quality, format, progress -> {
                    if (canceled) return;
                    double overall = (fi + progress) / total;
                    Platform.runLater(() -> {
                        progressBar.setProgress(overall);
                        lblPercent.setText(String.format("%.0f%%", overall * 100));
                    });
                });
            } catch (Exception e) {
                if (!canceled) {
                    Platform.runLater(() -> {
                        lblTitle.setText("Erro na exportação");
                        lblStatus.setText(e.getMessage());
                        progressBar.setStyle("-fx-accent: #e06c75;");
                        btnCancel.setText("Fechar");
                    });
                }
                return;
            }
        }

        if (!canceled) {
            Platform.runLater(() -> {
                progressBar.setProgress(1.0);
                lblPercent.setText("100%");
                lblTitle.setText("Exportação concluída!");
                lblStatus.setText(total == 1 ? "Vídeo exportado com sucesso."
                                             : total + " vídeos exportados com sucesso.");
                btnCancel.setText("Fechar");
            });
        }
    }

    @FXML private void onCancel() {
        canceled = true;
        exportService.cancel();
        ((Stage) btnCancel.getScene().getWindow()).close();
    }
}
