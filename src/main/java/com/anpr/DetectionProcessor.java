package com.anpr;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

/**
 * Handles the processing of a single detected object from the YOLO model.
 */
public class DetectionProcessor {

    private final Tesseract tesseract;
    private final Pattern platePattern;

    public DetectionProcessor(Tesseract tesseract, Pattern platePattern) {
        this.tesseract = tesseract;
        this.platePattern = platePattern;
    }

    /**
     * Processes a single detected bounding box.
     * @param detectionRow The row from the detections matrix.
     * @param frame The original video frame.
     * @return The corrected text of the license plate, or an empty string if not valid.
     */
    public ProcessResult process(Mat detectionRow, Mat frame) {
        // 1. Extract Bounding Box and Scale
        double x = detectionRow.get(0, 0)[0];
        double y = detectionRow.get(0, 1)[0];
        double w = detectionRow.get(0, 2)[0];
        double h = detectionRow.get(0, 3)[0];

        double xScale = frame.width() / 640.0;
        double yScale = frame.height() / 640.0;

        int x1 = (int) ((x - w / 2) * xScale);
        int y1 = (int) ((y - h / 2) * yScale);
        int x2 = (int) ((x + w / 2) * xScale);
        int y2 = (int) ((y + h / 2) * yScale);

        // 2. Clamp and Validate ROI
        int clampedX1 = Math.max(0, x1);
        int clampedY1 = Math.max(0, y1);
        int clampedX2 = Math.min(frame.width() - 1, x2);
        int clampedY2 = Math.min(frame.height() - 1, y2);

        if (clampedX2 <= clampedX1 || clampedY2 <= clampedY1) {
            return null; // Invalid ROI
        }

        // 3. Aspect Ratio Filter
        float minAspectRatio = ConfigLoader.getFloatProperty("detection.minAspectRatio", 1.5f);
        float maxAspectRatio = ConfigLoader.getFloatProperty("detection.maxAspectRatio", 5.5f);
        double aspectRatio = (double) (clampedX2 - clampedX1) / (clampedY2 - clampedY1);
        if (aspectRatio < minAspectRatio || aspectRatio > maxAspectRatio) {
            return null; // Fails aspect ratio filter
        }

        // 4. Crop and Perform OCR
        Rect roi = new Rect(new Point(clampedX1, clampedY1), new Point(clampedX2, clampedY2));
        Mat licensePlate = new Mat(frame, roi);
        if (licensePlate.empty()) {
            return null;
        }

        String correctedText = "";
        try {
            correctedText = performOcr(licensePlate);
        } catch (TesseractException e) {
            // System.out.println("Tesseract Error: " + e.getMessage());
        }

        return new ProcessResult(clampedX1, clampedY1, clampedX2, clampedY2, correctedText);
    }

    private String performOcr(Mat licensePlate) throws TesseractException {
        // Pre-processing
        Mat grayPlate = new Mat();
        Imgproc.cvtColor(licensePlate, grayPlate, Imgproc.COLOR_BGR2GRAY);

        Mat threshPlate = new Mat();
        Imgproc.threshold(grayPlate, threshPlate, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);

        Mat resizedPlate = new Mat();
        Imgproc.resize(threshPlate, resizedPlate, new Size(), 2, 2, Imgproc.INTER_CUBIC);

        // Convert and OCR
        BufferedImage bufferedImage = matToBufferedImage(resizedPlate);
        String rawText = tesseract.doOCR(bufferedImage);

        // Post-processing
        return correctPlateFormat(rawText);
    }

    private BufferedImage matToBufferedImage(Mat mat) {
        int type = mat.channels() > 1 ? BufferedImage.TYPE_3BYTE_BGR : BufferedImage.TYPE_BYTE_GRAY;
        BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
        byte[] buffer = new byte[mat.channels() * mat.cols() * mat.rows()];
        mat.get(0, 0, buffer);
        System.arraycopy(buffer, 0, ((DataBufferByte) image.getRaster().getDataBuffer()).getData(), 0, buffer.length);
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
        return cleanedStr.length() > 10 ? cleanedStr.substring(0, 10) : cleanedStr;
    }
}
