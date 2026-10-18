package com.geoattend.backend.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.geoattend.backend.model.Attendance;
import com.geoattend.backend.model.AttendanceSession;
import com.geoattend.backend.model.ManualAttendanceRequest;
import com.geoattend.backend.repository.AttendanceRepository;
import com.geoattend.backend.repository.AttendanceSessionRepository;
import com.geoattend.backend.repository.AttendanceTrackingRepository;
import com.geoattend.backend.repository.MemberRepository;
import com.geoattend.backend.security.ClubUser;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AttendanceServiceValidationTest {

    private AttendanceRepository attendanceRepository;
    private MemberRepository memberRepository;
    private AttendanceSessionRepository sessionRepository;
    private AttendanceTrackingRepository trackingRepository;
    private ClubUserService clubUserService;
    private AttendanceService attendanceService;

    @BeforeEach
    void setUp() {
        attendanceRepository = mock(AttendanceRepository.class);
        memberRepository = mock(MemberRepository.class);
        sessionRepository = mock(AttendanceSessionRepository.class);
        trackingRepository = mock(AttendanceTrackingRepository.class);
        clubUserService = mock(ClubUserService.class);

        attendanceService = new AttendanceService(
                attendanceRepository,
                memberRepository,
                sessionRepository,
                trackingRepository,
                clubUserService
        );
    }

    @Test
    @DisplayName("markAttendance throws RuntimeException when attendance payload or IDs are null")
    void markAttendanceNullValidation() {
        Exception ex1 = assertThrows(RuntimeException.class, () -> attendanceService.markAttendance(null));
        assertTrue(ex1.getMessage().contains("Session ID and Member ID are required"));

        Attendance a = new Attendance();
        a.setSessionId(null);
        a.setMemberId(1L);
        Exception ex2 = assertThrows(RuntimeException.class, () -> attendanceService.markAttendance(a));
        assertTrue(ex2.getMessage().contains("Session ID and Member ID are required"));

        a.setSessionId(1L);
        a.setMemberId(null);
        Exception ex3 = assertThrows(RuntimeException.class, () -> attendanceService.markAttendance(a));
        assertTrue(ex3.getMessage().contains("Session ID and Member ID are required"));
    }

    @Test
    @DisplayName("markManualAttendance throws RuntimeException when request or IDs are null")
    void markManualAttendanceNullValidation() {
        ClubUser staff = new ClubUser(1L, "Staff Admin", "ADMIN");

        Exception ex1 = assertThrows(RuntimeException.class, () -> attendanceService.markManualAttendance(null, staff));
        assertTrue(ex1.getMessage().contains("Session ID and Member ID are required"));

        ManualAttendanceRequest req = new ManualAttendanceRequest();
        req.setSessionId(null);
        req.setMemberId(1L);
        Exception ex2 = assertThrows(RuntimeException.class, () -> attendanceService.markManualAttendance(req, staff));
        assertTrue(ex2.getMessage().contains("Session ID and Member ID are required"));

        req.setSessionId(1L);
        req.setMemberId(null);
        Exception ex3 = assertThrows(RuntimeException.class, () -> attendanceService.markManualAttendance(req, staff));
        assertTrue(ex3.getMessage().contains("Session ID and Member ID are required"));
    }

    @Test
    @DisplayName("markManualAttendance uses India Standard Time (Asia/Kolkata) to validate session start time")
    void markManualAttendanceTimezoneValidation() {
        ClubUser staff = new ClubUser(1L, "Staff Admin", "ADMIN");
        when(clubUserService.canMarkManualAttendance(staff)).thenReturn(true);
        when(memberRepository.memberExists(100L)).thenReturn(true);
        when(sessionRepository.sessionExists(200L)).thenReturn(true);

        // A session that starts 1 hour in the future in Asia/Kolkata
        LocalDateTime nowKolkata = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
        AttendanceSession futureSession = new AttendanceSession();
        futureSession.setId(200L);
        futureSession.setStartTime(nowKolkata.plusHours(1));
        futureSession.setEndTime(nowKolkata.plusHours(2));

        when(sessionRepository.getSessionById(200L)).thenReturn(futureSession);

        ManualAttendanceRequest req = new ManualAttendanceRequest();
        req.setSessionId(200L);
        req.setMemberId(100L);

        Exception ex = assertThrows(RuntimeException.class, () -> attendanceService.markManualAttendance(req, staff));
        assertTrue(ex.getMessage().contains("cannot be recorded before the session starts"));
    }
}
