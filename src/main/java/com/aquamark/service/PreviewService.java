package com.aquamark.service;

import javafx.scene.image.Image;
import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class PreviewService {

    private static final File TEMP_DIR;

    static {
        try {
            TEMP_DIR = Files.createTempDirectory("aquamark_preview_").toFile();
            TEMP_DIR.deleteOnExit();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Duração total em segundos via ffprobe. */
    public double getDuration(File file) throws Exception {
        Process p = ffprobe("-show_entries", "format=duration", "-of", "csv=p=0",
                            file.getAbsolutePath());
        String out = readStdout(p).trim();
        p.waitFor();
        return Double.parseDouble(out);
    }

    /** FPS real do stream de vídeo. */
    public double getFps(File file) throws Exception {
        Process p = ffprobe("-select_streams", "v:0",
                            "-show_entries", "stream=r_frame_rate",
                            "-of", "csv=p=0",
                            file.getAbsolutePath());
        String out = readStdout(p).trim().split("\n")[0].trim();
        p.waitFor();
        if (out.contains("/")) {
            String[] parts = out.split("/");
            double den = Double.parseDouble(parts[1]);
            return den > 0 ? Double.parseDouble(parts[0]) / den : 30.0;
        }
        return out.isEmpty() ? 30.0 : Double.parseDouble(out);
    }

    /** Largura e altura do stream de vídeo em pixels — int[]{w, h}. */
    public int[] getVideoSize(File file) throws Exception {
        Process p = ffprobe("-select_streams", "v:0",
                            "-show_entries", "stream=width,height",
                            "-of", "csv=p=0",
                            file.getAbsolutePath());
        String out = readStdout(p).trim().split("\n")[0].trim();
        p.waitFor();
        String[] parts = out.split(",");
        return new int[]{ Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) };
    }

    /** Frame estático em um instante — usado para seek e primeiro frame. */
    public Image extractFrame(File file, double seconds, String vfFilter) throws Exception {
        File temp = new File(TEMP_DIR, "seek_frame.jpg");
        List<String> cmd = new ArrayList<>(List.of(
            "ffmpeg", "-y", "-loglevel", "quiet",
            "-ss", String.format("%.3f", Math.max(0, seconds)),
            "-i", file.getAbsolutePath()));
        if (vfFilter != null && !vfFilter.isBlank()) { cmd.add("-vf"); cmd.add(vfFilter); }
        cmd.addAll(List.of("-vframes", "1", "-q:v", "3", temp.getAbsolutePath()));
        new ProcessBuilder(cmd).redirectErrorStream(true).start().waitFor();
        return (temp.exists() && temp.length() > 0)
            ? new Image(temp.toURI().toString()) : null;
    }

    /**
     * Pipe MJPEG com filtro de vídeo opcional (rotação, resolução).
     * O caller é responsável por fechar o processo.
     */
    public Process startPipe(File file, double startTime, String vfFilter) throws IOException {
        List<String> cmd = new ArrayList<>(List.of(
            "ffmpeg", "-loglevel", "quiet",
            "-re",
            "-ss", String.format("%.3f", Math.max(0, startTime)),
            "-i", file.getAbsolutePath()));
        if (vfFilter != null && !vfFilter.isBlank()) { cmd.add("-vf"); cmd.add(vfFilter); }
        cmd.addAll(List.of("-f", "image2pipe", "-vcodec", "mjpeg", "-q:v", "4", "pipe:1"));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        return pb.start();
    }

    /** Inicia ffplay headless para reprodução de áudio sincronizado com o preview. */
    public Process startAudioPlayer(File file, double startTime) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
            "ffplay", "-nodisp", "-loglevel", "quiet",
            "-ss", String.format("%.3f", Math.max(0, startTime)),
            "-i", file.getAbsolutePath());
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        return pb.start();
    }

    // ── helpers ───────────────────────────────────────────────────

    private static Process ffprobe(String... args) throws IOException {
        String[] cmd = new String[args.length + 2];
        cmd[0] = "ffprobe"; cmd[1] = "-v"; // filled below
        // build: ffprobe -v quiet <args>
        String[] full = new String[args.length + 3];
        full[0] = "ffprobe"; full[1] = "-v"; full[2] = "quiet";
        System.arraycopy(args, 0, full, 3, args.length);
        ProcessBuilder pb = new ProcessBuilder(full);
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        return pb.start();
    }

    private static String readStdout(Process p) throws IOException {
        return new String(p.getInputStream().readAllBytes());
    }
}
