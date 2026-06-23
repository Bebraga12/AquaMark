package com.aquamark.service;

import com.aquamark.model.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FFmpegService {

    public void export(VideoProject project, File outputFile, String quality, String format)
            throws IOException, InterruptedException {
        List<String> cmd = buildCommand(project, outputFile, quality, format);
        System.err.println("[FFmpeg] " + String.join(" ", cmd));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        Process proc = pb.start();
        int exit = proc.waitFor();
        if (exit != 0) throw new IOException("FFmpeg exited with code " + exit);
    }

    public List<String> buildCommand(VideoProject project, File outputFile,
                                     String quality, String format) {
        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");
        cmd.add("-y");

        TrimRange trim = project.getTrimRange();
        double start = trim.startSeconds();
        double end   = trim.endSeconds();

        if (start > 0.01) {
            cmd.add("-ss");
            cmd.add(String.format("%.3f", start));
        }

        cmd.add("-i");
        cmd.add(project.getVideoFile().getAbsolutePath());

        if (end > 0.01 && end > start) {
            cmd.add("-t");
            cmd.add(String.format("%.3f", end - start));
        }

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
            String mainRef  = "mr"  + n;
            String out      = "v"   + (++n);

            // scale2ref: first input = stream to scale, second = reference
            // iw/ih in expression refer to the reference (main video) dimensions
            segments.add(String.format(
                "[%s][%s]scale2ref='min(iw\\,ih)*%.4f/100':-1[%s][%s]",
                wmIn, cur, sizePct, wmSized, mainRef));
            cur = mainRef;

            segments.add(String.format(
                "[%s][%s]overlay='(main_w-overlay_w)*%.6f:(main_h-overlay_h)*%.6f':format=auto[%s]",
                cur, wmSized, wmX, wmY, out));
            cur = out;
        }

        return new FilterResult(String.join(";", segments), cur);
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
