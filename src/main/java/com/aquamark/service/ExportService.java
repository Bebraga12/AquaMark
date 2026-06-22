package com.aquamark.service;

import com.aquamark.model.VideoProject;
import java.io.File;
import java.util.List;

public class ExportService {
    private final FFmpegService ffmpegService = new FFmpegService();

    public void exportSingle(VideoProject project, File outputDir) {
        // TODO: implementar exportacao individual
    }

    public void exportAll(List<VideoProject> projects, File outputDir) {
        // TODO: implementar exportacao em lote
    }
}
