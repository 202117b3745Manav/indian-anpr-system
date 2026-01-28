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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnprUI extends JFrame {

    private static final Logger logger = LoggerFactory.getLogger(AnprUI.class);
    private final VideoPanel videoPanel;
    private final JButton captureButton;
    private final JButton enrichButton;
    private final JToggleButton liveModeButton;
    private final JButton resetButton;
    private final JLabel statusLabel;

    private VideoCapture videoCapture;
    private volatile Mat currentFrame;
    private volatile boolean isCameraActive = false;
    private Thread videoThread;

    private final ImageProcessor imageProcessor;
    private final Set<String> processedPlates = ConcurrentHashMap.newKeySet();
    private volatile List<ProcessResult> latestResults;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final Object frameLock = new Object();

    public AnprUI() {
        // 1. Initialize Core Components
        this.imageProcessor = new ImageProcessor();

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

        JPanel buttonPanel = new JPanel();
        
        captureButton = new JButton("Capture & Process");
        captureButton.addActionListener(e -> onCapture());
        buttonPanel.add(captureButton);

        liveModeButton = new JToggleButton("Live Mode");
        liveModeButton.addActionListener(e -> toggleLiveMode());
        buttonPanel.add(liveModeButton);

        resetButton = new JButton("Reset Session");
        resetButton.addActionListener(e -> onReset());
        buttonPanel.add(resetButton);

        enrichButton = new JButton("Enrich Data (API)");
        enrichButton.addActionListener(e -> onEnrich());
        buttonPanel.add(enrichButton);

        add(buttonPanel, BorderLayout.SOUTH);

        statusLabel = new JLabel("Ready. Please connect to the camera.");
        statusLabel.setBorder(BorderFactory.createLoweredBevelBorder());
        add(statusLabel, BorderLayout.NORTH);

        pack();
        setLocationRelativeTo(null); // Center the window

        // 3. Add Shutdown Hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stopCamera();
        }));

        // 4. Start Camera
        startCamera();
    }

    private void startCamera() {
        // Start the video thread immediately. Connection logic is moved to the thread
        // to prevent UI freezing and allow for auto-reconnection.
        isCameraActive = true;
        videoThread = new Thread(this::videoLoop);
        videoThread.setDaemon(true);
        videoThread.start();
        logger.info("Camera thread started.");
    }

    private void stopCamera() {
        logger.info("Stopping camera...");
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
        executorService.shutdown();
    }

    private void videoLoop() {
        String cameraUrl = ConfigLoader.getProperty("camera.url");
        Mat frame = new Mat();

        while (isCameraActive) {
            // 1. Connect if not connected
            if (videoCapture == null || !videoCapture.isOpened()) {
                SwingUtilities.invokeLater(() -> statusLabel.setText("Connecting to " + cameraUrl + "..."));
                logger.info("Attempting to connect to camera at: {}", cameraUrl);

                if (videoCapture != null) videoCapture.release();
                videoCapture = new VideoCapture(cameraUrl);

                if (videoCapture.isOpened()) {
                    logger.info("Camera connected successfully.");
                    SwingUtilities.invokeLater(() -> statusLabel.setText("Camera connected. Ready to capture."));
                } else {
                    logger.error("Failed to connect. Retrying in 3 seconds...");
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }
            }

            // 2. Read Frame
            if (videoCapture.read(frame)) {
                synchronized (frameLock) {
                    if (currentFrame != null) currentFrame.release();
                    currentFrame = frame.clone();
                }
                videoPanel.repaint();
            } else {
                logger.warn("Lost connection to camera. Attempting to reconnect...");
                videoCapture.release(); // Force reconnection in next loop iteration
            }
        }
        
        if (videoCapture != null) videoCapture.release();
        SwingUtilities.invokeLater(() -> statusLabel.setText("Camera disconnected."));
    }

    private void onCapture() {
        captureButton.setEnabled(false);
        statusLabel.setText("Processing...");
        logger.info("Capture initiated. Processing frame...");

        // Run processing in a background thread to keep the UI responsive
        executorService.submit(() -> {
            Mat frameToProcess = null;
            try {
                synchronized (frameLock) {
                    if (currentFrame != null && !currentFrame.empty()) {
                        frameToProcess = currentFrame.clone();
                    }
                }

                if (frameToProcess == null) {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Error: No frame available.");
                        captureButton.setEnabled(true);
                    });
                    return;
                }

            // 1. Save the original captured image
            String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS").format(LocalDateTime.now());
            String inputFilename = ConfigLoader.getProperty("output.input_folder") + "/capture_" + timestamp + ".png";
            Imgcodecs.imwrite(inputFilename, frameToProcess);
            logger.debug("Saved input frame to {}", inputFilename);

            // 2. Process the image to find plates
            List<ProcessResult> results = imageProcessor.processImage(frameToProcess);
            logger.info("Image processing complete. Found {} potential plates.", results.size());

            // 3. Log to Excel and draw on the output image
            int validPlatesFound = 0;
            for (ProcessResult result : results) {
                if (result.isValid()) {
                    // Check if the plate has already been processed in this session
                    if (processedPlates.add(result.text)) {
                        // New plate: Log it to Excel
                        ExcelLogger.logBasicDetection(ConfigLoader.getProperty("log.filename"), result.text);
                        logger.info("New valid plate found: {}", result.text);
                        validPlatesFound++;
                    } else {
                        logger.info("Duplicate plate detected (already processed): {}", result.text);
                    }
                    Imgproc.rectangle(frameToProcess, new Point(result.x1, result.y1), new Point(result.x2, result.y2), new Scalar(0, 255, 0), 2);
                    Imgproc.putText(frameToProcess, result.text, new Point(result.x1, result.y1 - 10), Imgproc.FONT_HERSHEY_SIMPLEX, 0.9, new Scalar(0, 255, 0), 2);
                } else {
                    // Invalid OCR: Just draw the box in RED for feedback
                    logger.debug("Invalid OCR result ignored: {}", result.text);
                    Imgproc.rectangle(frameToProcess, new Point(result.x1, result.y1), new Point(result.x2, result.y2), new Scalar(0, 0, 255), 1);
                }
            }

            // 4. Save the annotated output image
            String outputFilename = ConfigLoader.getProperty("output.output_folder") + "/processed_" + timestamp + ".png";
            Imgcodecs.imwrite(outputFilename, frameToProcess);
            logger.info("Saved annotated output to {}", outputFilename);

            final int finalValidPlatesFound = validPlatesFound;

            // 5. Update UI on the Event Dispatch Thread
            SwingUtilities.invokeLater(() -> {
                String status = String.format("Processing complete. Detected %d plates, validated %d. Saved to %s", results.size(), finalValidPlatesFound, outputFilename);
                statusLabel.setText(status);
                captureButton.setEnabled(true);
            });
            } catch (Exception e) {
                logger.error("Error during processing", e);
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Error: " + e.getMessage());
                    captureButton.setEnabled(true);
                });
            }
            finally {
                if (frameToProcess != null) frameToProcess.release();
            }
        });
    }

    private void onEnrich() {
        captureButton.setEnabled(false);
        enrichButton.setEnabled(false);
        statusLabel.setText("Starting batch enrichment...");

        executorService.submit(() -> {
            try {
                String inputFile = ConfigLoader.getProperty("log.filename");
                String outputFile = "enriched_" + inputFile;

                BatchDataEnricher.enrichData(inputFile, outputFile, (msg) -> {
                    SwingUtilities.invokeLater(() -> statusLabel.setText(msg));
                });

            } catch (Exception e) {
                logger.error("Error during enrichment", e);
                SwingUtilities.invokeLater(() -> statusLabel.setText("Error: " + e.getMessage()));
            } finally {
                SwingUtilities.invokeLater(() -> {
                    captureButton.setEnabled(true);
                    enrichButton.setEnabled(true);
                });
            }
        });
    }

    private void onReset() {
        processedPlates.clear();
        latestResults = null;
        statusLabel.setText("Session reset. Ready to scan new plates.");
        videoPanel.repaint();
    }

    private void toggleLiveMode() {
        if (liveModeButton.isSelected()) {
            captureButton.setEnabled(false);
            enrichButton.setEnabled(false);
            statusLabel.setText("Live Mode Active - Scanning...");
            new Thread(this::liveProcessingLoop).start();
        } else {
            captureButton.setEnabled(true);
            enrichButton.setEnabled(true);
            statusLabel.setText("Live Mode Stopped.");
            latestResults = null;
            videoPanel.repaint();
        }
    }

    private void liveProcessingLoop() {
        while (liveModeButton.isSelected() && isCameraActive) {
            try {
                Mat frameCopy = null;
                synchronized (frameLock) {
                    if (currentFrame != null && !currentFrame.empty()) {
                        frameCopy = currentFrame.clone();
                    }
                }
                if (frameCopy != null) {
                    List<ProcessResult> results = imageProcessor.processImage(frameCopy);
                    this.latestResults = results;

                    boolean newPlate = false;
                    for (ProcessResult result : results) {
                        if (result.isValid() && processedPlates.add(result.text)) {
                            ExcelLogger.logBasicDetection(ConfigLoader.getProperty("log.filename"), result.text);
                            logger.info("Live: New plate found: {}", result.text);
                            newPlate = true;

                            // Save snapshot for the new plate
                            String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS").format(LocalDateTime.now());
                            String filename = ConfigLoader.getProperty("output.output_folder") + "/live_" + result.text + "_" + timestamp + ".png";
                            
                            // Draw on frameCopy for saving
                            Imgproc.rectangle(frameCopy, new Point(result.x1, result.y1), new Point(result.x2, result.y2), new Scalar(0, 255, 0), 2);
                            Imgproc.putText(frameCopy, result.text, new Point(result.x1, result.y1 - 10), Imgproc.FONT_HERSHEY_SIMPLEX, 0.9, new Scalar(0, 255, 0), 2);
                            Imgcodecs.imwrite(filename, frameCopy);
                        }
                    }

                    if (newPlate) {
                        SwingUtilities.invokeLater(() -> statusLabel.setText("New plate detected!"));
                    }
                    videoPanel.repaint();
                    frameCopy.release();
                }
                Thread.sleep(200); // Process ~5 FPS to avoid CPU overload
            } catch (Exception e) {
                logger.error("Error in live loop", e);
            }
        }
    }

    // Inner class for the video panel
    private class VideoPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            synchronized (frameLock) {
                if (currentFrame != null && !currentFrame.empty()) {
                    g.drawImage(matToBufferedImage(currentFrame), 0, 0, this.getWidth(), this.getHeight(), null);
                
                    if (latestResults != null && !latestResults.isEmpty()) {
                        double scaleX = (double) getWidth() / currentFrame.cols();
                        double scaleY = (double) getHeight() / currentFrame.rows();
                        
                        for (ProcessResult result : latestResults) {
                            int x = (int) (result.x1 * scaleX);
                            int y = (int) (result.y1 * scaleY);
                            int w = (int) ((result.x2 - result.x1) * scaleX);
                            int h = (int) ((result.y2 - result.y1) * scaleY);
                            
                            if (result.isValid()) {
                                g.setColor(Color.GREEN);
                                g.drawString(result.text, x, y - 5);
                            } else {
                                g.setColor(Color.RED);
                            }
                            ((java.awt.Graphics2D) g).setStroke(new java.awt.BasicStroke(2));
                            g.drawRect(x, y, w, h);
                        }
                    }
                } else {
                    g.setColor(Color.BLACK);
                    g.fillRect(0, 0, getWidth(), getHeight());
                    g.setColor(Color.WHITE);
                    g.drawString("Connecting to camera...", 20, 30);
                }
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