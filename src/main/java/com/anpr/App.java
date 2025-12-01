package com.anpr;

import org.opencv.core.Core;
import nu.pattern.OpenCV;

/**
 * Main application class to test OpenCV setup.
 */
public class App {
    public static void main(String[] args) {
        // Loads the OpenCV native library.
        OpenCV.loadLocally();

        // Print the OpenCV version if loaded correctly.
        System.out.println("Welcome to OpenCV " + Core.VERSION);
        System.out.println("Success! OpenCV native library loaded correctly.");
    }
}
