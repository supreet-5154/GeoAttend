package com.geoattend.backend.service;

import com.geoattend.backend.model.Attendance;
import com.geoattend.backend.model.AttendanceLocationAudit;
import com.geoattend.backend.model.AttendanceSession;
import com.geoattend.backend.model.AttendanceTracking;
import com.geoattend.backend.model.LocationCheckRequest;
import com.geoattend.backend.model.LocationCheckResult;
import com.geoattend.backend.repository.AttendanceRepository;
import com.geoattend.backend.repository.AttendanceSessionRepository;
import com.geoattend.backend.repository.AttendanceTrackingRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttendanceTrackingService {

    private final AttendanceTrackingRepository trackingRepository;
    private final AttendanceSessionRepository sessionRepository;
    private final AttendanceRepository attendanceRepository;

    public AttendanceTrackingService(
            AttendanceTrackingRepository trackingRepository,
            AttendanceSessionRepository sessionRepository,
            AttendanceRepository attendanceRepository) {
        this.trackingRepository = trackingRepository;
        this.sessionRepository = sessionRepository;
        this.attendanceRepository = attendanceRepository;
    }

    @Transactional
    public AttendanceTracking startTracking(Long memberId, Long sessionId) {
        if (sessionId == null || !sessionRepository.sessionExists(sessionId)) {
            throw new IllegalArgumentException("Attendance session does not exist.");
        }
        AttendanceSession session = sessionRepository.getSessionById(sessionId);
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(session.getStartTime()) || !now.isBefore(session.getEndTime())) {
            throw new IllegalArgumentException("Tracking can only start during an active session.");
        }
        if (!session.isLocationEnabled()) {
            throw new IllegalArgumentException("This session does not have a geofence configured.");
        }
        if (attendanceRepository.attendanceExists(memberId, sessionId)) {
            throw new IllegalArgumentException("Attendance for this session has already been finalized.");
        }

        AttendanceTracking tracking = trackingRepository.findByMemberAndSession(memberId, sessionId);
        if (tracking == null) {
            trackingRepository.startTracking(memberId, sessionId, now);
            tracking = trackingRepository.findByMemberAndSession(memberId, sessionId);
        }
        return tracking;
    }

    @Transactional
    public LocationCheckResult recordLocationCheck(Long memberId, LocationCheckRequest request) {
        validateCoordinates(request.getLatitude(), request.getLongitude());
        validateAccuracy(request.getAccuracyMeters());
        if (request.getSessionId() == null || !sessionRepository.sessionExists(request.getSessionId())) {
            throw new IllegalArgumentException("Attendance session does not exist.");
        }

        AttendanceSession session = sessionRepository.getSessionById(request.getSessionId());
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(session.getStartTime()) || !now.isBefore(session.getEndTime())) {
            throw new IllegalArgumentException("Location checks are only accepted during an active session.");
        }
        if (!session.isLocationEnabled()) {
            throw new IllegalArgumentException("This session does not have a geofence configured.");
        }
        if (attendanceRepository.attendanceExists(memberId, session.getId())) {
            throw new IllegalArgumentException("Attendance for this session has already been finalized.");
        }

        double distanceMeters = calculateDistance(
                session.getLatitude(), session.getLongitude(),
                request.getLatitude(), request.getLongitude()
        );
        boolean inside = distanceMeters <= session.getRadiusMeters();
        int checkIntervalSeconds = AttendanceTrackingPolicy.checkIntervalSeconds(
            session.getStartTime(), session.getEndTime());

        AttendanceTracking tracking = trackingRepository.findByMemberAndSession(memberId, session.getId());
        boolean trackingWasMissing = tracking == null;
        if (trackingWasMissing) {
            tracking = startTracking(memberId, session.getId());
        }

        if (!"TRACKING".equals(tracking.getStatus())
                && !"WAITING_FOR_GEOFENCE".equals(tracking.getStatus())) {
            throw new IllegalArgumentException("Location tracking for this session is already finalized.");
        }

        long elapsedSeconds = java.time.Duration.between(tracking.getStartedAt(), now).getSeconds();
        int checkNumber = (int) (elapsedSeconds / checkIntervalSeconds);
        int lastCheckNumber = tracking.getTotalChecks() == 0
                ? -1
            : (int) (java.time.Duration.between(
                tracking.getStartedAt(), tracking.getLastCheckAt()).getSeconds() / checkIntervalSeconds);

        LocationCheckResult result = new LocationCheckResult();
        result.setInsideGeofence(inside);
        result.setDistanceMeters(roundMeters(distanceMeters));
        result.setAccuracyMeters(request.getAccuracyMeters() == null ? null : roundMeters(request.getAccuracyMeters()));
        result.setRadiusMeters(session.getRadiusMeters());
        result.setCheckIntervalSeconds(checkIntervalSeconds);
        if (checkNumber <= lastCheckNumber) {
            result.setStatus(tracking.getStatus());
            result.setTotalChecks(tracking.getTotalChecks());
            result.setInsideChecks(tracking.getInsideChecks());
            result.setNextCheckAt(tracking.getLastCheckAt().plusSeconds(checkIntervalSeconds));
            return result;
        }

        trackingRepository.recordLocationCheck(
            tracking.getId(), checkNumber, now,
            request.getLatitude(), request.getLongitude(), request.getAccuracyMeters(),
            distanceMeters, inside);
        tracking = trackingRepository.findByMemberAndSession(memberId, session.getId());
        result.setTrackingStarted(trackingWasMissing && inside);
        result.setSampleRecorded(true);
        result.setStatus(tracking.getStatus());
        result.setTotalChecks(tracking.getTotalChecks());
        result.setInsideChecks(tracking.getInsideChecks());
        result.setNextCheckAt(tracking.getLastCheckAt().plusSeconds(checkIntervalSeconds));
        return result;
    }

    public List<AttendanceTracking> getMyTracking(Long memberId) {
        List<AttendanceTracking> trackingList = trackingRepository.findByMember(memberId);
        trackingList.forEach(this::setExpectedChecks);
        return trackingList;
    }

    public List<AttendanceTracking> getSessionTracking(Long sessionId) {
        List<AttendanceTracking> trackingList = trackingRepository.findBySession(sessionId);
        trackingList.forEach(this::setExpectedChecks);
        return trackingList;
    }

    public List<AttendanceLocationAudit> getSessionLocationAudit(Long sessionId) {
        return trackingRepository.findLocationAuditBySession(sessionId);
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 10000)
    public void finalizeEndedSessions() {
        LocalDateTime now = LocalDateTime.now();
        for (AttendanceTracking tracking : trackingRepository.findDueForFinalization(now)) {
            try {
                finalizeTracking(tracking, now);
            } catch (RuntimeException exception) {
                // Leave the row pending so the next scheduled pass can retry.
            }
        }
    }

    private void finalizeTracking(AttendanceTracking tracking, LocalDateTime finalizedAt) {
        if (attendanceRepository.attendanceExists(tracking.getMemberId(), tracking.getSessionId())) {
            trackingRepository.updateStatus(tracking.getId(), "MANUAL_OVERRIDE", finalizedAt);
            return;
        }

        int expectedChecks = AttendanceTrackingPolicy.expectedChecks(
            tracking.getStartedAt(), tracking.getSessionStartTime(), tracking.getSessionEndTime());
        boolean meetsThreshold = AttendanceTrackingPolicy.meetsAttendanceThreshold(
                tracking.getInsideChecks(), expectedChecks);

        if (!meetsThreshold) {
            trackingRepository.updateStatus(tracking.getId(), "REVIEW", finalizedAt);
            return;
        }

        Attendance attendance = new Attendance();
        attendance.setSessionId(tracking.getSessionId());
        attendance.setMemberId(tracking.getMemberId());
        attendance.setMarkedAt(tracking.getSessionEndTime());
        attendance.setLatitude(null);
        attendance.setLongitude(null);
        attendance.setAttendanceMethod("GEOFENCE");
        attendanceRepository.markAttendance(attendance);
        trackingRepository.updateStatus(tracking.getId(), "PRESENT", finalizedAt);
    }

    private void setExpectedChecks(AttendanceTracking tracking) {
        tracking.setCheckIntervalSeconds(AttendanceTrackingPolicy.checkIntervalSeconds(
            tracking.getSessionStartTime(), tracking.getSessionEndTime()));
        boolean isActive = "TRACKING".equals(tracking.getStatus())
                || "WAITING_FOR_GEOFENCE".equals(tracking.getStatus());
        LocalDateTime until = isActive && LocalDateTime.now().isBefore(tracking.getSessionEndTime())
                ? LocalDateTime.now() : tracking.getSessionEndTime();
        tracking.setExpectedChecks(AttendanceTrackingPolicy.expectedChecks(
                tracking.getStartedAt(), tracking.getSessionStartTime(), until));
    }

    private void validateAccuracy(Double accuracyMeters) {
        if (accuracyMeters != null && (!Double.isFinite(accuracyMeters) || accuracyMeters < 0)) {
            throw new IllegalArgumentException("The browser reported an invalid location accuracy.");
        }
    }

    private double roundMeters(double meters) {
        return Math.round(meters * 10.0) / 10.0;
    }

    private void validateCoordinates(Double latitude, Double longitude) {
        if (latitude == null || longitude == null
                || !Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90
                || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("A valid device location is required.");
        }
    }

    private double calculateDistance(double latitudeOne, double longitudeOne,
            double latitudeTwo, double longitudeTwo) {
        final double earthRadiusMeters = 6_371_000;
        double latitudeDifference = Math.toRadians(latitudeTwo - latitudeOne);
        double longitudeDifference = Math.toRadians(longitudeTwo - longitudeOne);
        double haversine = Math.sin(latitudeDifference / 2) * Math.sin(latitudeDifference / 2)
                + Math.cos(Math.toRadians(latitudeOne)) * Math.cos(Math.toRadians(latitudeTwo))
                * Math.sin(longitudeDifference / 2) * Math.sin(longitudeDifference / 2);
        return earthRadiusMeters * 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
    }
}