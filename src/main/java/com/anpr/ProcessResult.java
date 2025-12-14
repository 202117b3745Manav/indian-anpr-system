package com.anpr;

/**
 * A data class to hold the results of processing a single detection.
 */
public class ProcessResult {
    public final int x1, y1, x2, y2;
    public final String text;
    private VehicleDetails vehicleDetails; // To hold fetched details

    public ProcessResult(int x1, int y1, int x2, int y2, String text) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.text = text;
    }

    /**
     * Checks if the detected text is a valid plate number.
     * If it is, it fetches the mock vehicle details.
     */
    public boolean isValid() {
        if (text == null || text.isEmpty()) {
            return false;
        }
        // Regex combining two patterns:
        // 1. Standard Plates: 2 letters, 1-2 digits, 1-2 letters (excluding I & O), 4 digits.
        // 2. BH Series Plates: 2 digits, "BH", 4 digits, 1-2 letters (excluding I & O).
        String plateRegex = "^([A-Z]{2}[0-9]{1,2}[A-HJ-NP-Z]{1,2}[0-9]{4})|([0-9]{2}BH[0-9]{4}[A-HJ-NP-Z]{1,2})$";

        if (text.matches(plateRegex)) {
            this.vehicleDetails = VehicleApiClient.fetchVehicleDetails(text);
            return true;
        }
        return false;
    }

    public VehicleDetails getVehicleDetails() {
        return vehicleDetails;
    }
}
