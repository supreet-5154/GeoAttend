package com.geoattend.backend.model;

import java.time.LocalDateTime;

public class LocationCheckResult {

    private boolean insideGeofence;
    private boolean trackingStarted;
    private boolean sampleRecorded;
    private String status;
    private Integer totalChecks;
    private Integer insideChecks;
    private LocalDateTime nextCheckAt;
    private Double distanceMeters;
    private Double accuracyMeters;
    private Integer radiusMeters;
    private Integer checkIntervalSeconds;

    public boolean isInsideGeofence() { return insideGeofence; }
    public void setInsideGeofence(boolean insideGeofence) { this.insideGeofence = insideGeofence; }
    public boolean isTrackingStarted() { return trackingStarted; }
    public void setTrackingStarted(boolean trackingStarted) { this.trackingStarted = trackingStarted; }
    public boolean isSampleRecorded() { return sampleRecorded; }
    public void setSampleRecorded(boolean sampleRecorded) { this.sampleRecorded = sampleRecorded; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getTotalChecks() { return totalChecks; }
    public void setTotalChecks(Integer totalChecks) { this.totalChecks = totalChecks; }
    public Integer getInsideChecks() { return insideChecks; }
    public void setInsideChecks(Integer insideChecks) { this.insideChecks = insideChecks; }
    public LocalDateTime getNextCheckAt() { return nextCheckAt; }
    public void setNextCheckAt(LocalDateTime nextCheckAt) { this.nextCheckAt = nextCheckAt; }
    public Double getDistanceMeters() { return distanceMeters; }
    public void setDistanceMeters(Double distanceMeters) { this.distanceMeters = distanceMeters; }
    public Double getAccuracyMeters() { return accuracyMeters; }
    public void setAccuracyMeters(Double accuracyMeters) { this.accuracyMeters = accuracyMeters; }
    public Integer getRadiusMeters() { return radiusMeters; }
    public void setRadiusMeters(Integer radiusMeters) { this.radiusMeters = radiusMeters; }
    public Integer getCheckIntervalSeconds() { return checkIntervalSeconds; }
    public void setCheckIntervalSeconds(Integer checkIntervalSeconds) { this.checkIntervalSeconds = checkIntervalSeconds; }
}