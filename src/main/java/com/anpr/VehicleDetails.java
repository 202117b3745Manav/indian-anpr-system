package com.anpr;

public class VehicleDetails {
    
    private String ownerName;
    private String vehicleModel;
    private String registrationDate;

    public String getOwnerName() {
        return ownerName;
    }

    public String getVehicleModel() {
        return vehicleModel;
    }

    @Override
    public String toString() {
        return "Owner: " + ownerName + "\nVehicle: " + vehicleModel + "\nReg. Date: " + registrationDate;
    }
}