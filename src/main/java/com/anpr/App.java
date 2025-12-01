package com.anpr;

import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.highgui.HighGui;
import org.opencv.dnn.Net;
import org.opencv.dnn.Dnn;
import nu.pattern.OpenCV;

public class App {
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
                // Display the frame in the window
                HighGui.imshow(windowName, frame);

                // Wait for 1ms for a key press. If a key is pressed, break the loop.
                if (HighGui.waitKey(1) >= 0) {
                    break;
                }
            } else {
                System.out.println("Error: Could not read a frame from the video stream. Exiting.");
                break;
            }
        }

        // Release the VideoCapture object to free resources
        cap.release();
        HighGui.destroyAllWindows();
        System.out.println("Stream stopped and resources released.");
    }
}
