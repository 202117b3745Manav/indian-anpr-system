package com.anpr;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;

public class AnprUI extends JFrame {

    private final VideoPanel videoPanel;
    private final JButton captureButton;
    private final JLabel statusLabel;

    private VideoCapture videoCapture;
    private volatile Mat currentFrame;
    private volatile boolean isCameraActive = false;
    private Thread videoThread;

    private final ImageProcessor imageProcessor;
    private final ExcelExporter excelExporter;

    public AnprUI() {
        // 1. Initialize Core Components
        this.imageProcessor = new ImageProcessor();
        this.excelExporter = new ExcelExporter(ConfigLoader.getProperty("log.filename"));

        // Ensure output directories exist
        new File(ConfigLoader.getProperty("output.input_folder")).mkdirs();
        new File(ConfigLoader.getProperty("output.output_folder")).mkdirs();

        // 2. Setup UI Components
        setTitle("Indian ANPR System");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        videoPanel = new VideoPanel();
        videoPanel.setPreferredSize(new Dimension(800, 600));
        add(videoPanel, BorderLayout.CENTER);

        captureButton = new JButton("Capture & Process");
        captureButton.addActionListener(e -> onCapture());
        add(captureButton, BorderLayout.SOUTH);

        statusLabel = new JLabel("Ready. Please connect to the camera.");
        statusLabel.setBorder(BorderFactory.createLoweredBevelBorder());
        add(statusLabel, BorderLayout.NORTH);

        pack();
        setLocationRelativeTo(null); // Center the window

        // 3. Add Shutdown Hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stopCamera();
            excelExporter.close();
        }));

        // 4. Start Camera
        startCamera();
    }

    private void startCamera() {
        String cameraUrl = ConfigLoader.getProperty("camera.url");
        videoCapture = new VideoCapture(cameraUrl);

        if (!videoCapture.isOpened()) {
            statusLabel.setText("Error: Could not connect to camera at " + cameraUrl);
            return;
        }

        isCameraActive = true;
        videoThread = new Thread(this::videoLoop);
        videoThread.setDaemon(true);
        videoThread.start();
        statusLabel.setText("Camera connected. Ready to capture.");
    }

    private void stopCamera() {
        isCameraActive = false;
        if (videoThread != null && videoThread.isAlive()) {
            try {
                videoThread.join(1000); // Wait for the thread to die
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (videoCapture != null && videoCapture.isOpened()) {
            videoCapture.release();
        }
    }

    private void videoLoop() {
        Mat frame = new Mat();
        while (isCameraActive && videoCapture.read(frame)) {
            currentFrame = frame.clone();
            videoPanel.repaint();
        }
        videoCapture.release();
        statusLabel.setText("Camera disconnected.");
    }

    private void onCapture() {
        if (currentFrame == null || currentFrame.empty()) {
            statusLabel.setText("Error: No frame available to capture.");
            return;
        }

        captureButton.setEnabled(false);
        statusLabel.setText("Processing...");

        // Run processing in a background thread to keep the UI responsive
        new Thread(() -> {
            Mat frameToProcess = currentFrame.clone();

            // 1. Save the original captured image
            String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS").format(LocalDateTime.now());
            String inputFilename = ConfigLoader.getProperty("output.input_folder") + "/capture_" + timestamp + ".png";
            Imgcodecs.imwrite(inputFilename, frameToProcess);

            // 2. Process the image to find plates
            List<ProcessResult> results = imageProcessor.processImage(frameToProcess);

            // 3. Log to Excel and draw on the output image
            int validPlatesFound = 0;
            for (ProcessResult result : results) {
                if (result.isValid()) {
                    // Valid plate: Log it and draw in GREEN
                    excelExporter.appendRow(result.text, result.getVehicleDetails());
                    Imgproc.rectangle(frameToProcess, new Point(result.x1, result.y1), new Point(result.x2, result.y2), new Scalar(0, 255, 0), 2);
                    Imgproc.putText(frameToProcess, result.text, new Point(result.x1, result.y1 - 10), Imgproc.FONT_HERSHEY_SIMPLEX, 0.9, new Scalar(0, 255, 0), 2);
                    validPlatesFound++;
                } else {
                    // Invalid OCR: Just draw the box in RED for feedback
                    Imgproc.rectangle(frameToProcess, new Point(result.x1, result.y1), new Point(result.x2, result.y2), new Scalar(0, 0, 255), 1);
                }
            }

            // 4. Save the annotated output image
            String outputFilename = ConfigLoader.getProperty("output.output_folder") + "/processed_" + timestamp + ".png";
            Imgcodecs.imwrite(outputFilename, frameToProcess);

            final int finalValidPlatesFound = validPlatesFound;

            // 5. Update UI on the Event Dispatch Thread
            SwingUtilities.invokeLater(() -> {
                String status = String.format("Processing complete. Detected %d plates, validated %d. Saved to %s", results.size(), finalValidPlatesFound, outputFilename);
                statusLabel.setText(status);
                captureButton.setEnabled(true);
            });
        }).start();
    }

    // Inner class for the video panel
    private class VideoPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (currentFrame != null && !currentFrame.empty()) {
                g.drawImage(matToBufferedImage(currentFrame), 0, 0, this.getWidth(), this.getHeight(), null);
            } else {
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Color.WHITE);
                g.drawString("Connecting to camera...", 20, 30);
            }
        }
    }

    private BufferedImage matToBufferedImage(Mat mat) {
        int type = (mat.channels() > 1) ? BufferedImage.TYPE_3BYTE_BGR : BufferedImage.TYPE_BYTE_GRAY;
        BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
        mat.get(0, 0, ((DataBufferByte) image.getRaster().getDataBuffer()).getData());
        return image;
    }
}