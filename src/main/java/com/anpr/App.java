package com.anpr;

import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.highgui.HighGui;
import org.opencv.imgproc.Imgproc;
import org.opencv.dnn.Net;
import org.opencv.core.Size;
import org.opencv.core.Scalar;
import org.opencv.dnn.Dnn;
import org.opencv.core.Point;
import org.opencv.core.Core;
import java.util.ArrayList;
import java.util.List;
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
                        Imgproc.rectangle(frame, new Point(x1, y1), new Point(x2, y2), new Scalar(0, 255, 0), 2);
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
