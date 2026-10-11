package com.geoattend.backend.model;

import java.time.LocalDateTime;

public class Attendance {

    private Long id;
    private Long sessionId;
    private Long memberId;
    private LocalDateTime markedAt;
    private Double latitude;
    private Double longitude;
    private String attendanceMethod;

    public Attendance(Long id, Long sessionId, Long memberId, LocalDateTime markedAt, Double latitude, Double longitude, String attendanceMethod) {
        this.id = id;
        this.sessionId = sessionId;
        this.memberId = memberId;
        this.markedAt = markedAt;
        this.latitude = latitude;
        this.longitude = longitude;
        this.attendanceMethod = attendanceMethod;
    }

    public Attendance() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public LocalDateTime getMarkedAt() {
        return markedAt;
    }

    public void setMarkedAt(LocalDateTime markedAt) {
        this.markedAt = markedAt;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getAttendanceMethod() {
        return attendanceMethod;
    }

    public void setAttendanceMethod(String attendanceMethod) {
        this.attendanceMethod = attendanceMethod;
    }
}