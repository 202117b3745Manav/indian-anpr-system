package com.anpr;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Reads the basic detection log (Plate numbers only), fetches details from the API,
 * and saves the enriched data to a new Excel file.
 */
public class BatchDataEnricher {

    private static final Logger logger = LoggerFactory.getLogger(BatchDataEnricher.class);

    public static void main(String[] args) {
        String inputFile = ConfigLoader.getProperty("log.filename");
        String outputFile = "enriched_" + inputFile;
        enrichData(inputFile, outputFile, msg -> logger.info(msg));
    }

    public static void enrichData(String inputFile, String outputFile, Consumer<String> statusCallback) {
        if (statusCallback != null) statusCallback.accept("Starting batch enrichment...");
        logger.info("Starting batch enrichment. Reading: {}, Writing: {}", inputFile, outputFile);

        List<String> plates = readPlatesFromExcel(inputFile);
        
        if (plates.isEmpty()) {
            if (statusCallback != null) statusCallback.accept("No plates found in " + inputFile);
            return;
        }

        if (statusCallback != null) statusCallback.accept("Found " + plates.size() + " plates. Processing...");

        for (int i = 0; i < plates.size(); i++) {
            String plate = plates.get(i);
            String statusMsg = String.format("[%d/%d] Fetching details for: %s", i + 1, plates.size(), plate);
            logger.info(statusMsg);
            if (statusCallback != null) statusCallback.accept(statusMsg);
            
            VehicleDetails details = VehicleApiClient.fetchVehicleDetails(plate);
            ExcelLogger.logVehicleData(outputFile, plate, details);

            // Optional: Sleep briefly to be nice to the API
            try { Thread.sleep(500); } catch (InterruptedException e) {}
        }

        logger.info("Batch processing complete.");
        
        // Reset the input file to avoid re-processing the same data
        File file = new File(inputFile);
        if (file.exists() && file.delete()) {
            logger.info("Input file {} has been reset.", inputFile);
            if (statusCallback != null) statusCallback.accept("Batch processing complete. Saved to " + outputFile + ". Log reset.");
        } else {
            logger.warn("Failed to reset input file {}. Please ensure it is not open.", inputFile);
            if (statusCallback != null) statusCallback.accept("Batch processing complete. Saved to " + outputFile + ". Warning: Log not reset.");
        }
    }

    private static List<String> readPlatesFromExcel(String filePath) {
        List<String> plates = new ArrayList<>();
        File file = new File(filePath);

        if (!file.exists()) {
            logger.error("Input file does not exist: {}", filePath);
            return plates;
        }

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // Skip header
                Cell cell = row.getCell(1); // Column 1 is Plate Number
                if (cell != null) {
                    plates.add(cell.getStringCellValue());
                }
            }
        } catch (Exception e) {
            logger.error("Error reading Excel file", e);
        }
        return plates;
    }
}
