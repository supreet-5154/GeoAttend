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

    // Joined / projection fields for efficient client-side consumption
    private String sessionTitle;
    private String memberName;
    private String studentId;
    private Long batchId;
    private String batchName;
    private String trackingStatus;

    public Attendance(Long id, Long sessionId, Long memberId, LocalDateTime markedAt, Double latitude, Double longitude, String attendanceMethod) {
        this.id = id;
        this.sessionId = sessionId;
        this.memberId = memberId;
        this.markedAt = markedAt;
        this.latitude = latitude;
        this.longitude = longitude;
        this.attendanceMethod = attendanceMethod;
    }

    public Attendance(Long id, Long sessionId, Long memberId, LocalDateTime markedAt, Double latitude, Double longitude, String attendanceMethod,
                      String sessionTitle, String memberName, String studentId, Long batchId, String batchName, String trackingStatus) {
        this(id, sessionId, memberId, markedAt, latitude, longitude, attendanceMethod);
        this.sessionTitle = sessionTitle;
        this.memberName = memberName;
        this.studentId = studentId;
        this.batchId = batchId;
        this.batchName = batchName;
        this.trackingStatus = trackingStatus;
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

    public String getSessionTitle() {
        return sessionTitle;
    }

    public void setSessionTitle(String sessionTitle) {
        this.sessionTitle = sessionTitle;
    }

    public String getMemberName() {
        return memberName;
    }

    public void setMemberName(String memberName) {
        this.memberName = memberName;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public String getBatchName() {
        return batchName;
    }

    public void setBatchName(String batchName) {
        this.batchName = batchName;
    }

    public String getTrackingStatus() {
        return trackingStatus;
    }

    public void setTrackingStatus(String trackingStatus) {
        this.trackingStatus = trackingStatus;
    }
}