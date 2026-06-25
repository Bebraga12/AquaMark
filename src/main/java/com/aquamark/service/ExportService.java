package com.aquamark.service;

import com.aquamark.model.VideoProject;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

public class ExportService {
    private final FFmpegService ffmpeg = new FFmpegService();

    public void exportSingle(VideoProject project, File outputDir, String quality, String format,
                             Consumer<Double> onProgress) throws IOException, InterruptedException {
        File out = outputFile(project.getVideoFile(), outputDir, format);
        ffmpeg.export(project, out, quality, format, onProgress);
    }

    public void exportAll(List<VideoProject> projects, File outputDir, String quality, String format)
            throws IOException, InterruptedException {
        for (VideoProject p : projects)
            exportSingle(p, outputDir, quality, format, null);
    }

    public void cancel() { ffmpeg.cancel(); }

    private File outputFile(File input, File dir, String format) {
        String name = input.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return new File(dir, base + "_aquamark." + FFmpegService.extensionFor(format));
    }
}
