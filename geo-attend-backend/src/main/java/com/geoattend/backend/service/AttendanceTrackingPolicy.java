package com.geoattend.backend.service;

import java.time.Duration;
import java.time.LocalDateTime;

public final class AttendanceTrackingPolicy {

    private static final int CHECK_SLOT_COUNT = 10;
    private static final int MINIMUM_PERCENT_PRESENT = 80;

    private AttendanceTrackingPolicy() {
    }

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
}