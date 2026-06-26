package com.aquamark.service;

import javafx.scene.image.Image;
import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    public double getDuration(File file) throws Exception {
        Process p = ffprobe("-show_entries", "format=duration", "-of", "csv=p=0",
                            file.getAbsolutePath());
        String out = readStdout(p).trim();
        p.waitFor();
        return parseNum(out);
    }

    public double getFps(File file) throws Exception {
        Process p = ffprobe("-select_streams", "v:0",
                            "-show_entries", "stream=r_frame_rate",
                            "-of", "csv=p=0",
                            file.getAbsolutePath());
        String out = readStdout(p).trim().split("\n")[0].trim();
        p.waitFor();
        if (out.contains("/")) {
            String[] parts = out.split("/");
            double den = parseNum(parts[1]);
            return den > 0 ? parseNum(parts[0]) / den : 30.0;
        }
        return out.isEmpty() ? 30.0 : parseNum(out);
    }

    /** Aceita ponto ou vírgula como separador decimal (defesa contra locale do ffprobe). */
    private static double parseNum(String s) {
        return Double.parseDouble(s.trim().replace(',', '.'));
    }

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

    public Image extractFrame(File file, double seconds, String vfFilter) throws Exception {
        File temp = new File(TEMP_DIR, "seek_frame.jpg");
        List<String> cmd = new ArrayList<>(List.of(
            "ffmpeg", "-y", "-loglevel", "quiet",
            "-ss", String.format(Locale.US, "%.3f", Math.max(0, seconds)),
            "-i", file.getAbsolutePath()));
        if (vfFilter != null && !vfFilter.isBlank()) { cmd.add("-vf"); cmd.add(vfFilter); }
        cmd.addAll(List.of("-vframes", "1", "-q:v", "3", temp.getAbsolutePath()));
        cLocale(new ProcessBuilder(cmd).redirectErrorStream(true)).start().waitFor();
        return (temp.exists() && temp.length() > 0)
            ? new Image(temp.toURI().toString()) : null;
    }

    /**
     * Extrai o 1º frame de uma imagem/GIF como PNG (preserva transparência).
     * Usado como fallback de preview quando o decodificador do JavaFX falha (ex.: alguns GIFs).
     */
    public Image extractImageFirstFrame(File src) throws Exception {
        File temp = new File(TEMP_DIR, "wm_frame_" + System.nanoTime() + ".png");
        List<String> cmd = List.of(
            "ffmpeg", "-y", "-loglevel", "quiet",
            "-i", src.getAbsolutePath(),
            "-frames:v", "1", temp.getAbsolutePath());
        cLocale(new ProcessBuilder(cmd).redirectErrorStream(true)).start().waitFor();
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
            "-ss", String.format(Locale.US, "%.3f", Math.max(0, startTime)),
            "-i", file.getAbsolutePath()));
        if (vfFilter != null && !vfFilter.isBlank()) { cmd.add("-vf"); cmd.add(vfFilter); }
        cmd.addAll(List.of("-f", "image2pipe", "-vcodec", "mjpeg", "-q:v", "4", "pipe:1"));
        ProcessBuilder pb = cLocale(new ProcessBuilder(cmd));
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        return pb.start();
    }

    /** Inicia ffplay headless para reprodução de áudio sincronizado com o preview. */
    public Process startAudioPlayer(File file, double startTime) throws IOException {
        ProcessBuilder pb = cLocale(new ProcessBuilder(
            "ffplay", "-nodisp", "-loglevel", "quiet",
            "-ss", String.format(Locale.US, "%.3f", Math.max(0, startTime)),
            "-i", file.getAbsolutePath()));
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
        ProcessBuilder pb = cLocale(new ProcessBuilder(full));
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        return pb.start();
    }

    private static String readStdout(Process p) throws IOException {
        return new String(p.getInputStream().readAllBytes());
    }

    /** Força locale C para que ffmpeg/ffprobe usem ponto decimal (não vírgula). */
    private static ProcessBuilder cLocale(ProcessBuilder pb) {
        pb.environment().put("LC_ALL", "C");
        return pb;
    }

    // ── Preparação da marca d'água ─────────────────────────────────

    /**
     * Garante um arquivo de marca d'água que ffmpeg e JavaFX consigam ler.
     * WebP (inclusive renomeado para .gif) não é decodificável pelo ffmpeg nativo,
     * então é convertido para APNG animado (preserva alpha total) via ImageMagick.
     * Retorna o arquivo original quando já é suportado (gif/png/jpg) ou se a conversão falhar.
     */
    public static File prepareWatermark(File src) {
        if (src == null || !isWebp(src)) return src;
        File out = new File(TEMP_DIR, "wm_conv_" + System.nanoTime() + ".apng");
        return convertImage(src, out) ? out : src;
    }

    /** Detecta WebP pelo cabeçalho (RIFF....WEBP), ignorando a extensão do arquivo. */
    private static boolean isWebp(File f) {
        try (InputStream in = new FileInputStream(f)) {
            byte[] b = in.readNBytes(12);
            return b.length == 12
                && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
        } catch (IOException e) {
            return false;
        }
    }

    /** Converte via ImageMagick (magick ou convert). true se gerou o arquivo de saída. */
    private static boolean convertImage(File src, File out) {
        for (String tool : new String[]{"magick", "convert"}) {
            try {
                Process p = new ProcessBuilder(tool, src.getAbsolutePath(), out.getAbsolutePath())
                    .redirectErrorStream(true).start();
                p.getInputStream().readAllBytes();
                if (p.waitFor() == 0 && out.exists() && out.length() > 0) return true;
            } catch (Exception ignored) {
                // tenta a próxima ferramenta
            }
        }
        System.err.println("[AquaMark] Não foi possível converter WebP (ImageMagick ausente?): "
            + src.getName());
        return false;
    }
}
