# Indian ANPR System

An Automatic Number Plate Recognition (ANPR) system designed for Indian license plates. This application uses YOLOv8 for object detection, Tesseract OCR for text recognition, and integrates with the RegCheck API to fetch vehicle registration details.

## Features

*   **Real-time Detection**: Detects license plates from a video stream (IP Webcam or local camera).
*   **OCR Processing**: Extracts text using Tesseract with custom character correction for common Indian plate errors.
*   **Excel Logging**:
    *   **Basic Mode**: Logs detected plate numbers and timestamps to `detection_log.xlsx` instantly.
    *   **Enriched Mode**: Fetches vehicle details (Owner, Model, Registration Date) and saves them to `enriched_detection_log.xlsx`.
*   **Batch Data Enrichment**: A dedicated tool to process logged plates in bulk and fetch details from the API.
*   **UI Dashboard**: A Java Swing interface to view the camera feed, capture plates, and trigger the enrichment process.

## Prerequisites

*   **Java 17** or higher
*   **Maven**
*   **Tesseract OCR**: Installed on your system (v4.x or v5.x).
*   **RegCheck API Account**: A username for regcheck.org.uk (Free trial available).

## Setup

1.  **Clone the repository**.
2.  **Install Dependencies**:
    ```bash
    mvn clean install
    ```
3.  **Configure the Application**:
    Edit `src/main/resources/config.properties`:
    ```properties
    # Camera URL (e.g., from IP Webcam app)
    camera.url=http://192.168.1.100:8080/video

    # Path to Tesseract 'tessdata' folder
    tesseract.path=C:/Program Files/Tesseract-OCR/tessdata

    # API Credentials
    api.username=your_regcheck_username
    api.url=https://www.regcheck.org.uk/api/reg.asmx/CheckIndia
    ```

## Usage

### 1. Running the Main Application
Launch the UI to start detecting plates.
```bash
mvn exec:java -Dexec.mainClass="com.anpr.App"
```
*   **Capture & Process**: Captures the current frame, detects the plate, and logs the number to `detection_log.xlsx`.
*   **Enrich Data (API)**: Reads the basic log file, fetches details for each plate from the API, and saves them to `enriched_detection_log.xlsx`.

### 2. Running Batch Enrichment Manually
You can also run the enrichment tool directly from the command line without the UI:
```bash
mvn exec:java -Dexec.mainClass="com.anpr.BatchDataEnricher"
```

## Project Structure

*   `src/main/java/com/anpr/`
    *   `App.java`: Main entry point for the UI.
    *   `AnprUI.java`: Swing UI handling camera feed and buttons.
    *   `ImageProcessor.java`: Orchestrates YOLO detection and Tesseract OCR.
    *   `VehicleApiClient.java`: Handles HTTP requests to the RegCheck API.
    *   `ExcelLogger.java`: Manages reading/writing to Excel files using Apache POI.
    *   `BatchDataEnricher.java`: Logic for bulk API fetching.
*   `src/main/resources/`
    *   `config.properties`: Application settings.
    *   `simplelogger.properties`: Logging configuration.

## Troubleshooting

*   **Logs**: Check `anpr_app.log` for detailed system logs, including API request/response bodies.
*   **Excel File Locked**: Ensure `detection_log.xlsx` is closed in Microsoft Excel before capturing or enriching data.
*   **OCR Accuracy**: If plates are misread (e.g., 'T' vs 'Y'), check `DetectionProcessor.java` to adjust character mappings.
*   **Dependency Conflicts**: If you encounter `NoSuchMethodError` related to POI/Commons-IO, ensure `commons-io` version 2.16.1+ is in your `pom.xml`.