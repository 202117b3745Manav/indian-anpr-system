# Indian Vehicle Automatic Number Plate Recognition (ANPR) System

## Project Abstract

This application is a specialized desktop solution designed for the detection and recognition of Indian vehicle license plates in real-time. Built using Java and Computer Vision technologies, it bridges the gap between raw video feeds and actionable data. The system offers dual operational modes—manual capture for high-precision analysis and a live mode for continuous monitoring—making it suitable for security checkpoints, parking management, or traffic analysis.

## Core Features

### 1. Intelligent Detection Pipeline
*   **YOLOv8 Integration:** Utilizes a custom-trained ONNX model to detect license plates with high accuracy, even in complex visual environments.
*   **Optimized OCR Engine:** Leverages Tesseract 5 with specific tuning for Indian syntax. This includes character whitelisting (A-Z, 0-9) and single-line page segmentation to significantly reduce processing time and improve accuracy.
*   **Regex Validation:** Implements strict pattern matching based on Ministry of Road Transport and Highways (MoRTH) standards (e.g., standard `MH12AB1234` and BH Series) to filter out false positives.

### 2. Dual Operational Modes
*   **Live Surveillance Mode:** Continuously scans the camera feed, detecting and logging unique plates automatically without user intervention.
*   **Capture & Process:** Allows operators to freeze a specific frame for detailed inspection and logging, ideal for manned entry points.

### 3. Data Management & Enrichment
*   **Automated Logging:** Every valid detection is timestamped and logged into an Excel spreadsheet (`.xlsx`), creating an immediate audit trail.
*   **Batch Data Enrichment:** Includes a dedicated module to process logged plate numbers against an external Vehicle Registration API. This fetches and appends details like Owner Name, Vehicle Model, and Registration Date to the records.
*   **Visual Evidence:** Automatically archives the original capture and the processed output (with bounding boxes) for verification purposes.

## Technical Architecture

The system is built on a robust multi-threaded architecture to ensure the User Interface (UI) remains responsive during heavy image processing tasks.

*   **Frontend:** Java Swing provides the graphical interface, rendering the video feed and status updates.
*   **Computer Vision:** OpenCV (via Java bindings) handles image manipulation, resizing, and drawing bounding boxes.
*   **Deep Learning:** The `Dnn` module of OpenCV loads the YOLOv8 neural network for object detection.
*   **Text Recognition:** Tess4J acts as the Java wrapper for the Tesseract OCR engine.
*   **Concurrency:** Dedicated threads manage video capture, live inference loops, and UI updates independently to prevent freezing.

## Getting Started

### Prerequisites
*   **Java Development Kit (JDK) 17** or higher.
*   **Maven** for dependency management.
*   **Tesseract OCR:** Must be installed on the host machine (Windows/Linux). The path must be configured in the application properties.
*   **IP Webcam (Android):** The system is designed to ingest video streams via HTTP (e.g., from a smartphone running an IP Webcam app).

### Installation & Configuration

1.  **Clone the Repository**
    ```bash
    git clone <repository-url>
    ```

2.  **Model Setup**
    Place your trained YOLO model (`license_plate_best.onnx`) in the `models/` directory.

3.  **Configuration**
    Edit `src/main/resources/config.properties` to match your environment:
    *   `camera.url`: The IP address of your video feed (e.g., `http://192.168.1.100:8080/video`).
    *   `tesseract.path`: The absolute path to your Tesseract installation.
    *   `api.url` & `api.username`: Credentials for the vehicle registration API.

4.  **Build the Project**
    ```bash
    mvn clean package
    ```

### Usage Guide

1.  **Launch the Application:**
    Run the generated JAR file from the `target` directory:
    ```bash
    java -jar indian-anpr-system-1.0-SNAPSHOT-jar-with-dependencies.jar
    ```

2.  **Connect Camera:**
    The system attempts to connect to the configured URL on startup. Ensure your IP camera is online.

3.  **Manual Capture:**
    Click "Capture & Process" to analyze the current view.

4.  **Live Mode:**
    Toggle "Live Mode" to start automatic scanning. Valid plates are logged to Excel immediately.

5.  **Enrich Data:**
    Click "Enrich Data (API)" to process the Excel log and fetch vehicle ownership details for all recorded plates.
    
---
*Developed as a Capstone Project demonstrating the integration of Deep Learning, OCR, and Software Engineering principles.*
