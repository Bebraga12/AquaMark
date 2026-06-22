package com.aquamark.model;

import java.io.File;

public class VideoProject {
    private File videoFile;
    private WatermarkConfig watermarkConfig;
    private ResolutionPreset resolution;
    private double rotation;
    private TrimRange trimRange;
    private String letterboxColor;

    public VideoProject(File videoFile) {
        this.videoFile = videoFile;
        this.rotation = 0;
        this.resolution = ResolutionPreset.ORIGINAL;
        this.trimRange = new TrimRange(0, -1);
        this.letterboxColor = "#000000";
    }

    public File getVideoFile()               { return videoFile; }
    public WatermarkConfig getWatermarkConfig() { return watermarkConfig; }
    public ResolutionPreset getResolution()  { return resolution; }
    public double getRotation()              { return rotation; }
    public TrimRange getTrimRange()          { return trimRange; }
    public String getLetterboxColor()        { return letterboxColor; }

    public void setVideoFile(File videoFile)               { this.videoFile = videoFile; }
    public void setWatermarkConfig(WatermarkConfig wc)     { this.watermarkConfig = wc; }
    public void setResolution(ResolutionPreset resolution) { this.resolution = resolution; }
    public void setRotation(double rotation)               { this.rotation = rotation; }
    public void setTrimRange(TrimRange trimRange)          { this.trimRange = trimRange; }
    public void setLetterboxColor(String letterboxColor)   { this.letterboxColor = letterboxColor; }
}
