package com.geoattend.backend.service;

import java.time.Duration;
import java.time.LocalDateTime;

public final class AttendanceTrackingPolicy {

    private static final int CHECK_SLOT_COUNT = 10;
    private static final int MINIMUM_PERCENT_PRESENT = 80;

    private AttendanceTrackingPolicy() {
    }

    public static final double MAX_ACCEPTABLE_ACCURACY_METERS = 100.0;
    public static final double HIGH_ACCURACY_THRESHOLD_METERS = 30.0;
    public static final double MAX_BOUNDARY_TOLERANCE_METERS = 10.0;
    public static final double BOUNDARY_TOLERANCE_RATIO = 0.25;

    public record GeofenceEvaluation(
            boolean insideGeofence,
            String accuracyStatus,
            double boundaryToleranceMeters,
            String evaluationReason
    ) {}

    public static int checkIntervalSeconds(LocalDateTime sessionStart, LocalDateTime sessionEnd) {
        long sessionDurationSeconds = Duration.between(sessionStart, sessionEnd).getSeconds();
        if (sessionDurationSeconds <= 0) {
            return 1;
        }
        return (int) Math.max(1, sessionDurationSeconds / CHECK_SLOT_COUNT);
    }

    public static int expectedChecks(LocalDateTime trackingStartedAt, LocalDateTime sessionStart,
            LocalDateTime sessionEnd) {
        long remainingSeconds = Duration.between(trackingStartedAt, sessionEnd).getSeconds();
        if (remainingSeconds <= 0) {
            return 1;
        }
        long intervalSeconds = checkIntervalSeconds(sessionStart, sessionEnd);
        long checkCount = (remainingSeconds + intervalSeconds - 1) / intervalSeconds;
        return (int) Math.min(checkCount, Integer.MAX_VALUE);
    }

    public static boolean meetsAttendanceThreshold(int insideChecks, int expectedChecks) {
        return insideChecks >= 0 && expectedChecks > 0
                && insideChecks * 100L >= expectedChecks * (long) MINIMUM_PERCENT_PRESENT;
    }

    public static String evaluateAccuracyStatus(Double accuracyMeters) {
        if (accuracyMeters == null || accuracyMeters > MAX_ACCEPTABLE_ACCURACY_METERS) {
            return "POOR_ACCURACY";
        }
        if (accuracyMeters <= HIGH_ACCURACY_THRESHOLD_METERS) {
            return "HIGH";
        }
        return "ACCEPTABLE";
    }

    public static double calculateBoundaryTolerance(Double accuracyMeters) {
        if (accuracyMeters == null || accuracyMeters <= 0.0 || accuracyMeters > MAX_ACCEPTABLE_ACCURACY_METERS) {
            return 0.0;
        }
        double rawTolerance = Math.min(MAX_BOUNDARY_TOLERANCE_METERS, BOUNDARY_TOLERANCE_RATIO * accuracyMeters);
        return Math.round(rawTolerance * 10.0) / 10.0;
    }

    public static GeofenceEvaluation evaluateGeofencePresence(double distanceMeters, double radiusMeters, Double accuracyMeters) {
        String accuracyStatus = evaluateAccuracyStatus(accuracyMeters);

        if ("POOR_ACCURACY".equals(accuracyStatus)) {
            String reason = accuracyMeters == null
                    ? "GPS accuracy unavailable; reading cannot reliably verify geofence presence."
                    : String.format(java.util.Locale.ROOT,
                            "Reported GPS accuracy (±%.1fm) exceeds maximum acceptable threshold (%.1fm); reading rejected.",
                            accuracyMeters, MAX_ACCEPTABLE_ACCURACY_METERS);
            return new GeofenceEvaluation(false, accuracyStatus, 0.0, reason);
        }

        double tolerance = calculateBoundaryTolerance(accuracyMeters);

        if (distanceMeters <= radiusMeters) {
            String reason = String.format(java.util.Locale.ROOT,
                    "Device is inside geofence (%.1fm from center, radius %.1fm, accuracy ±%.1fm).",
                    distanceMeters, radiusMeters, accuracyMeters);
            return new GeofenceEvaluation(true, accuracyStatus, 0.0, reason);
        }

        if (distanceMeters <= radiusMeters + tolerance) {
            String reason = String.format(java.util.Locale.ROOT,
                    "Device is near boundary (%.1fm from center, radius %.1fm) within allowable GPS uncertainty margin (±%.1fm tolerance, accuracy ±%.1fm).",
                    distanceMeters, radiusMeters, tolerance, accuracyMeters);
            return new GeofenceEvaluation(true, accuracyStatus, tolerance, reason);
        }

        String reason = String.format(java.util.Locale.ROOT,
                "Device is outside geofence (%.1fm from center, radius %.1fm, accuracy ±%.1fm).",
                distanceMeters, radiusMeters, accuracyMeters);
        return new GeofenceEvaluation(false, accuracyStatus, tolerance, reason);
    }
}