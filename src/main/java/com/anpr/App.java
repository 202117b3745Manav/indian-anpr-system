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
import net.sourceforge.tess4j.TesseractException;
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
        OpenCV.loadLocally();

        // Loading the YOLOv8 Model
        String modelPath = "models/license_plate_best.onnx";
        Net net;
        try {
            net = Dnn.readNetFromONNX(modelPath);
            System.out.println("Success: YOLOv8 model loaded from " + modelPath);
        } catch (Exception e) {
            System.out.println("Error: Could not load the YOLOv8 model.");
            System.out.println(e.getMessage());
            return; // Exit if the model cannot be loaded
        }

        // Initialize Tesseract
        Tesseract tesseract = new Tesseract();
        try {
            // The path to the "tessdata" folder
            tesseract.setDatapath("C:/Program Files/Tesseract-OCR/tessdata");
        } catch (Exception e) {
            System.out.println("Error: Could not set the Tesseract data path.");
            System.out.println(e.getMessage());
            return;
        }

        // The URL should be http and point to the /video endpoint
        String ipCamUrl = "http://192.168.31.214:8080/video";

        // VideoCapture object to access the webcam stream
        VideoCapture cap = new VideoCapture(ipCamUrl);

        // Check if the stream is opened successfully
        if (!cap.isOpened()) {
            System.out.println("Error: Could not open video stream from " + ipCamUrl);
            return; // Exit if the stream cannot be opened
        }

        // Mat object to store the video frame
        Mat frame = new Mat();
        String windowName = "Live Video Feed";
        HighGui.namedWindow(windowName);

        // Loop to continuously read frames from the stream
        while (true) {
            if (cap.read(frame)) {
                // YOLOv8 Inference 
                // Prepare input blob for the open neural network exchange (ONNX) model
                Mat inputBlob = Dnn.blobFromImage(frame, 1 / 255.0, new Size(640, 640), new Scalar(0), true, false);
                net.setInput(inputBlob);

                // Run inference (forward pass)
                // The output of YOLOv8 is a single Mat with shape [1, 5, 8400]
                // where 5 = [x, y, w, h, confidence] and 8400 is the number of detections
                Mat output = net.forward();

                // Reshape the 3D output [1, 5, 8400] to a 2D table [5, 8400]
                Mat detections = output.reshape(1, (int)output.size(1));
                // Transpose the table to have detections as rows [8400, 5]
                Core.transpose(detections, detections);

                // Loop through all the detections
                float confidenceThreshold = 0.5f; // Only consider detections with 50% or more confidence
                for (int i = 0; i < detections.rows(); i++) {
                    Mat row = detections.row(i);
                    float confidence = (float) row.get(0, 4)[0];

                    if (confidence >= confidenceThreshold) {
                        // We have a detection!
                        // The bounding box coordinates are for the 640x640 image, so we need to scale them
                        double x = row.get(0, 0)[0];
                        double y = row.get(0, 1)[0];
                        double w = row.get(0, 2)[0];
                        double h = row.get(0, 3)[0];

                        // Scaling the coordinates to the original frame size
                        double x_scale = frame.width() / 640.0;
                        double y_scale = frame.height() / 640.0;

                        // Calculating the top-left and bottom-right points of the bounding box
                        double x1 = (x - w / 2) * x_scale;
                        double y1 = (y - h / 2) * y_scale;
                        double x2 = (x + w / 2) * x_scale;
                        double y2 = (y + h / 2) * y_scale;

                        // Drawing the bounding box on the original frame
                        // Clamp coordinates to ensure they are within frame boundaries
                        int clampedX1 = (int) Math.max(0, x1);
                        int clampedY1 = (int) Math.max(0, y1);
                        int clampedX2 = (int) Math.min(frame.width() - 1, x2);
                        int clampedY2 = (int) Math.min(frame.height() - 1, y2);

                        // Ensure the rectangle has positive width and height
                        if (clampedX2 <= clampedX1 || clampedY2 <= clampedY1) {
                            System.out.println("Skipping invalid ROI: " + clampedX1 + "," + clampedY1 + "," + clampedX2 + "," + clampedY2);
                            continue; // Skip to the next detection if ROI is invalid
                        }

                        // --- ASPECT RATIO FILTER ---
                        // Check the shape of the bounding box to filter out non-plate objects
                        double aspectRatio = (double) (clampedX2 - clampedX1) / (clampedY2 - clampedY1);
                        if (aspectRatio < 1.5 || aspectRatio > 5.5) {
                            // This is likely not a license plate, skip it
                            // System.out.println("Skipping ROI with invalid aspect ratio: " + aspectRatio);
                            continue;
                        }

                        Imgproc.rectangle(frame, new Point(clampedX1, clampedY1), new Point(clampedX2, clampedY2), new Scalar(0, 255, 0), 2);

                        // --- OCR PART ---
                        // Crop the license plate from the original frame
                        Rect roi = new Rect(new Point(clampedX1, clampedY1), new Point(clampedX2, clampedY2));
                        Mat licensePlate = new Mat(frame, roi);

                        // Add a check for empty licensePlate Mat after cropping
                        if (licensePlate.empty()) {
                            System.out.println("Skipping OCR for empty license plate Mat.");
                            continue; // Skip to the next detection if the cropped Mat is empty
                        }

                        String correctedText = "";
                        try {
                            // --- PRE-PROCESSING FOR OCR ---
                            Mat grayPlate = new Mat();
                            Imgproc.cvtColor(licensePlate, grayPlate, Imgproc.COLOR_BGR2GRAY);

                            Mat threshPlate = new Mat();
                            Imgproc.threshold(grayPlate, threshPlate, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);

                            Mat resizedPlate = new Mat();
                            Imgproc.resize(threshPlate, resizedPlate, new Size(), 2, 2, Imgproc.INTER_CUBIC);

                            // Convert pre-processed Mat to BufferedImage for Tesseract
                            BufferedImage bufferedImage = matToBufferedImage(resizedPlate);

                            // Perform OCR
                            String rawText = tesseract.doOCR(bufferedImage);

                            // Correct the OCR text
                            correctedText = correctPlateFormat(rawText);
                        } catch (TesseractException e) {
                            System.out.println("Tesseract Error: " + e.getMessage());
                        }

                        // --- STABILIZATION LOGIC ---
                        String boxId = getBoxId(clampedX1, clampedY1, clampedX2, clampedY2);
                        String stableText = getStablePlate(boxId, correctedText);

                        // Print the recognized text
                        // System.out.println("BoxID: " + boxId + " | Corrected: " + correctedText + " -> Stable: " + stableText);

                        // --- API CALL LOGIC ---
                        // Check if the stable text is a valid plate and has not been processed yet
                        if (stableText != null && !stableText.isEmpty() && !processedPlates.contains(stableText)) {
                            // Apply the strict regex pattern validation
                            if (platePattern.matcher(stableText).matches()) {
                                System.out.println("\n--- New Valid Plate Detected: " + stableText + " ---");
                                VehicleDetails details = VehicleApiClient.fetchVehicleDetails(stableText);
                                if (details != null) {
                                    System.out.println(details.toString());
                                }
                                processedPlates.add(stableText); // Mark this plate as processed
                            }
                        }

                        // Draw the STABLE recognized text on the frame
                        Imgproc.putText(
                            frame,
                            stableText,
                            new Point(clampedX1, clampedY1 - 10), // Position the text above the box
                            Imgproc.FONT_HERSHEY_SIMPLEX,
                            0.9,
                            new Scalar(255, 255, 255), // White color
                            2
                        );
                    }
                }

                // End YOLOv8 Inference

                // Display the frame in the window
                HighGui.imshow(windowName, frame);

                // Wait for 1ms for a key press. If a key is pressed, break the loop.
                if (HighGui.waitKey(1) >= 0) {
                    break;
                }
            } else {
                System.out.println("Error: Could not read a frame. Attempting to reconnect...");
                cap.release(); // Release the broken connection
                try {
                    Thread.sleep(2000); // Wait 2 seconds before reconnecting
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                cap = new VideoCapture(ipCamUrl); // Re-establish the connection
                if (!cap.isOpened()) {
                    System.out.println("Reconnect failed. Exiting.");
                    break;
                }
            }
        }

        // Release the VideoCapture object to free resources
        cap.release();
        HighGui.destroyAllWindows();
        System.out.println("Stream stopped and resources released.");
    }

    // Helper method to convert OpenCV Mat to BufferedImage
    private static BufferedImage matToBufferedImage(Mat mat) {
        int type = BufferedImage.TYPE_BYTE_GRAY;
        if (mat.channels() > 1) {
            type = BufferedImage.TYPE_3BYTE_BGR;
        }
        int bufferSize = mat.channels() * mat.cols() * mat.rows();
        byte[] buffer = new byte[bufferSize];
        mat.get(0, 0, buffer); // Get all the pixels
        BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
        final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(buffer, 0, targetPixels, 0, buffer.length);
        return image;
    }

    // Helper method to correct common OCR errors on Indian license plates
    private static String correctPlateFormat(String ocrText) {
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

    // Creates a pseudo-unique ID for a bounding box based on its rounded coordinates
    private static String getBoxId(int x1, int y1, int x2, int y2) {
        // Divide by a factor (e.g., 20) to group nearby boxes
        int factor = 20;
        return (x1 / factor) + "_" + (y1 / factor) + "_" + (x2 / factor) + "_" + (y2 / factor);
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

}
