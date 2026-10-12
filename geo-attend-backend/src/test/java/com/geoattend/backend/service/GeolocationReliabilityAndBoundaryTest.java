package com.geoattend.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GeolocationReliabilityAndBoundaryTest {

    private static final double VENUE_RADIUS = 50.0;

    @Test
    @DisplayName("Clearly inside geofence with high accuracy is accepted")
    void clearlyInsideGeofenceWithHighAccuracy() {
        double distance = 20.0;
        double accuracy = 8.5; // High accuracy (<= 30m)

        AttendanceTrackingPolicy.GeofenceEvaluation evaluation =
                AttendanceTrackingPolicy.evaluateGeofencePresence(distance, VENUE_RADIUS, accuracy);

        assertTrue(evaluation.insideGeofence());
        assertEquals("HIGH", evaluation.accuracyStatus());
        assertEquals(0.0, evaluation.boundaryToleranceMeters());
        assertTrue(evaluation.evaluationReason().contains("Device is inside geofence"));
    }

    @Test
    @DisplayName("Clearly outside geofence is rejected regardless of high accuracy")
    void clearlyOutsideGeofenceIsRejected() {
        double distance = 150.0;
        double accuracy = 10.0;

        AttendanceTrackingPolicy.GeofenceEvaluation evaluation =
                AttendanceTrackingPolicy.evaluateGeofencePresence(distance, VENUE_RADIUS, accuracy);

        assertFalse(evaluation.insideGeofence());
        assertEquals("HIGH", evaluation.accuracyStatus());
        assertTrue(evaluation.evaluationReason().contains("Device is outside geofence"));
    }

    @Test
    @DisplayName("Near boundary: within calculated GPS uncertainty margin is accepted with tolerance note")
    void nearBoundaryWithinUncertaintyToleranceIsAccepted() {
        // Radius = 50.0m. Accuracy = 20.0m.
        // Calculated tolerance = min(10.0, 0.25 * 20.0) = 5.0m.
        // Effective perimeter = 55.0m.
        double distance = 53.0; // 3m outside nominal fence, but within 5m uncertainty
        double accuracy = 20.0;

        AttendanceTrackingPolicy.GeofenceEvaluation evaluation =
                AttendanceTrackingPolicy.evaluateGeofencePresence(distance, VENUE_RADIUS, accuracy);

        assertTrue(evaluation.insideGeofence());
        assertEquals("HIGH", evaluation.accuracyStatus());
        assertEquals(5.0, evaluation.boundaryToleranceMeters());
        assertTrue(evaluation.evaluationReason().contains("within allowable GPS uncertainty margin"));
    }

    @Test
    @DisplayName("Near boundary: beyond calculated uncertainty margin is rejected")
    void nearBoundaryBeyondToleranceIsRejected() {
        // Radius = 50.0m. Accuracy = 20.0m. Tolerance = 5.0m. Effective perimeter = 55.0m.
        double distance = 58.0; // 8m outside nominal fence
        double accuracy = 20.0;

        AttendanceTrackingPolicy.GeofenceEvaluation evaluation =
                AttendanceTrackingPolicy.evaluateGeofencePresence(distance, VENUE_RADIUS, accuracy);

        assertFalse(evaluation.insideGeofence());
        assertEquals("HIGH", evaluation.accuracyStatus());
        assertEquals(5.0, evaluation.boundaryToleranceMeters());
        assertTrue(evaluation.evaluationReason().contains("Device is outside geofence"));
    }

    @Test
    @DisplayName("Boundary tolerance is strictly capped at maximum 10.0 meters even with higher accuracy uncertainty")
    void boundaryToleranceStrictlyCapped() {
        double accuracy = 80.0; // Acceptable indoor accuracy (<= 100m)
        double tolerance = AttendanceTrackingPolicy.calculateBoundaryTolerance(accuracy);

        // 0.25 * 80.0 = 20.0m, but capped at 10.0m
        assertEquals(10.0, tolerance);

        // Distance = 58m on 50m radius (within 50 + 10 = 60m)
        AttendanceTrackingPolicy.GeofenceEvaluation evaluation =
                AttendanceTrackingPolicy.evaluateGeofencePresence(58.0, VENUE_RADIUS, accuracy);
        assertTrue(evaluation.insideGeofence());
        assertEquals("ACCEPTABLE", evaluation.accuracyStatus());
        assertEquals(10.0, evaluation.boundaryToleranceMeters());

        // Distance = 62m on 50m radius (beyond 60m cap)
        AttendanceTrackingPolicy.GeofenceEvaluation beyondCap =
                AttendanceTrackingPolicy.evaluateGeofencePresence(62.0, VENUE_RADIUS, accuracy);
        assertFalse(beyondCap.insideGeofence());
    }

    @Test
    @DisplayName("Poor reported GPS accuracy (>100m) is rejected even when coordinates are inside nominal center")
    void poorReportedGpsAccuracyRejectedEvenInside() {
        // Device claims to be 15m from center, but reported GPS accuracy is +/- 350m (Cell tower/IP lookup)
        double distance = 15.0;
        double accuracy = 350.0;

        AttendanceTrackingPolicy.GeofenceEvaluation evaluation =
                AttendanceTrackingPolicy.evaluateGeofencePresence(distance, VENUE_RADIUS, accuracy);

        assertFalse(evaluation.insideGeofence(), "Poor accuracy reading must not be treated as a valid inside check");
        assertEquals("POOR_ACCURACY", evaluation.accuracyStatus());
        assertEquals(0.0, evaluation.boundaryToleranceMeters());
        assertTrue(evaluation.evaluationReason().contains("exceeds maximum acceptable threshold"));
    }

    @Test
    @DisplayName("Missing or null GPS accuracy is rejected")
    void nullAccuracyRejected() {
        AttendanceTrackingPolicy.GeofenceEvaluation evaluation =
                AttendanceTrackingPolicy.evaluateGeofencePresence(10.0, VENUE_RADIUS, null);

        assertFalse(evaluation.insideGeofence());
        assertEquals("POOR_ACCURACY", evaluation.accuracyStatus());
        assertTrue(evaluation.evaluationReason().contains("GPS accuracy unavailable"));
    }

    @Test
    @DisplayName("Preserves 80% threshold rule across full 10-slot intervals")
    void preservesThresholdRule() {
        assertTrue(AttendanceTrackingPolicy.meetsAttendanceThreshold(8, 10));
        assertTrue(AttendanceTrackingPolicy.meetsAttendanceThreshold(9, 10));
        assertTrue(AttendanceTrackingPolicy.meetsAttendanceThreshold(10, 10));
        assertFalse(AttendanceTrackingPolicy.meetsAttendanceThreshold(7, 10)); // 70% < 80%
        assertFalse(AttendanceTrackingPolicy.meetsAttendanceThreshold(0, 10));
    }
}
