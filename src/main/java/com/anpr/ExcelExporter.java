package com.anpr;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class ExcelExporter {

    private Workbook workbook;
    private Sheet sheet;
    private String filename;
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    public ExcelExporter(String filename) {
        this.filename = filename;
        this.workbook = new XSSFWorkbook();
        this.sheet = workbook.createSheet("Detections");
        createHeaderRow();
    }

    private void createHeaderRow() {
        Row headerRow = sheet.createRow(0);
        String[] columns = { "Timestamp", "Plate Number", "Owner Name", "Vehicle Model" };
        for (int i = 0; i < columns.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columns[i]);
        }
    }

    public void appendRow(String plateNumber, VehicleDetails details) {
        int lastRowNum = sheet.getLastRowNum();
        Row row = sheet.createRow(lastRowNum + 1);

        row.createCell(0).setCellValue(dtf.format(LocalDateTime.now()));
        row.createCell(1).setCellValue(plateNumber);
        row.createCell(2).setCellValue(details.getOwnerName());
        row.createCell(3).setCellValue(details.getVehicleModel());

        System.out.println("Logged to Excel: " + plateNumber);
    }

    public void close() {
        try (FileOutputStream fileOut = new FileOutputStream(filename)) {
            workbook.write(fileOut);
            workbook.close();
            System.out.println("Excel log file saved successfully to " + filename);
        } catch (IOException e) {
            System.out.println("Error saving Excel file: " + e.getMessage());
        }
    }
}