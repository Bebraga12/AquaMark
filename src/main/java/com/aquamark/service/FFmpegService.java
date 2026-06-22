package com.aquamark.service;

import com.aquamark.model.VideoProject;
import java.io.File;

public class FFmpegService {

    public void export(VideoProject project, File outputFile) {
        // TODO: implementar — montar e executar comando FFmpeg via ProcessBuilder
    }

    public String buildCommand(VideoProject project, File outputFile) {
        // TODO: implementar — retornar string do comando ffmpeg
        return "";
    }
}
