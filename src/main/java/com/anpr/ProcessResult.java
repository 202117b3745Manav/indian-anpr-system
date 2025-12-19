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

        // --- Regex Components based on MoRTH Standards ---
        
        // 1. Valid State/UT Codes (Includes current and some legacy codes like OR/UA)
        String stateCodes = "(AN|AP|AR|AS|BR|CG|CH|DD|DL|DN|GA|GJ|HP|HR|JH|JK|KA|KL|LA|LD|MH|ML|MN|MP|MZ|NL|OD|OR|PB|PY|RJ|SK|TN|TR|TS|UA|UK|UP|WB)";
        
        // 2. District Code: 1 or 2 digits (e.g., 01, 12)
        String districtCode = "[0-9]{1,2}";
        
        // 3. Series: 1 or 2 letters. Excludes I, O, and Q to avoid confusion with 1 and 0.
        String series = "[A-HJ-NP-PR-Z]{1,2}";
        
        // 4. Unique Number: 4 digits (e.g., 1234)
        String uniqueNumber = "[0-9]{4}";

        // Combine into full patterns
        // Pattern 1: Standard (e.g., MH12AB1234)
        String standardPlateRegex = "^" + stateCodes + districtCode + series + uniqueNumber + "$";
        
        // Pattern 2: BH Series (e.g., 22BH1234XX)
        String bhSeriesRegex = "^[0-9]{2}BH" + uniqueNumber + series + "$";

        if (text.matches(standardPlateRegex) || text.matches(bhSeriesRegex)) {
            this.vehicleDetails = VehicleApiClient.fetchVehicleDetails(text);
            return true;
        }
        return false;
    }

    public VehicleDetails getVehicleDetails() {
        return vehicleDetails;
    }
}
