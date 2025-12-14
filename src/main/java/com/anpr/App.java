package com.anpr;

import javax.swing.SwingUtilities;

import nu.pattern.OpenCV;

/**
 * Main entry point for the ANPR application.
 * This class is responsible for loading the native OpenCV library
 * and launching the Swing user interface.
 */
public class App {

    public static void main(String[] args) {
        // Load the native OpenCV library
        OpenCV.loadLocally();
        // Launch the UI on the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            AnprUI ui = new AnprUI();
            ui.setVisible(true);
        });
    }
}
