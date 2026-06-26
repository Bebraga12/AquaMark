package com.aquamark.service;

import com.aquamark.model.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        pb.environment().put("LC_ALL", "C"); // ffmpeg usa ponto decimal no progresso (time=)
        pb.redirectErrorStream(true); // stderr → stdout ler o progresso
        Process proc = pb.start();
        currentProcess = proc;

        double start = project.getTrimRange().startSeconds();
        double end   = project.getTrimRange().endSeconds();
        double total = (end > start) ? (end - start) : 0;

        // Mantém as últimas linhas da saída do ffmpeg para diagnosticar falhas
        java.util.ArrayDeque<String> tail = new java.util.ArrayDeque<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (tail.size() >= 12) tail.removeFirst();
                tail.addLast(line);
                if (onProgress != null && line.contains("time=") && total > 0) {
                    double elapsed = parseTime(line);
                    if (elapsed >= 0)
                        onProgress.accept(Math.min(elapsed / total, 1.0));
                }
            }
        } catch (IOException ignored) {}

        int exit = proc.waitFor();
        currentProcess = null;
        if (!canceled && exit != 0) {
            String detail = extractError(tail);
            throw new IOException("FFmpeg falhou (código " + exit + ")"
                + (detail.isEmpty() ? "" : ": " + detail));
        }
    }

    /** Escolhe a linha mais informativa da cauda da saída do ffmpeg (a real causa do erro). */
    private String extractError(java.util.Deque<String> tail) {
        String last = "";
        for (String l : tail) {
            String t = l.trim();
            if (t.isEmpty() || t.startsWith("frame=") || t.startsWith("size=")) continue;
            // Prioriza linhas que claramente descrevem um erro
            String low = t.toLowerCase();
            if (low.contains("error") || low.contains("invalid") || low.contains("no such")
                || low.contains("unable") || low.contains("failed") || low.contains("could not"))
                return t;
            last = t;
        }
        return last;
    }

    private double parseTime(String line) {
        int idx = line.indexOf("time=");
        if (idx < 0) return -1;
        String t = line.substring(idx + 5).trim().split("\\s+")[0].replace(',', '.');
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
            cmd.add(String.format(Locale.US, "%.3f", start));
        }

        cmd.add("-i");
        cmd.add(project.getVideoFile().getAbsolutePath());

        // Watermark como segundo -i — deve vir logo após o primeiro -i,
        // NUNCA com -t entre eles (FFmpeg aplicaria o -t ao próximo input)
        WatermarkConfig wm = project.getWatermarkConfig();
        boolean hasWatermark = wm != null && wm.getFile() != null && wm.getFile().exists();
        if (hasWatermark) {
            // Marca animada (GIF/APNG/WebP) precisa de -stream_loop -1 para repetir até o fim do vídeo
            if (isAnimatedWatermark(wm.getFile())) {
                cmd.add("-stream_loop"); cmd.add("-1");
            }
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
            // Sem filtros, mas o vídeo é re-encodado: garante dimensões pares (libx264)
            cmd.add("-vf");
            cmd.add("scale=trunc(iw/2)*2:trunc(ih/2)*2");
            addCodecArgs(cmd, format, quality);
            cmd.add("-c:a");
            cmd.add("copy");
        }

        // -t como opção de OUTPUT — deve vir APÓS todos os -i e filtros,
        // imediatamente antes do arquivo de saída
        if (end > 0.01 && end > start) {
            cmd.add("-t");
            cmd.add(String.format(Locale.US, "%.3f", end - start));
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
                // ow/oh = rotw/roth: expande o frame para conter o vídeo girado (sem cortar)
                String a = String.format(java.util.Locale.US, "%.5f*PI/180", rot);
                segments.add(String.format(
                    "[%s]rotate=%s:ow=rotw(%s):oh=roth(%s):fillcolor=black[%s]",
                    cur, a, a, a, out));
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

            // Marca animada: normaliza timestamps e garante canal alpha antes de qualquer filtro
            if (isAnimatedWatermark(wm.getFile())) {
                String animNorm = "wmg" + (++n);
                segments.add(String.format("[%s]format=rgba,setpts=PTS-STARTPTS[%s]", wmIn, animNorm));
                wmIn = animNorm;
            }

            if (opacity < 0.99) {
                String wmAlpha = "wma" + (++n);
                segments.add(String.format(Locale.US,
                    "[%s]format=rgba,colorchannelmixer=aa=%.4f[%s]",
                    wmIn, opacity, wmAlpha));
                wmIn = wmAlpha;
            }

            String wmSized  = "wms" + (++n);
            String out      = "v"   + (++n);

            // imprevisível e distorcia a marca d'água).
            int refDim = referenceDimension(project);
            int wmWidth = Math.max(2, (int) Math.round(refDim * sizePct / 100.0));

            // scale com largura absoluta e h=-1 → preserva o aspect ratio da marca d'água
            segments.add(String.format("[%s]scale=%d:-1[%s]", wmIn, wmWidth, wmSized));

            // shortest=1: garante que o overlay para quando o vídeo principal acaba
            // (necessário especialmente quando a marca d'água é um GIF em loop infinito)
            segments.add(String.format(Locale.US,
                "[%s][%s]overlay=x=(main_w-overlay_w)*%.6f:y=(main_h-overlay_h)*%.6f:shortest=1[%s]",
                cur, wmSized, wmX, wmY, out));
            cur = out;
        }

        // Guarda final de PARIDADE: libx264/yuv420p exige largura e altura divisíveis por 2.
        // rotate (rotw/roth) e scale+pad de resolução podem produzir dimensões ímpares
        // (ex.: 1920x1081) — isso causava "height not divisible by 2" / erro 187 na exportação.
        String even = "vf" + (++n);
        segments.add(String.format("[%s]scale=trunc(iw/2)*2:trunc(ih/2)*2[%s]", cur, even));
        cur = even;

        return new FilterResult(String.join(";", segments), cur);
    }

    /**
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
            ProcessBuilder pb = new ProcessBuilder(
                "ffprobe", "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=width,height",
                "-of", "csv=p=0:s=x",
                video.getAbsolutePath()
            ).redirectErrorStream(true);
            pb.environment().put("LC_ALL", "C");
            Process p = pb.start();

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

    private static boolean isAnimatedWatermark(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".gif") || name.endsWith(".apng") || name.endsWith(".webp");
    }
}
