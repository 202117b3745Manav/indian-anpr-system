# Indian Vehicle Automatic Number Plate Recognition (ANPR) System

## 1. Project Overview

This project is a user-driven Automatic Number Plate Recognition (ANPR) system built in Java. It features a simple desktop UI, built with Swing, that displays a live video feed from a smartphone camera. The user can capture a frame at any time, and the system will process that single image to detect, read, and log Indian vehicle license plates.

The system uses a custom-trained YOLOv8 model for robust object detection and the Tesseract OCR engine for text extraction. This capstone project demonstrates skills in computer vision, machine learning integration, and Java desktop application development.

## 2. Key Features

-   **Swing Desktop UI:** A user-friendly interface provides a live video feed, a "Capture & Process" button, and a real-time status bar for feedback.
-   **On-Demand Processing:** The user has full control. Clicking the "Capture & Process" button analyzes a single, high-quality frame, ensuring accurate and deliberate analysis.
-   **OCR Text Extraction:** Employs Tesseract OCR with image pre-processing and post-processing logic to accurately read plate numbers.
-   **Visual Feedback:** The system generates an annotated output image, drawing **green boxes** around successfully validated plates and **red boxes** around plates that were detected but failed OCR validation.
-   **Data Validation:** Uses Regex to filter for valid Indian license plate formats, reducing false positives.
-   **Mock API Integration:** Fetches mock vehicle details for each valid plate to simulate integration with a government database.
-   **Excel Logging:** Records every unique, valid plate detection with a timestamp and vehicle details into a `detection_log.xlsx` file.
-   **Image Archiving:** Automatically saves the original captured image to an `input/` folder and the annotated image to an `output/` folder for review.


## 3. Technology Stack

-   **Language:** Java 17
-   **UI:** Java Swing
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
    1.  Download and install Tesseract for Windows from the official repository.
    2.  During installation, make sure to include the English language data.
    3.  Ensure the Tesseract installation path is added to your system's PATH. The application's `config.properties` file expects it to be in `C:\Program Files\Tesseract-OCR`.

### b. Project Setup

1.  **Clone the repository** and navigate into the project directory.
2.  **Place YOLO Model:** Place your trained `license_plate_best.onnx` file into the `models` directory.
3.  **Configure:** Open `src/main/resources/config.properties` and verify that the `camera.url` and `tesseract.path` are correct for your system.

### c. Smartphone Camera Setup

1.  Install the **IP Webcam** app on your Android smartphone.
2.  Connect your phone and your computer to the **same Wi-Fi network**.
3.  Start the IP Webcam app and select "Start server".
4.  Note the IP address and port shown on the screen (e.g., `http://192.168.31.214:8080`).
4.  Update the `camera.url` property in `src/main/resources/config.properties` with this address.

## 5. How to Run

### a. From the Command Line (Recommended)

1.  Open a terminal in the project's root directory.
2.  Build the project and create the executable JAR:
    ```bash
    mvn clean package
    ```
3.  Navigate to the `target` directory:
    ```bash
    cd target
    ```
4.  Run the application:
    ```bash
    java -jar indian-anpr-system-1.0-SNAPSHOT-jar-with-dependencies.jar
    ```

### b. From an IDE

You can also run the application directly from your IDE (like VS Code or IntelliJ) by executing the `main` method in the `src/main/java/com/anpr/App.java` file.

### c. Using the Application

1.  A window titled "Indian ANPR System" will appear, showing the live feed from your phone.
2.  Point the camera at a vehicle and click the **"Capture & Process"** button.
3.  The status bar will update with the results.
4.  Check the `output/` folder for the annotated image and the `detection_log.xlsx` file for any validated plate details.
