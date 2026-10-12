package com.geoattend.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.geoattend.backend.model.Attendance;
import com.geoattend.backend.model.AttendanceSession;
import com.geoattend.backend.model.Batch;
import com.geoattend.backend.model.Member;
import com.geoattend.backend.model.PagedResponse;
import com.geoattend.backend.model.PendingMemberSignup;
import com.geoattend.backend.repository.AttendanceRepository;
import com.geoattend.backend.repository.AttendanceSessionRepository;
import com.geoattend.backend.repository.AttendanceTrackingRepository;
import com.geoattend.backend.repository.BatchRepository;
import com.geoattend.backend.repository.ClubUserRepository;
import com.geoattend.backend.repository.MemberRepository;
import com.geoattend.backend.repository.MemberSignupRepository;
import com.geoattend.backend.util.DateTimeFilterUtils;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FilterAndPaginationTest {

    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private AttendanceSessionRepository attendanceSessionRepository;
    @Mock
    private AttendanceTrackingRepository attendanceTrackingRepository;
    @Mock
    private BatchRepository batchRepository;
    @Mock
    private MemberSignupRepository memberSignupRepository;
    @Mock
    private ClubUserRepository clubUserRepository;
    @Mock
    private AuthService authService;

    private AttendanceService attendanceService;
    private MemberService memberService;
    private AttendanceSessionService sessionService;
    private BatchService batchService;
    private MemberSignupApprovalService signupApprovalService;

    @BeforeEach
    void setUp() {
        attendanceService = new AttendanceService(
                attendanceRepository, memberRepository, attendanceSessionRepository,
                attendanceTrackingRepository, null);
        memberService = new MemberService(memberRepository, batchRepository, authService);
        sessionService = new AttendanceSessionService(attendanceSessionRepository, attendanceRepository);
        batchService = new BatchService(batchRepository);
        signupApprovalService = new MemberSignupApprovalService(
                memberSignupRepository, memberRepository, batchRepository, clubUserRepository, authService);
    }

    // 1. Date Range Boundaries Tests
    @Test
    void testDateRangeBoundaryParsing() {
        LocalDateTime start = DateTimeFilterUtils.parseStartBoundary("2026-10-12");
        assertNotNull(start);
        assertEquals(2026, start.getYear());
        assertEquals(10, start.getMonthValue());
        assertEquals(12, start.getDayOfMonth());
        assertEquals(0, start.getHour());
        assertEquals(0, start.getMinute());

        LocalDateTime end = DateTimeFilterUtils.parseEndBoundary("2026-10-12");
        assertNotNull(end);
        assertEquals(2026, end.getYear());
        assertEquals(10, end.getMonthValue());
        assertEquals(12, end.getDayOfMonth());
        assertEquals(23, end.getHour());
        assertEquals(59, end.getMinute());

        // Null and blank handling
        assertNull(DateTimeFilterUtils.parseStartBoundary(null));
        assertNull(DateTimeFilterUtils.parseStartBoundary(""));
        assertNull(DateTimeFilterUtils.parseEndBoundary("   "));

        // ISO format with timestamp
        LocalDateTime isoStart = DateTimeFilterUtils.parseStartBoundary("2026-10-12T09:30:00");
        assertNotNull(isoStart);
        assertEquals(9, isoStart.getHour());
        assertEquals(30, isoStart.getMinute());
    }

    // 2. Pagination Wrapper Properties
    @Test
    void testPagedResponseMathAndFlags() {
        PagedResponse<String> emptyResponse = new PagedResponse<>(Collections.emptyList(), 0, 10, 0);
        assertTrue(emptyResponse.isFirst());
        assertTrue(emptyResponse.isLast());
        assertEquals(0, emptyResponse.getTotalPages());
        assertEquals(0, emptyResponse.getTotalElements());

        PagedResponse<String> firstPage = new PagedResponse<>(List.of("A", "B", "C"), 0, 3, 10);
        assertTrue(firstPage.isFirst());
        assertFalse(firstPage.isLast());
        assertEquals(4, firstPage.getTotalPages());
        assertEquals(10, firstPage.getTotalElements());

        PagedResponse<String> lastPage = new PagedResponse<>(List.of("J"), 3, 3, 10);
        assertFalse(lastPage.isFirst());
        assertTrue(lastPage.isLast());
    }

    // 3. Attendance Combined Filters & Pagination
    @Test
    void testAttendanceCombinedFiltersAndPagination() {
        when(attendanceRepository.countAttendance(eq(10L), eq(20L), eq(1L), eq("alice"),
                eq("GEOFENCE"), eq("PRESENT"), any(), any()))
                .thenReturn(15L);

        when(attendanceRepository.findAttendance(eq(10L), eq(20L), eq(1L), eq("alice"),
                eq("GEOFENCE"), eq("PRESENT"), any(), any(), eq("markedAt"), eq("desc"), eq(0), eq(5)))
                .thenReturn(List.of(
                        new Attendance(1L, 10L, 20L, LocalDateTime.now(), 12.34, 56.78, "GEOFENCE",
                                "Morning Workshop", "Alice Johnson", "S123", 1L, "5th Generation", "PRESENT")
                ));

        PagedResponse<Attendance> response = attendanceService.getPagedAttendance(
                10L, 20L, 1L, "alice", "GEOFENCE", "PRESENT", "2026-10-01", "2026-10-31",
                "markedAt", "desc", 0, 5);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals(15L, response.getTotalElements());
        assertEquals(3, response.getTotalPages());
        assertTrue(response.isFirst());
        assertFalse(response.isLast());
        assertEquals("Morning Workshop", response.getContent().get(0).getSessionTitle());
        assertEquals("Alice Johnson", response.getContent().get(0).getMemberName());
    }

    // 4. Clearing Attendance Filters (Unfiltered fallback)
    @Test
    void testAttendanceClearingFiltersReturnsAll() {
        when(attendanceRepository.findAttendance(isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), eq("markedAt"), eq("desc"), isNull(), isNull()))
                .thenReturn(List.of(
                        new Attendance(1L, 10L, 20L, LocalDateTime.now(), 12.34, 56.78, "GEOFENCE"),
                        new Attendance(2L, 11L, 21L, LocalDateTime.now(), 12.34, 56.78, "MANUAL")
                ));

        List<Attendance> results = attendanceService.getFilteredAttendance(
                null, null, null, null, null, null, null, null, "markedAt", "desc");

        assertEquals(2, results.size());
    }

    // 5. Member Filtering, Sorting and Pagination
    @Test
    void testMemberFilteringAndPagination() {
        when(memberRepository.countMembers("john", 2L, "MEMBER")).thenReturn(8L);
        when(memberRepository.findMembers("john", 2L, "MEMBER", "name", "asc", 0, 10))
                .thenReturn(List.of(
                        new Member(101L, "John Doe", "john@okc.edu", 2L, "6th Generation", "CS101")
                ));

        PagedResponse<Member> response = memberService.getPagedMembers("john", 2L, "MEMBER", "name", "asc", 0, 10);
        assertEquals(1, response.getContent().size());
        assertEquals(8L, response.getTotalElements());
        assertEquals(1, response.getTotalPages());
        assertTrue(response.isFirst());
        assertTrue(response.isLast());
    }

    // 6. Session Filtering by Status and Date Range
    @Test
    void testSessionFilteringByStatusAndDateRange() {
        when(attendanceSessionRepository.countSessions(eq("weekly"), eq("ACTIVE"), any(), any()))
                .thenReturn(3L);
        when(attendanceSessionRepository.findSessions(eq("weekly"), eq("ACTIVE"), any(), any(), eq("startTime"), eq("desc"), eq(0), eq(10)))
                .thenReturn(List.of(
                        new AttendanceSession(1L, "Weekly Meetup", LocalDateTime.now(), LocalDateTime.now().plusHours(2), 12.0, 56.0, 50)
                ));

        PagedResponse<AttendanceSession> response = sessionService.getPagedSessions(
                "weekly", "ACTIVE", "2026-10-01", "2026-10-15", "startTime", "desc", 0, 10);

        assertEquals(1, response.getContent().size());
        assertEquals(3L, response.getTotalElements());
        assertEquals("Weekly Meetup", response.getContent().get(0).getTitle());
    }

    // 7. Member Signup Queue Filtering
    @Test
    void testSignupQueueFilteringAndPagination() {
        when(memberSignupRepository.countSignups(eq("PENDING"), eq(3L), eq("supreet"), any(), any()))
                .thenReturn(2L);
        when(memberSignupRepository.findSignups(eq("PENDING"), eq(3L), eq("supreet"), any(), any(), eq("createdAt"), eq("desc"), eq(0), eq(5)))
                .thenReturn(List.of(
                        new PendingMemberSignup(10L, "Supreet", "supreet@okc.edu", 3L, "7th Generation", "CS101",
                                LocalDateTime.now(), "PENDING", null)
                ));

        PagedResponse<PendingMemberSignup> response = signupApprovalService.getPagedSignups(
                "PENDING", 3L, "supreet", "2026-10-01", "2026-10-12", "createdAt", "desc", 0, 5);

        assertEquals(1, response.getContent().size());
        assertEquals("Supreet", response.getContent().get(0).name());
        assertEquals("7th Generation", response.getContent().get(0).requestedBatchName());
    }

    // 8. Batch Search and Status Filtering
    @Test
    void testBatchFilteringAndPagination() {
        when(batchRepository.countBatches("generation", "ACTIVE")).thenReturn(3L);
        when(batchRepository.findBatches("generation", "ACTIVE", "name", "asc", 0, 10))
                .thenReturn(List.of(
                        new Batch(1L, "5th Generation", "ACTIVE", LocalDateTime.now(), LocalDateTime.now(), 20),
                        new Batch(2L, "6th Generation", "ACTIVE", LocalDateTime.now(), LocalDateTime.now(), 15),
                        new Batch(3L, "7th Generation", "ACTIVE", LocalDateTime.now(), LocalDateTime.now(), 0)
                ));

        PagedResponse<Batch> response = batchService.getPagedBatches("generation", "ACTIVE", "name", "asc", 0, 10);
        assertEquals(3, response.getContent().size());
        assertEquals(3L, response.getTotalElements());
        assertTrue(response.isFirst());
        assertTrue(response.isLast());
    }
}
