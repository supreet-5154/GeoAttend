package com.geoattend.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AttendanceTrackingPolicyTest {

    @Test
    void dividesTenAndSixtyMinuteSessionsIntoTenIntervals() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 9, 0);

        assertEquals(6, AttendanceTrackingPolicy.checkIntervalSeconds(start, start.plusMinutes(1)));
        assertEquals(60, AttendanceTrackingPolicy.checkIntervalSeconds(start, start.plusMinutes(10)));
        assertEquals(360, AttendanceTrackingPolicy.checkIntervalSeconds(start, start.plusHours(1)));
        assertEquals(10, AttendanceTrackingPolicy.expectedChecks(start, start, start.plusMinutes(1)));
        assertEquals(10, AttendanceTrackingPolicy.expectedChecks(start, start, start.plusMinutes(10)));
        assertEquals(10, AttendanceTrackingPolicy.expectedChecks(start, start, start.plusHours(1)));
        assertEquals(5, AttendanceTrackingPolicy.expectedChecks(start.plusMinutes(30), start, start.plusHours(1)));
    }

    @Test
    void attendanceRequiresAtLeastEightyPercentOfExpectedChecksInside() {
        assertTrue(AttendanceTrackingPolicy.meetsAttendanceThreshold(4, 5));
        assertTrue(AttendanceTrackingPolicy.meetsAttendanceThreshold(8, 10));
        assertFalse(AttendanceTrackingPolicy.meetsAttendanceThreshold(3, 5));
        assertFalse(AttendanceTrackingPolicy.meetsAttendanceThreshold(0, 0));
    }
}