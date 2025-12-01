# Indian Vehicle Automatic Number Plate Recognition (ANPR) System

## 1. Project Overview

This project is a real-time Automatic Number Plate Recognition (ANPR) system built in Java. It is designed to detect and recognize Indian vehicle license plates from a live video stream, fetch mock vehicle details, and log the detections to an Excel file.

The system uses a custom-trained YOLOv8 model for robust object detection and the Tesseract OCR engine for text extraction. It is built as a capstone project demonstrating skills in computer vision, machine learning integration, and Java application development.

## 2. Key Features

-   **Live Video Capture:** Wirelessly streams video from a smartphone camera using the IP Webcam app.
-   **Real-Time Plate Detection:** Uses a custom-trained YOLOv8 model to accurately detect license plates in real-time.
-   **OCR Text Extraction:** Employs Tesseract OCR with image pre-processing and post-processing logic to accurately read plate numbers.
-   **Detection Stabilization:** Averages OCR results over several frames to provide a stable and accurate reading.
-   **Data Validation:** Uses Regex to filter for valid Indian license plate formats, reducing false positives.
-   **Mock API Integration:** Fetches mock vehicle details for each valid plate to simulate integration with a government database.
-   **Excel Logging:** Records every unique, valid plate detection with a timestamp and vehicle details into a `detection_log.xlsx` file.

## 3. Technology Stack

-   **Language:** Java 17
-   **Build & Dependency Management:** Apache Maven
-   **Computer Vision:** OpenCV 4.9.0
-   **Object Detection:** YOLOv8 (ONNX model)
-   **OCR:** Tesseract 5 with Tess4J wrapper
-   **API & JSON:** Gson for parsing mock API responses
-   **Excel Export:** Apache POI

## 4. Setup and Installation

To run this project, you will need to set up the following components:

### a. Prerequisites

-   **Java 17 (JDK):** Ensure you have Java 17 installed and the `JAVA_HOME` environment variable is set.
-   **Apache Maven:** Ensure Maven is installed and its `bin` directory is in your system's PATH.
-   **Tesseract OCR Engine:**
    1.  Download and install Tesseract for Windows from here.
    2.  During installation, make sure to include the English language data.
    3.  Ensure the Tesseract installation path is added to your system's PATH. The application expects it to be in `C:\Program Files\Tesseract-OCR`.

### b. Project Setup

1.  **Clone the repository:**
    ```bash
    git clone <your-repo-url>
    cd indian-anpr-system
    ```
2.  **Install Maven Dependencies:** Open a terminal in the project root and run:
    ```bash
    mvn install
    ```
3.  **Place YOLO Model:** Place your trained `license_plate_best.onnx` file into the `models` directory in the project root.

### c. Smartphone Camera Setup

1.  Install the **IP Webcam** app on your Android smartphone.
2.  Connect your phone and your computer to the **same Wi-Fi network**.
3.  Start the IP Webcam app and select "Start server".
4.  Note the IP address and port shown on the screen (e.g., `http://192.168.31.214:8080`).
5.  Update the `ipCamUrl` variable in `src/main/java/com/anpr/App.java` with this address.

## 5. How to Run

Once all setup steps are complete, you can run the application directly from your IDE (like VS Code or IntelliJ) by running the `main` method in the `App.java` file.

A window titled "Live Video Feed" will appear, showing the stream from your phone's camera with bounding boxes around detected license plates. Detected vehicle information will be printed to the console and logged in `detection_log.xlsx`.
