package com.anpr;

/**
 * A simple data class to hold the results of processing a single detection.
 */
public class ProcessResult {
    public final int x1, y1, x2, y2;
    public final String text;

    public ProcessResult(int x1, int y1, int x2, int y2, String text) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.text = text;
    }

    public String getBoxId() {
        int factor = 20;
        return (x1 / factor) + "_" + (y1 / factor) + "_" + (x2 / factor) + "_" + (y2 / factor);
    }
}
