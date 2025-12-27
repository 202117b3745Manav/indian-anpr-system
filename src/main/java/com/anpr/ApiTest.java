package com.anpr;

/**
 * A simple standalone test to verify API connectivity and Excel logging
 * without needing the camera or UI.
 */
public class ApiTest {
    public static void main(String[] args) {
        String testPlate = "UP14PT3456"; // The plate from your example
        String testFile = "test_log.xlsx";

        System.out.println("--- Starting API Test ---");
        System.out.println("Fetching details for: " + testPlate);

        // 1. Test API Call
        VehicleDetails details = VehicleApiClient.fetchVehicleDetails(testPlate);

        if (details != null) {
            System.out.println("API Success! Found: " + details.getOwnerName() + ", " + details.getVehicleModel());
        } else {
            System.err.println("API Failed. Check logs/console for errors.");
        }

        // 2. Test Excel Logging
        System.out.println("Logging to " + testFile + "...");
        ExcelLogger.logVehicleData(testFile, testPlate, details);
        System.out.println("--- Test Complete ---");
    }
}
