package com.anpr;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.highgui.HighGui;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;

import net.sourceforge.tess4j.Tesseract;
import nu.pattern.OpenCV;

public class App {

    // --- Stabilization Helpers ---
    // Stores the history of OCR readings for a given box ID
    private static Map<String, Deque<String>> plateHistory = new HashMap<>();
    private static final int HISTORY_SIZE = 10;
    // Stores plate numbers that have already been processed to avoid duplicate API calls
    private static HashSet<String> processedPlates = new HashSet<>();
    // Regex for common Indian license plate formats
    private static final Pattern platePattern = Pattern.compile("^[A-Z]{2}[0-9]{2}[A-Z]{1,2}[0-9]{4}$");


    public static void main(String[] args) {
        initializeSystem();

        // INITIALIZATION 
        
        ExcelExporter exporter = new ExcelExporter(ConfigLoader.getProperty("log.filename"));

        // Add a shutdown hook to ensure the Excel file is saved on exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> exporter.close()));

        // Load the YOLOv8 Model from the models folder
        String modelPath = ConfigLoader.getProperty("model.path");
        Net net;
        try {
            net = Dnn.readNetFromONNX(modelPath);
            System.out.println("Success: YOLOv8 model loaded from " + modelPath);
        } catch (Exception e) {
            System.out.println("Error: Could not load the YOLOv8 model.");
            System.out.println(e.getMessage());
            return; // Exit if the model cannot be loaded
        }

        // Initialize the Tesseract OCR engine
        Tesseract tesseract = new Tesseract();
        try {
            // The path to the "tessdata" folder
            tesseract.setDatapath(ConfigLoader.getProperty("tesseract.path"));
        } catch (Exception e) {
            System.out.println("Error: Could not set the Tesseract data path.");
            System.out.println(e.getMessage());
            return;
        }

        // Define the URL for the IP Webcam stream
        String ipCamUrl = ConfigLoader.getProperty("camera.url");

        // Create a VideoCapture object to access the camera stream
        VideoCapture cap = new VideoCapture(ipCamUrl);

        // Check if the stream is opened successfully
        if (!cap.isOpened()) {
            System.out.println("Error: Could not open video stream from " + ipCamUrl);
            return; // Exit if the stream cannot be opened
        }

        DetectionProcessor processor = new DetectionProcessor(tesseract, platePattern);

        System.out.println("\n--- System Initialized. Starting video stream... ---");

        runMainLoop(cap, net, processor, exporter);

        // CLEANUP 
        cleanup(cap);
    }

    private static void initializeSystem() {
        OpenCV.loadLocally();
        HighGui.namedWindow("Live Video Feed");
    }

    // Helper method to correct common OCR errors on Indian license plates
    public static String correctPlateFormat(String ocrText) {
        if (ocrText == null || ocrText.isEmpty()) {
            return "";
        }

        String text = ocrText.toUpperCase().replaceAll("[^A-Z0-9]", "");

        // Per-character mappings for common OCR errors
        Map<Character, Character> perCharMap = new HashMap<>();
        perCharMap.put('O', '0');
        perCharMap.put('I', '1');
        perCharMap.put('Z', '2');
        perCharMap.put('S', '5');
        perCharMap.put('B', '8');
        perCharMap.put('G', '6');
        perCharMap.put('T', '7');

        StringBuilder cleaned = new StringBuilder();
        for (char ch : text.toCharArray()) {
            // Apply direct substitution if exists, otherwise keep the original if it's a letter/digit
            if (perCharMap.containsKey(ch)) {
                cleaned.append(perCharMap.get(ch));
            } else if (Character.isLetterOrDigit(ch)) {
                cleaned.append(ch);
            }
        }

        String cleanedStr = cleaned.toString();

        // Many Indian plates are 10 chars; trim accordingly
        return cleanedStr.length() > 10 ? cleanedStr.substring(0, 10) : cleanedStr;
    }

    
    // Gets the most stable OCR text from the history of a given box ID
    private static String getStablePlate(String boxId, String newText) {
        plateHistory.putIfAbsent(boxId, new ArrayDeque<>(HISTORY_SIZE));
        Deque<String> history = plateHistory.get(boxId);

        if (newText != null && !newText.isEmpty()) {
            if (history.size() == HISTORY_SIZE) {
                history.poll(); // Remove the oldest element
            }
            history.offer(newText); // Add the new element
        }

        if (history.isEmpty()) {
            return "";
        }

        // Find the most frequent string in the history
        return history.stream()
                .collect(java.util.stream.Collectors.groupingBy(s -> s, java.util.stream.Collectors.counting()))
                .entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");
    }

    private static void runMainLoop(VideoCapture cap, Net net, DetectionProcessor processor, ExcelExporter exporter) {
        Mat frame = new Mat();
        String ipCamUrl = ConfigLoader.getProperty("camera.url");

        while (true) {
            if (cap.read(frame)) {
                // 1. Prepare frame for YOLO
                Mat inputBlob = Dnn.blobFromImage(frame, 1 / 255.0, new Size(640, 640), new Scalar(0), true, false);
                net.setInput(inputBlob);

                // 2. Run Inference
                Mat output = net.forward();
                Mat detections = output.reshape(1, (int)output.size(1));
                Core.transpose(detections, detections);

                // 3. Process Detections
                float confidenceThreshold = ConfigLoader.getFloatProperty("detection.confidenceThreshold", 0.5f);
                for (int i = 0; i < detections.rows(); i++) {
                    Mat row = detections.row(i);
                    if ((float) row.get(0, 4)[0] < confidenceThreshold) {
                        continue;
                    }

                    ProcessResult result = processor.process(row, frame);
                    if (result == null) {
                        continue;
                    }

                    // 4. Stabilize and Log
                    String stableText = getStablePlate(result.getBoxId(), result.text);
                    handleNewPlate(stableText, exporter);

                    // 5. Draw on Frame
                    Imgproc.rectangle(frame, new Point(result.x1, result.y1), new Point(result.x2, result.y2), new Scalar(0, 255, 0), 2);
                    Imgproc.putText(frame, stableText, new Point(result.x1, result.y1 - 10), Imgproc.FONT_HERSHEY_SIMPLEX, 0.9, new Scalar(255, 255, 255), 2);
                }

                HighGui.imshow("Live Video Feed", frame);
                if (HighGui.waitKey(1) >= 0) {
                    break;
                }
            } else {
                System.out.println("Error: Could not read a frame. Attempting to reconnect...");
                cap.release();
                try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                cap = new VideoCapture(ipCamUrl);
                if (!cap.isOpened()) {
                    System.out.println("Reconnect failed. Exiting.");
                    break;
                }
            }
        }
    }

    private static void handleNewPlate(String stableText, ExcelExporter exporter) {
        if (stableText == null || stableText.isEmpty() || processedPlates.contains(stableText)) {
            return;
        }

        if (platePattern.matcher(stableText).matches()) {
            System.out.println("\n--- New Valid Plate Detected: " + stableText + " ---");
            VehicleDetails details = VehicleApiClient.fetchVehicleDetails(stableText);
            if (details != null) {
                System.out.println(details.toString());
                exporter.appendRow(stableText, details);
            }
            processedPlates.add(stableText);
        }
    }

    private static void cleanup(VideoCapture cap) {
        System.out.println("Shutting down...");
        cap.release();
        HighGui.destroyAllWindows();
        // The exporter is closed by the shutdown hook.
    }
}
