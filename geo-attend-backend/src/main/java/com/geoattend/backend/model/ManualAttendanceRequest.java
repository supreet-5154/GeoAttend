package com.geoattend.backend.model;

public class ManualAttendanceRequest {

    private Long sessionId;
    private Long memberId;

    public ManualAttendanceRequest() {
    }

    public ManualAttendanceRequest(Long sessionId, Long memberId) {
        this.sessionId = sessionId;
        this.memberId = memberId;
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
}