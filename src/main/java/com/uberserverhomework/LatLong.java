package com.uberserverhomework;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class LatLong {
  private double latitude;
  private double longitude;
  @JsonIgnore
  private double type;


  LatLong() {}


  public double getType() {
    return type;
  }

  public void setType(double type) {
    this.type = type;
  }

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
}
