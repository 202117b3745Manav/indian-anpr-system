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
        if (text != null && !text.isEmpty() && text.matches("^[A-Z]{2}[0-9]{2}[A-Z]{1,2}[0-9]{4}$")) {
            this.vehicleDetails = VehicleApiClient.fetchVehicleDetails(text);
            return true;
        }
        return false;
    }

    public VehicleDetails getVehicleDetails() {
        return vehicleDetails;
    }
}
