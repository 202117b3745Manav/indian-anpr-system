package com.anpr;

import com.google.gson.Gson;

public class VehicleApiClient {

    private static final Gson gson = new Gson();

    /**
     * MOCK IMPLEMENTATION: Simulates calling a vehicle details API.
     * In a real application, this method would use HttpClient to make a real network request.
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

        // Return mock data for demo.
        String mockJsonResponse = "{\"ownerName\":\"Manav Vashistha\",\"vehicleModel\":\"Maruti Swift\",\"registrationDate\":\"2023-04-15\"}";
        return gson.fromJson(mockJsonResponse, VehicleDetails.class);
    }
}