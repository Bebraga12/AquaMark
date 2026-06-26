package com.aquamark.model;

import java.io.File;

public class VideoProject {
    private File videoFile;
    private WatermarkConfig watermarkConfig;
    private ResolutionPreset resolution;
    private double rotation;
    private TrimRange trimRange;
    private String letterboxColor;

    // Posição da marca d'água individual deste vídeo.
    // Quando false, o vídeo usa a posição global compartilhada.
    // Quando individual, também guarda tamanho e opacidade próprios deste vídeo.
    private boolean watermarkPositionIndividual;
    private double  watermarkX;
    private double  watermarkY;
    private double  watermarkSize;
    private double  watermarkOpacity;

    public VideoProject(File videoFile) {
        this.videoFile = videoFile;
        this.rotation = 0;
        this.resolution = ResolutionPreset.ORIGINAL;
        this.trimRange = new TrimRange(0, -1);
        this.letterboxColor = "#000000";
        this.watermarkPositionIndividual = false;
        this.watermarkX = 0.95;
        this.watermarkY = 0.05;
        this.watermarkSize = 20;
        this.watermarkOpacity = 100;
    }

    public File getVideoFile()               { return videoFile; }
    public WatermarkConfig getWatermarkConfig() { return watermarkConfig; }
    public ResolutionPreset getResolution()  { return resolution; }
    public double getRotation()              { return rotation; }
    public TrimRange getTrimRange()          { return trimRange; }
    public String getLetterboxColor()        { return letterboxColor; }
    public boolean isWatermarkPositionIndividual() { return watermarkPositionIndividual; }
    public double getWatermarkX()            { return watermarkX; }
    public double getWatermarkY()            { return watermarkY; }
    public double getWatermarkSize()         { return watermarkSize; }
    public double getWatermarkOpacity()      { return watermarkOpacity; }

    public void setVideoFile(File videoFile)               { this.videoFile = videoFile; }
    public void setWatermarkConfig(WatermarkConfig wc)     { this.watermarkConfig = wc; }
    public void setResolution(ResolutionPreset resolution) { this.resolution = resolution; }
    public void setRotation(double rotation)               { this.rotation = rotation; }
    public void setTrimRange(TrimRange trimRange)          { this.trimRange = trimRange; }
    public void setLetterboxColor(String letterboxColor)   { this.letterboxColor = letterboxColor; }
    public void setWatermarkPositionIndividual(boolean v)  { this.watermarkPositionIndividual = v; }
    public void setWatermarkX(double x)                    { this.watermarkX = x; }
    public void setWatermarkY(double y)                    { this.watermarkY = y; }
    public void setWatermarkSize(double s)                 { this.watermarkSize = s; }
    public void setWatermarkOpacity(double o)              { this.watermarkOpacity = o; }
}
