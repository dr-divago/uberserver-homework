package com.uberserverhomework.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

public class LatLong {
    @NotNull(message = "Name cannot be null")
    @Min(value = -90, message = "Latitude shouldn't be less then -90")
    @Max(value = 90, message = "Latitude shouldn't be more then +90")
    private double latitude;

    @NotNull(message = "Name cannot be null")
    @Min(value = -180, message = "Latitude shouldn't be less then -180")
    @Max(value = 180, message = "Latitude shouldn't be more then +180")
    private double longitude;

    @JsonIgnore
    private String type;

    public LatLong() {}

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
