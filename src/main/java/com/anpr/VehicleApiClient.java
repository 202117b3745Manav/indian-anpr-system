package com.anpr;

import com.google.gson.Gson;

public class VehicleApiClient {

    private static final Gson gson = new Gson();

    // Predefined lists for generating diverse mock data
    private static final String[] MOCK_OWNERS = {"Amit Singh", "Priya Sharma", "Rahul Kumar", "Sunita Devi", "Vikram Rathore", "Anjali Mehta"};
    private static final String[] MOCK_MODELS = {"Maruti Baleno", "Hyundai Creta", "Tata Nexon", "Kia Seltos", "Mahindra XUV700", "Honda City"};
    private static final String[] MOCK_DATES = {"2022-08-11", "2021-05-23", "2023-01-15", "2020-11-30", "2022-03-19"};

    /**
     * MOCK IMPLEMENTATION: Simulates calling a vehicle details API.
     * This version generates deterministic mock data based on the plate number's hash code.
     * @param plateNumber The license plate number to look up.
     * @return A VehicleDetails object or null if not found.
     */
    public static VehicleDetails fetchVehicleDetails(String plateNumber) {
        System.out.println("Fetching details for " + plateNumber + "...");
        try {
            // Simulate network delay
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Use the plate number's hash code to select mock data deterministically
        int hashCode = Math.abs(plateNumber.hashCode());
        String owner = MOCK_OWNERS[hashCode % MOCK_OWNERS.length];
        String model = MOCK_MODELS[hashCode % MOCK_MODELS.length];
        String date = MOCK_DATES[hashCode % MOCK_DATES.length];

        // Create a mock JSON response string
        String mockJsonResponse = String.format(
            "{\"ownerName\":\"%s\",\"vehicleModel\":\"%s\",\"registrationDate\":\"%s\"}",
            owner, model, date
        );
        return gson.fromJson(mockJsonResponse, VehicleDetails.class);
    }
}