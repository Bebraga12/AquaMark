# AquaMark

> Desktop video editor focused on watermarking — add image or GIF overlays, trim, rotate, adjust resolution, and batch-export via FFmpeg.

AquaMark is a lightweight, open-source desktop application built with Java and JavaFX. It provides a professional, editor-style interface for applying watermarks to one or multiple videos and exporting them in a single pass.

---

## Features

- **Watermark overlay** — supports static images and animated GIFs, with configurable position, size (%), and opacity (%)
- **Trim** — set a start/end cut point per video using a visual timeline
- **Rotation** — rotate the video output
- **Resolution presets** — export as Original, 16:9, 9:16, or 1:1 with automatic letterbox (configurable color)
- **Batch export** — process the entire video list in one click
- **Dark UI** — neutral gray palette + blue accent

---

## Requirements

| Tool | Version |
|---|---|
| Java (JDK) | 21+ |
| Maven | 3.8+ |
| FFmpeg | any recent stable (must be on `$PATH`) |
| Node.js / npx | any (only needed to compile SCSS on first build) |

> FFmpeg is used at runtime via `ProcessBuilder`. It is **not** bundled — install it separately and make sure `ffmpeg` is accessible from your terminal.

### Installing FFmpeg

```bash
# Ubuntu / Debian
sudo apt install ffmpeg

# Arch Linux
sudo pacman -S ffmpeg

# macOS (Homebrew)
brew install ffmpeg

# Windows — download from https://ffmpeg.org/download.html and add to PATH
```

---

## Getting started

### 1. Clone the repository

```bash
git clone https://github.com/Bebraga12/AquaMark
cd aqua-mark
```

### 2. Run in development mode

```bash
mvn javafx:run
```

Maven will compile the SCSS to CSS automatically before launching the app.

### 3. Build a JAR

```bash
mvn clean package
```

The output lands in `target/`. Note that JavaFX modules must still be on the module path at runtime — use `mvn javafx:run` for the simplest experience during development.

---

## Project structure

```
src/main/java/com/aquamark/
├── AquaMarkApp.java              # Entry point (extends Application)
├── controller/                   # FXML controllers — UI event handling only
│   ├── MainController.java
│   ├── VideoListController.java
│   ├── EditorController.java
│   ├── TimelineController.java
│   ├── ExportDialogController.java
│   └── ExportProgressController.java
├── model/                        # Plain data objects — no JavaFX imports
│   ├── VideoProject.java
│   ├── WatermarkConfig.java
│   ├── ResolutionPreset.java     
│   └── TrimRange.java            
├── service/                      # Business logic — no JavaFX imports
│   ├── FFmpegService.java        
│   ├── ExportService.java       
│   └── PreviewService.java
└── util/
    └── TimeFormatter.java        # seconds → "mm:ss"

src/main/resources/
├── fxml/                         # FXML layout files
└── css/                          # dark-theme.css (compiled from dark-theme.scss)
```

The architecture follows a strict MVC split:

- **Controller** — only handles UI events and delegates to services
- **Model** — pure POJOs/records, zero JavaFX dependency
- **Service** — all business logic, zero JavaFX dependency
- **Util** — stateless helpers

---

## Tech 


| Layer | Technology |
|---|---|
| Language | Java 21 |
| UI framework | JavaFX 21 (controls, fxml, media, swing) |
| Build tool | Maven 3 |
| Styling | SCSS → CSS (compiled via `npx sass`) |
| Video processing | FFmpeg (via `ProcessBuilder`) |

---

## Contributing

Contributions are welcome. A few ground rules:

1. **Open an issue first** for any non-trivial change so the approach can be discussed before you invest time coding.
2. Keep the architecture separation: controllers touch JavaFX, models and services do not.
3. Do not add new Maven dependencies without discussing in an issue — the dependency surface is intentionally small.
4. Match the existing code style (no framework-generated boilerplate, descriptive `fx:id` names in camelCase).
5. FFmpeg commands go through `FFmpegService` — no ad-hoc `Runtime.exec` calls elsewhere.

### Running locally for development

```bash
# just run
mvn javafx:run

# recompile SCSS only (if you change dark-theme.scss)
npx sass src/main/resources/css/dark-theme.scss src/main/resources/css/dark-theme.css --no-source-map
```

---

## License

[GNU General Public License v3.0](LICENSE) — you are free to use, modify, and distribute this software, but any derivative work must also be released under the GPL v3 with its source code available.
