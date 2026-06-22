package com.aquamark.model;

import java.io.File;

public class WatermarkConfig {
    private File   file;
    private double positionX;    // 0.0 (esquerda) – 1.0 (direita)
    private double positionY;    // 0.0 (topo)     – 1.0 (baixo)
    private int    sizePercent;    // 5-80
    private int    opacityPercent; // 0-100

    public WatermarkConfig() {
        this.positionX = 0.95;
        this.positionY = 0.05;
        this.sizePercent = 20;
        this.opacityPercent = 100;
    }

    public File   getFile()            { return file; }
    public double getPositionX()       { return positionX; }
    public double getPositionY()       { return positionY; }
    public int    getSizePercent()     { return sizePercent; }
    public int    getOpacityPercent()  { return opacityPercent; }

    public void setFile(File file)                     { this.file = file; }
    public void setPositionX(double x)                 { this.positionX = x; }
    public void setPositionY(double y)                 { this.positionY = y; }
    public void setSizePercent(int sizePercent)        { this.sizePercent = sizePercent; }
    public void setOpacityPercent(int pct)             { this.opacityPercent = pct; }
}
