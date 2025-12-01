package com.anpr;

import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.imgcodecs.Imgcodecs;
import nu.pattern.OpenCV;

public class App {
    public static void main(String[] args) {
        OpenCV.loadLocally();

        // The URL should be http and point to the /video endpoint
        String ipCamUrl = "http://192.168.31.214:8080/video";

        // VideoCapture object to access the webcam stream
        VideoCapture cap = new VideoCapture(ipCamUrl);

        // Check if the stream is opened successfully
        if (!cap.isOpened()) {
            System.out.println("Error: Could not open video stream from " + ipCamUrl);
            return; // Exit if the stream cannot be opened
        }

        // Object to store the video frame
        Mat frame = new Mat();

        // Reading a frame from the video stream
        boolean success = cap.read(frame);

        // If a frame was successfully read, save it to a file
        if (success) {
            Imgcodecs.imwrite("output.png", frame);
            System.out.println("Success! Frame captured and saved to output.png");
        } else {
            System.out.println("Error: Could not read a frame from the video stream");
        }

        // Release the VideoCapture object to free resources
        cap.release();
    }
}
