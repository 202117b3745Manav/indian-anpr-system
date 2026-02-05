package com.anpr;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;

import net.sourceforge.tess4j.Tesseract;

public class ImageProcessor {

    private final Net yoloNet;
    private final DetectionProcessor detectionProcessor;
    private final float confidenceThreshold;

    public ImageProcessor() {
        // 1. Load YOLO Model
        String modelPath = ConfigLoader.getProperty("model.path");
        this.yoloNet = Dnn.readNetFromONNX(modelPath);
        if (yoloNet.empty()) {
            throw new RuntimeException("Failed to load YOLO model from " + modelPath);
        }
        System.out.println("YOLO model loaded successfully.");

        // 2. Initialize Tesseract
        Tesseract tesseract = new Tesseract();
        try {
            tesseract.setDatapath(ConfigLoader.getProperty("tesseract.path"));
        } catch (Exception e) {
            throw new RuntimeException("Failed to set Tesseract data path.", e);
        }

        // 3. Initialize Helper Processors
        Pattern platePattern = Pattern.compile("^[A-Z]{2}[0-9]{2}[A-Z]{1,2}[0-9]{4}$");
        this.detectionProcessor = new DetectionProcessor(tesseract, platePattern);
        this.confidenceThreshold = ConfigLoader.getFloatProperty("detection.confidenceThreshold", 0.5f);
    }

    public synchronized List<ProcessResult> processImage(Mat frame) {
        List<ProcessResult> validResults = new ArrayList<>();

        // 1. Prepare frame for YOLO
        Mat inputBlob = Dnn.blobFromImage(frame, 1 / 255.0, new Size(640, 640), new org.opencv.core.Scalar(0), true, false);
        yoloNet.setInput(inputBlob);

        // 2. Run Inference
        Mat output = yoloNet.forward();
        Mat detections = output.reshape(1, (int) output.size(1));
        Core.transpose(detections, detections);

        // 3. Process each detection
        for (int i = 0; i < detections.rows(); i++) {
            Mat row = detections.row(i);
            if ((float) row.get(0, 4)[0] < confidenceThreshold) {
                continue;
            }

            ProcessResult result = detectionProcessor.process(row, frame);
            if (result != null) { // Return all processed results, not just valid ones
                validResults.add(result);
            }
        }

        return validResults;
    }
}