package com.geoattend.backend.model;

import java.time.LocalDateTime;

public class AttendanceLocationAudit {

    private Long trackingId;
    private Integer checkNumber;
    private LocalDateTime checkedAt;
    private String memberName;
    private String sessionTitle;
    private Double latitude;
    private Double longitude;
    private Double accuracyMeters;
    private Double distanceMeters;
    private boolean insideGeofence;

    public Long getTrackingId() { return trackingId; }
    public void setTrackingId(Long trackingId) { this.trackingId = trackingId; }
    public Integer getCheckNumber() { return checkNumber; }
    public void setCheckNumber(Integer checkNumber) { this.checkNumber = checkNumber; }
    public LocalDateTime getCheckedAt() { return checkedAt; }
    public void setCheckedAt(LocalDateTime checkedAt) { this.checkedAt = checkedAt; }
    public String getMemberName() { return memberName; }
    public void setMemberName(String memberName) { this.memberName = memberName; }
    public String getSessionTitle() { return sessionTitle; }
    public void setSessionTitle(String sessionTitle) { this.sessionTitle = sessionTitle; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public Double getAccuracyMeters() { return accuracyMeters; }
    public void setAccuracyMeters(Double accuracyMeters) { this.accuracyMeters = accuracyMeters; }
    public Double getDistanceMeters() { return distanceMeters; }
    public void setDistanceMeters(Double distanceMeters) { this.distanceMeters = distanceMeters; }
    public boolean isInsideGeofence() { return insideGeofence; }
    public void setInsideGeofence(boolean insideGeofence) { this.insideGeofence = insideGeofence; }
}