package com.geoattend.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttendanceTrackingServiceReliabilityTest {

    @Mock
    private AttendanceTrackingRepository trackingRepository;

    @Mock
    private AttendanceSessionRepository sessionRepository;

    @Mock
    private AttendanceRepository attendanceRepository;

    private AttendanceTrackingService trackingService;

    private final Long memberId = 42L;
    private final Long sessionId = 101L;
    private AttendanceSession activeSession;

    @BeforeEach
    void setUp() {
        trackingService = new AttendanceTrackingService(trackingRepository, sessionRepository, attendanceRepository);

        activeSession = new AttendanceSession();
        activeSession.setId(sessionId);
        activeSession.setTitle("Oyster Kode General Meeting");
        // Active from 30 mins ago to 30 mins in future
        activeSession.setStartTime(LocalDateTime.now().minusMinutes(30));
        activeSession.setEndTime(LocalDateTime.now().plusMinutes(30));
        activeSession.setLatitude(12.9715987); // Sample venue coords
        activeSession.setLongitude(77.5945627);
        activeSession.setRadiusMeters(50);
    }

    @Test
    @DisplayName("Clearly inside geofence with high accuracy records valid inside sample")
    void recordCheckClearlyInside() {
        when(sessionRepository.sessionExists(sessionId)).thenReturn(true);
        when(sessionRepository.getSessionById(sessionId)).thenReturn(activeSession);
        when(attendanceRepository.attendanceExists(memberId, sessionId)).thenReturn(false);

        AttendanceTracking tracking = new AttendanceTracking();
        tracking.setId(1L);
        tracking.setMemberId(memberId);
        tracking.setSessionId(sessionId);
        tracking.setStartedAt(activeSession.getStartTime());
        tracking.setLastCheckAt(activeSession.getStartTime());
        tracking.setTotalChecks(0);
        tracking.setInsideChecks(0);
        tracking.setStatus("WAITING_FOR_GEOFENCE");

        when(trackingRepository.findByMemberAndSession(memberId, sessionId)).thenReturn(tracking);

        LocationCheckRequest request = new LocationCheckRequest();
        request.setSessionId(sessionId);
        request.setLatitude(12.9715987); // Exact venue center
        request.setLongitude(77.5945627);
        request.setAccuracyMeters(12.0); // High accuracy

        LocationCheckResult result = trackingService.recordLocationCheck(memberId, request);

        assertTrue(result.isInsideGeofence());
        assertTrue(result.isSampleRecorded());
        assertEquals("HIGH", result.getAccuracyStatus());
        assertEquals(0.0, result.getBoundaryToleranceMeters());

        verify(trackingRepository).recordLocationCheck(
                eq(1L), anyInt(), any(LocalDateTime.class),
                eq(12.9715987), eq(77.5945627), eq(12.0),
                anyDouble(), eq(true), eq("HIGH"), eq(0.0), anyString());
    }

    @Test
    @DisplayName("Clearly outside geofence records outside sample without incrementing inside checks")
    void recordCheckClearlyOutside() {
        when(sessionRepository.sessionExists(sessionId)).thenReturn(true);
        when(sessionRepository.getSessionById(sessionId)).thenReturn(activeSession);
        when(attendanceRepository.attendanceExists(memberId, sessionId)).thenReturn(false);

        AttendanceTracking tracking = new AttendanceTracking();
        tracking.setId(1L);
        tracking.setMemberId(memberId);
        tracking.setSessionId(sessionId);
        tracking.setStartedAt(activeSession.getStartTime());
        tracking.setLastCheckAt(activeSession.getStartTime());
        tracking.setTotalChecks(0);
        tracking.setInsideChecks(0);
        tracking.setStatus("WAITING_FOR_GEOFENCE");

        when(trackingRepository.findByMemberAndSession(memberId, sessionId)).thenReturn(tracking);

        LocationCheckRequest request = new LocationCheckRequest();
        request.setSessionId(sessionId);
        // Approx 500 meters away
        request.setLatitude(12.9760000);
        request.setLongitude(77.5945627);
        request.setAccuracyMeters(10.0);

        LocationCheckResult result = trackingService.recordLocationCheck(memberId, request);

        assertFalse(result.isInsideGeofence());
        assertTrue(result.isSampleRecorded());

        verify(trackingRepository).recordLocationCheck(
                eq(1L), anyInt(), any(LocalDateTime.class),
                anyDouble(), anyDouble(), eq(10.0),
                anyDouble(), eq(false), eq("HIGH"), anyDouble(), anyString());
    }

    @Test
    @DisplayName("Poor reported GPS accuracy (>100m) is rejected from inside check and flagged as POOR_ACCURACY")
    void recordCheckPoorAccuracyRejected() {
        when(sessionRepository.sessionExists(sessionId)).thenReturn(true);
        when(sessionRepository.getSessionById(sessionId)).thenReturn(activeSession);
        when(attendanceRepository.attendanceExists(memberId, sessionId)).thenReturn(false);

        AttendanceTracking tracking = new AttendanceTracking();
        tracking.setId(1L);
        tracking.setMemberId(memberId);
        tracking.setSessionId(sessionId);
        tracking.setStartedAt(activeSession.getStartTime());
        tracking.setLastCheckAt(activeSession.getStartTime());
        tracking.setTotalChecks(0);
        tracking.setInsideChecks(0);
        tracking.setStatus("WAITING_FOR_GEOFENCE");

        when(trackingRepository.findByMemberAndSession(memberId, sessionId)).thenReturn(tracking);

        LocationCheckRequest request = new LocationCheckRequest();
        request.setSessionId(sessionId);
        // Device coordinates are physically inside, but accuracy is +/- 250m
        request.setLatitude(12.9715987);
        request.setLongitude(77.5945627);
        request.setAccuracyMeters(250.0);

        LocationCheckResult result = trackingService.recordLocationCheck(memberId, request);

        assertFalse(result.isInsideGeofence(), "Poor accuracy reading must not count as inside");
        assertEquals("POOR_ACCURACY", result.getAccuracyStatus());
        assertTrue(result.getEvaluationReason().contains("exceeds maximum acceptable threshold"));

        verify(trackingRepository).recordLocationCheck(
                eq(1L), anyInt(), any(LocalDateTime.class),
                anyDouble(), anyDouble(), eq(250.0),
                anyDouble(), eq(false), eq("POOR_ACCURACY"), eq(0.0), anyString());
    }

    @Test
    @DisplayName("Repeated checks within same interval slot are idempotent and do not double-record")
    void repeatedCheckInSameSlotIsIdempotent() {
        when(sessionRepository.sessionExists(sessionId)).thenReturn(true);
        when(sessionRepository.getSessionById(sessionId)).thenReturn(activeSession);
        when(attendanceRepository.attendanceExists(memberId, sessionId)).thenReturn(false);

        AttendanceTracking tracking = new AttendanceTracking();
        tracking.setId(1L);
        tracking.setMemberId(memberId);
        tracking.setSessionId(sessionId);
        LocalDateTime now = LocalDateTime.now();
        tracking.setStartedAt(now.minusSeconds(100));
        // Last check recorded 20 seconds ago within the same 360-second slot 0
        tracking.setLastCheckAt(now.minusSeconds(20));
        tracking.setTotalChecks(1);
        tracking.setInsideChecks(1);
        tracking.setStatus("TRACKING");

        when(trackingRepository.findByMemberAndSession(memberId, sessionId)).thenReturn(tracking);

        LocationCheckRequest request = new LocationCheckRequest();
        request.setSessionId(sessionId);
        request.setLatitude(12.9715987);
        request.setLongitude(77.5945627);
        request.setAccuracyMeters(15.0);

        LocationCheckResult result = trackingService.recordLocationCheck(memberId, request);

        assertFalse(result.isSampleRecorded(), "Should not record duplicate sample in same interval slot");
        assertEquals("TRACKING", result.getStatus());
        assertEquals(1, result.getTotalChecks());
        assertNotNull(result.getNextCheckAt());
        assertTrue(result.getEvaluationReason().contains("already recorded"));

        // Verify no write was executed on repository
        verify(trackingRepository, never()).recordLocationCheck(
                anyLong(), anyInt(), any(LocalDateTime.class),
                anyDouble(), anyDouble(), any(), anyDouble(), anyBoolean(), any(), any(), any());
    }

    @Test
    @DisplayName("Location check rejected when session has already ended")
    void locationCheckRejectedWhenSessionEnded() {
        // Set session end in the past
        activeSession.setEndTime(LocalDateTime.now().minusMinutes(5));

        when(sessionRepository.sessionExists(sessionId)).thenReturn(true);
        when(sessionRepository.getSessionById(sessionId)).thenReturn(activeSession);

        LocationCheckRequest request = new LocationCheckRequest();
        request.setSessionId(sessionId);
        request.setLatitude(12.9715987);
        request.setLongitude(77.5945627);
        request.setAccuracyMeters(10.0);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                trackingService.recordLocationCheck(memberId, request));

        assertTrue(exception.getMessage().contains("active session"));
    }

    @Test
    @DisplayName("Manual attendance override locks out further automated location checks")
    void manualAttendanceOverrideLocksLocationChecks() {
        when(sessionRepository.sessionExists(sessionId)).thenReturn(true);
        when(sessionRepository.getSessionById(sessionId)).thenReturn(activeSession);
        // Manual attendance already exists for this member
        when(attendanceRepository.attendanceExists(memberId, sessionId)).thenReturn(true);

        LocationCheckRequest request = new LocationCheckRequest();
        request.setSessionId(sessionId);
        request.setLatitude(12.9715987);
        request.setLongitude(77.5945627);
        request.setAccuracyMeters(10.0);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                trackingService.recordLocationCheck(memberId, request));

        assertTrue(exception.getMessage().contains("already been finalized"));
    }

    @Test
    @DisplayName("Finalization respects staff manual override and marks MANUAL_OVERRIDE instead of reviewing")
    void finalizationRespectsManualOverride() {
        AttendanceTracking tracking = new AttendanceTracking();
        tracking.setId(10L);
        tracking.setMemberId(memberId);
        tracking.setSessionId(sessionId);
        tracking.setStartedAt(activeSession.getStartTime());
        tracking.setSessionStartTime(activeSession.getStartTime());
        tracking.setSessionEndTime(activeSession.getEndTime());
        tracking.setInsideChecks(1); // Would fail 80% threshold normally
        tracking.setTotalChecks(10);
        tracking.setStatus("TRACKING");

        when(trackingRepository.findDueForFinalization(any(LocalDateTime.class)))
                .thenReturn(List.of(tracking));
        // Manual attendance exists
        when(attendanceRepository.attendanceExists(memberId, sessionId)).thenReturn(true);

        trackingService.finalizeEndedSessions();

        verify(trackingRepository).updateStatus(eq(10L), eq("MANUAL_OVERRIDE"), any(LocalDateTime.class));
        verify(attendanceRepository, never()).markAttendance(any());
    }

    @Test
    @DisplayName("Invalid coordinates (out of range, NaN, null) are rejected safely")
    void invalidCoordinatesRejected() {
        LocationCheckRequest nullLat = new LocationCheckRequest();
        nullLat.setSessionId(sessionId);
        nullLat.setLatitude(null);
        nullLat.setLongitude(77.5945627);

        assertThrows(IllegalArgumentException.class, () ->
                trackingService.recordLocationCheck(memberId, nullLat));

        LocationCheckRequest outOfRangeLat = new LocationCheckRequest();
        outOfRangeLat.setSessionId(sessionId);
        outOfRangeLat.setLatitude(195.0); // > 90
        outOfRangeLat.setLongitude(77.5945627);

        assertThrows(IllegalArgumentException.class, () ->
                trackingService.recordLocationCheck(memberId, outOfRangeLat));

        LocationCheckRequest negativeAccuracy = new LocationCheckRequest();
        negativeAccuracy.setSessionId(sessionId);
        negativeAccuracy.setLatitude(12.9715987);
        negativeAccuracy.setLongitude(77.5945627);
        negativeAccuracy.setAccuracyMeters(-5.0);

        assertThrows(IllegalArgumentException.class, () ->
                trackingService.recordLocationCheck(memberId, negativeAccuracy));
    }
}
