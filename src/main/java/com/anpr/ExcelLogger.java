package com.anpr;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ExcelLogger {

    private static final Logger logger = LoggerFactory.getLogger(ExcelLogger.class);
    private static final String[] BASIC_HEADERS = {"Timestamp", "Plate Number"};
    private static final String[] FULL_HEADERS = {"Timestamp", "Plate Number", "Owner Name", "Vehicle Model", "Registration Date"};

    public static synchronized void logBasicDetection(String filePath, String plateNumber) {
        writeToExcel(filePath, plateNumber, null, BASIC_HEADERS);
    }

    /**
     * Logs the vehicle details to an Excel file.
     * Used by the BatchDataEnricher to save full details.
     */
    public static synchronized void logVehicleData(String filePath, String plateNumber, VehicleDetails details) {
        writeToExcel(filePath, plateNumber, details, FULL_HEADERS);
    }

    private static void writeToExcel(String filePath, String plateNumber, VehicleDetails details, String[] headers) {
        Workbook workbook = null;
        File file = new File(filePath);

        try {
            if (file.exists() && file.length() > 0) {
                // Open existing workbook
                try (FileInputStream fis = new FileInputStream(file)) {
                    workbook = new XSSFWorkbook(fis);
                } catch (Exception e) {
                    logger.warn("Could not read existing Excel file (might be corrupt or empty). Creating new one.", e);
                    workbook = new XSSFWorkbook();
                }
            } else {
                // Create new workbook
                workbook = new XSSFWorkbook();
            }

            Sheet sheet = workbook.getSheet("Vehicle Logs");
            if (sheet == null) {
                sheet = workbook.createSheet("Vehicle Logs");
                createHeaderRow(sheet, headers);
            }

            // Create data row
            int lastRowNum = sheet.getLastRowNum();
            Row row = sheet.createRow(lastRowNum + 1);

            // Timestamp
            row.createCell(0).setCellValue(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            
            // Plate Number
            row.createCell(1).setCellValue(plateNumber);

            // Details from API
            if (headers.length > 2) {
                if (details != null) {
                // Assuming VehicleDetails has getters or public fields. 
                // Using reflection or assuming standard getters based on the JSON structure.
                // You might need to adjust these calls based on your actual VehicleDetails class.
                // Here we assume the class was populated by Gson and has these fields.
                
                // Since I cannot see VehicleDetails.java, I will assume it has these fields accessible via Gson serialization
                // or standard getters. For safety, I'll use the values we know we mapped.
                // In a real scenario, use details.getOwnerName(), etc.
                
                // For this implementation, let's assume standard getters exist:
                // row.createCell(2).setCellValue(details.getOwnerName());
                
                // If getters are not available, you can modify VehicleDetails to add them.
                // Below is a generic approach using Gson to access the fields if getters are missing:
                com.google.gson.JsonObject json = new com.google.gson.Gson().toJsonTree(details).getAsJsonObject();
                
                row.createCell(2).setCellValue(json.has("ownerName") ? json.get("ownerName").getAsString() : "N/A");
                row.createCell(3).setCellValue(json.has("vehicleModel") ? json.get("vehicleModel").getAsString() : "N/A");
                row.createCell(4).setCellValue(json.has("registrationDate") ? json.get("registrationDate").getAsString() : "N/A");
                } else {
                    row.createCell(2).setCellValue("Not Found");
                    row.createCell(3).setCellValue("Not Found");
                    row.createCell(4).setCellValue("Not Found");
                }
            }

            // Auto-size columns for "beautiful" layout
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Write to file
            try (FileOutputStream fos = new FileOutputStream(file)) {
                workbook.write(fos);
            }
            logger.info("Successfully logged details for {} to {}", plateNumber, filePath);

        } catch (FileNotFoundException e) {
            logger.error("Could not write to file {}. It might be open in Excel.", filePath);
        } catch (Exception e) {
            logger.error("Error logging to Excel file: {}", filePath, e);
        } finally {
            if (workbook != null) {
                try {
                    workbook.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static void createHeaderRow(Sheet sheet, String[] headers) {
        Row headerRow = sheet.createRow(0);
        Workbook wb = sheet.getWorkbook();
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        
        // Optional: Add background color
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
    }
}
