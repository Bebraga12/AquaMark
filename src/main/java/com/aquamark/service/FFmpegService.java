package com.aquamark.service;

import com.aquamark.model.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class FFmpegService {

    private volatile Process currentProcess;
    private volatile boolean canceled = false;

    public void cancel() {
        canceled = true;
        if (currentProcess != null) currentProcess.destroyForcibly();
    }

    public void export(VideoProject project, File outputFile, String quality, String format,
                       Consumer<Double> onProgress) throws IOException, InterruptedException {
        canceled = false;
        List<String> cmd = buildCommand(project, outputFile, quality, format);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true); // stderr → stdout para lermos o progresso
        Process proc = pb.start();
        currentProcess = proc;

        double start = project.getTrimRange().startSeconds();
        double end   = project.getTrimRange().endSeconds();
        double total = (end > start) ? (end - start) : 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (onProgress != null && line.contains("time=") && total > 0) {
                    double elapsed = parseTime(line);
                    if (elapsed >= 0)
                        onProgress.accept(Math.min(elapsed / total, 1.0));
                }
            }
        } catch (IOException ignored) {}

        int exit = proc.waitFor();
        currentProcess = null;
        if (!canceled && exit != 0)
            throw new IOException("FFmpeg saiu com código " + exit);
    }

    private double parseTime(String line) {
        int idx = line.indexOf("time=");
        if (idx < 0) return -1;
        String t = line.substring(idx + 5).trim().split("\\s+")[0];
        try {
            String[] p = t.split(":");
            return Double.parseDouble(p[0]) * 3600
                 + Double.parseDouble(p[1]) * 60
                 + Double.parseDouble(p[2]);
        } catch (Exception e) { return -1; }
    }

    public List<String> buildCommand(VideoProject project, File outputFile,
                                     String quality, String format) {
        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");
        cmd.add("-y");

        TrimRange trim = project.getTrimRange();
        double start = trim.startSeconds();
        double end   = trim.endSeconds();

        // -ss como opção de INPUT (fast seek) — deve vir ANTES de -i
        if (start > 0.01) {
            cmd.add("-ss");
            cmd.add(String.format("%.3f", start));
        }

        cmd.add("-i");
        cmd.add(project.getVideoFile().getAbsolutePath());

        // Watermark como segundo -i — deve vir logo após o primeiro -i,
        // NUNCA com -t entre eles (FFmpeg aplicaria o -t ao próximo input)
        WatermarkConfig wm = project.getWatermarkConfig();
        boolean hasWatermark = wm != null && wm.getFile() != null && wm.getFile().exists();
        if (hasWatermark) {
            cmd.add("-i");
            cmd.add(wm.getFile().getAbsolutePath());
        }

        boolean hasRotation   = Math.abs(project.getRotation()) > 0.5;
        boolean hasResolution = project.getResolution() != ResolutionPreset.ORIGINAL;
        boolean hasFilter     = hasRotation || hasResolution || hasWatermark;

        if (hasFilter) {
            FilterResult fr = buildFilterComplex(project, hasWatermark);
            cmd.add("-filter_complex");
            cmd.add(fr.filterComplex);
            cmd.add("-map");
            cmd.add("[" + fr.outputLabel + "]");
            cmd.add("-map");
            cmd.add("0:a?");
            addCodecArgs(cmd, format, quality);
            cmd.add("-c:a");
            cmd.add("aac");
            cmd.add("-b:a");
            cmd.add("128k");
        } else {
            addCodecArgs(cmd, format, quality);
            cmd.add("-c:a");
            cmd.add("copy");
        }

        // -t como opção de OUTPUT — deve vir APÓS todos os -i e filtros,
        // imediatamente antes do arquivo de saída
        if (end > 0.01 && end > start) {
            cmd.add("-t");
            cmd.add(String.format("%.3f", end - start));
        }

        cmd.add(outputFile.getAbsolutePath());
        return cmd;
    }

    // ── Filter chain ───────────────────────────────────────────────

    private record FilterResult(String filterComplex, String outputLabel) {}

    private FilterResult buildFilterComplex(VideoProject project, boolean hasWatermark) {
        List<String> segments = new ArrayList<>();
        String cur = "0:v";
        int n = 0;

        // Rotation
        double rot = project.getRotation();
        if (Math.abs(rot) > 0.5) {
            String out = "v" + (++n);
            if (Math.abs(rot - 90)  < 1) {
                segments.add(String.format("[%s]transpose=1[%s]", cur, out));
            } else if (Math.abs(rot + 90) < 1) {
                segments.add(String.format("[%s]transpose=2[%s]", cur, out));
            } else if (Math.abs(Math.abs(rot) - 180) < 1) {
                segments.add(String.format("[%s]transpose=1,transpose=1[%s]", cur, out));
            } else {
                segments.add(String.format("[%s]rotate=%.5f*PI/180:fillcolor=black[%s]", cur, rot, out));
            }
            cur = out;
        }

        // Resolution + letterbox
        ResolutionPreset res = project.getResolution();
        if (res != ResolutionPreset.ORIGINAL) {
            String out = "v" + (++n);
            String color = project.getLetterboxColor().replace("#", "0x");
            int W = res.getWidth(), H = res.getHeight();
            segments.add(String.format(
                "[%s]scale=%d:%d:force_original_aspect_ratio=decrease,"
                + "pad=%d:%d:(ow-iw)/2:(oh-ih)/2:color=%s[%s]",
                cur, W, H, W, H, color, out));
            cur = out;
        }

        // Watermark overlay
        if (hasWatermark) {
            WatermarkConfig wm = project.getWatermarkConfig();
            double sizePct  = wm.getSizePercent();
            double wmX      = wm.getPositionX();
            double wmY      = wm.getPositionY();
            double opacity  = wm.getOpacityPercent() / 100.0;

            String wmIn = "1:v";

            if (opacity < 0.99) {
                String wmAlpha = "wma" + (++n);
                segments.add(String.format(
                    "[%s]format=rgba,colorchannelmixer=aa=%.4f[%s]",
                    wmIn, opacity, wmAlpha));
                wmIn = wmAlpha;
            }

            String wmSized  = "wms" + (++n);
            String out      = "v"   + (++n);

            // Dimensão de referência = menor lado do vídeo APÓS rotação/resolução.
            // Calculada em pixels no Java (scale2ref tem semântica de variáveis
            // imprevisível e distorcia a marca d'água).
            int refDim = referenceDimension(project);
            int wmWidth = Math.max(2, (int) Math.round(refDim * sizePct / 100.0));

            // scale com largura absoluta e h=-1 → preserva o aspect ratio da marca d'água
            segments.add(String.format("[%s]scale=%d:-1[%s]", wmIn, wmWidth, wmSized));

            // x e y como opções nomeadas separadas — permite que a marca d'água
            // ultrapasse os limites do vídeo (FFmpeg faz o clip automaticamente)
            segments.add(String.format(
                "[%s][%s]overlay=x=(main_w-overlay_w)*%.6f:y=(main_h-overlay_h)*%.6f[%s]",
                cur, wmSized, wmX, wmY, out));
            cur = out;
        }

        return new FilterResult(String.join(";", segments), cur);
    }

    /**
     * Menor lado (em pixels) do vídeo APÓS rotação e mudança de resolução.
     * É a base para dimensionar a marca d'água de forma proporcional.
     */
    private int referenceDimension(VideoProject project) {
        // Se há preset de resolução, o vídeo final tem exatamente essas dimensões
        ResolutionPreset res = project.getResolution();
        if (res != ResolutionPreset.ORIGINAL) {
            return Math.min(res.getWidth(), res.getHeight());
        }

        // Caso ORIGINAL: usa as dimensões reais do vídeo (probe)
        int[] dim = probeDimensions(project.getVideoFile());
        int w = dim[0], h = dim[1];

        // Rotação de 90/270 troca largura e altura — mas min() é simétrico,
        // então não precisa tratar o swap aqui.
        return Math.max(2, Math.min(w, h));
    }

    /** Lê largura/altura do vídeo via ffprobe. Fallback 1080x1920 se falhar. */
    private int[] probeDimensions(File video) {
        try {
            Process p = new ProcessBuilder(
                "ffprobe", "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=width,height",
                "-of", "csv=p=0:s=x",
                video.getAbsolutePath()
            ).redirectErrorStream(true).start();

            String line;
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream()))) {
                line = r.readLine();
            }
            p.waitFor();

            if (line != null && line.contains("x")) {
                String[] parts = line.trim().split("x");
                int w = Integer.parseInt(parts[0].trim());
                int h = Integer.parseInt(parts[1].trim());
                if (w > 0 && h > 0) return new int[]{w, h};
            }
        } catch (Exception e) {
            System.err.println("[AquaMark] probeDimensions falhou: " + e.getMessage());
        }
        return new int[]{1080, 1920};
    }

    // ── Codec / quality ────────────────────────────────────────────

    private void addCodecArgs(List<String> cmd, String format, String quality) {
        String codec = switch (format) {
            case "h265" -> "libx265";
            default     -> "libx264";
        };
        int crf = switch (quality) {
            case "high" -> 18;
            case "low"  -> 28;
            default     -> 23;
        };
        cmd.add("-c:v");    cmd.add(codec);
        cmd.add("-crf");    cmd.add(String.valueOf(crf));
        cmd.add("-preset"); cmd.add("medium");
    }

    public static String extensionFor(String format) {
        return "mov".equals(format) ? "mov" : "mp4";
    }
}
