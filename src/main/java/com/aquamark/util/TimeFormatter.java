package com.aquamark.util;

public class TimeFormatter {
    public static String format(double seconds) {
        int s = Math.max(0, (int) seconds);
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    public static String formatMillis(long millis) {
        return format(millis / 1000.0);
    }
}
