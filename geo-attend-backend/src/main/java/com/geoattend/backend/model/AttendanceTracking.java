package com.geoattend.backend.model;

import java.time.LocalDateTime;

public class AttendanceTracking {

    private Long id;
    private Long sessionId;
    private Long memberId;
    private String memberName;
    private String sessionTitle;
    private LocalDateTime sessionStartTime;
    private LocalDateTime sessionEndTime;
    private LocalDateTime startedAt;
    private LocalDateTime lastCheckAt;
    private Integer totalChecks;
    private Integer insideChecks;
    private Integer expectedChecks;
    private Integer checkIntervalSeconds;
    private String status;
    private LocalDateTime finalizedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
    public String getMemberName() { return memberName; }
    public void setMemberName(String memberName) { this.memberName = memberName; }
    public String getSessionTitle() { return sessionTitle; }
    public void setSessionTitle(String sessionTitle) { this.sessionTitle = sessionTitle; }
    public LocalDateTime getSessionStartTime() { return sessionStartTime; }
    public void setSessionStartTime(LocalDateTime sessionStartTime) { this.sessionStartTime = sessionStartTime; }
    public LocalDateTime getSessionEndTime() { return sessionEndTime; }
    public void setSessionEndTime(LocalDateTime sessionEndTime) { this.sessionEndTime = sessionEndTime; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getLastCheckAt() { return lastCheckAt; }
    public void setLastCheckAt(LocalDateTime lastCheckAt) { this.lastCheckAt = lastCheckAt; }
    public Integer getTotalChecks() { return totalChecks; }
    public void setTotalChecks(Integer totalChecks) { this.totalChecks = totalChecks; }
    public Integer getInsideChecks() { return insideChecks; }
    public void setInsideChecks(Integer insideChecks) { this.insideChecks = insideChecks; }
    public Integer getExpectedChecks() { return expectedChecks; }
    public void setExpectedChecks(Integer expectedChecks) { this.expectedChecks = expectedChecks; }
    public Integer getCheckIntervalSeconds() { return checkIntervalSeconds; }
    public void setCheckIntervalSeconds(Integer checkIntervalSeconds) { this.checkIntervalSeconds = checkIntervalSeconds; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getFinalizedAt() { return finalizedAt; }
    public void setFinalizedAt(LocalDateTime finalizedAt) { this.finalizedAt = finalizedAt; }
}