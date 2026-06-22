package com.aquamark.model;

public enum ResolutionPreset {
    ORIGINAL("Original", -1, -1),
    VERTICAL_9_16("Vertical 9:16", 1080, 1920),
    HORIZONTAL_16_9("Horizontal 16:9", 1920, 1080),
    SQUARE_1_1("Quadrado 1:1", 1080, 1080);

    private final String label;
    private final int width;
    private final int height;

    ResolutionPreset(String label, int width, int height) {
        this.label = label;
        this.width = width;
        this.height = height;
    }

    public String getLabel() { return label; }
    public int getWidth()    { return width; }
    public int getHeight()   { return height; }

    @Override
    public String toString() { return label; }
}
