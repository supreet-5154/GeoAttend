package com.geoattend.backend.service;

import com.geoattend.backend.model.AttendanceSession;
import com.geoattend.backend.model.PagedResponse;
import com.geoattend.backend.repository.AttendanceRepository;
import com.geoattend.backend.repository.AttendanceSessionRepository;
import com.geoattend.backend.util.DateTimeFilterUtils;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AttendanceSessionService {

    private final AttendanceSessionRepository attendanceSessionRepository;
    private final AttendanceRepository attendanceRepository;

    public AttendanceSessionService(
            AttendanceSessionRepository attendanceSessionRepository,
            AttendanceRepository attendanceRepository) {
        this.attendanceSessionRepository = attendanceSessionRepository;
        this.attendanceRepository = attendanceRepository;
    }

    public List<AttendanceSession> getAllSessions() {
        return attendanceSessionRepository.getAllSessions();
    }

    public PagedResponse<AttendanceSession> getPagedSessions(
            String search, String status, String fromDate, String toDate,
            String sortBy, String sortDirection, int page, int size) {
        LocalDateTime start = DateTimeFilterUtils.parseStartBoundary(fromDate);
        LocalDateTime end = DateTimeFilterUtils.parseEndBoundary(toDate);
        int pageNum = Math.max(0, page);
        int pageSize = Math.max(1, Math.min(size, 100));
        int offset = pageNum * pageSize;

        long total = attendanceSessionRepository.countSessions(search, status, start, end);
        List<AttendanceSession> content = attendanceSessionRepository.findSessions(
                search, status, start, end, sortBy, sortDirection, offset, pageSize);

        return new PagedResponse<>(content, pageNum, pageSize, total);
    }

    public List<AttendanceSession> getFilteredSessions(
            String search, String status, String fromDate, String toDate,
            String sortBy, String sortDirection) {
        LocalDateTime start = DateTimeFilterUtils.parseStartBoundary(fromDate);
        LocalDateTime end = DateTimeFilterUtils.parseEndBoundary(toDate);
        return attendanceSessionRepository.findSessions(
                search, status, start, end, sortBy, sortDirection, null, null);
    }

    public int createSession(AttendanceSession session) {
        validateSession(session);
        return attendanceSessionRepository.createSession(session);
    }

    public int updateSession(Long sessionId, AttendanceSession session) {
        if (!attendanceSessionRepository.sessionExists(sessionId)) {
            return 0;
        }
        validateSession(session);
        return attendanceSessionRepository.updateSession(sessionId, session);
    }

    public int deleteSession(Long sessionId) {
        if (!attendanceSessionRepository.sessionExists(sessionId)) {
            return 0;
        }
        if (attendanceRepository.hasAttendanceForSession(sessionId)) {
            throw new IllegalStateException("This session has attendance records and cannot be deleted.");
        }
        return attendanceSessionRepository.deleteSession(sessionId);
    }

    private void validateSession(AttendanceSession session) {
        if (session.getTitle() == null || session.getTitle().isBlank()) {
            throw new IllegalArgumentException("Session title is required.");
        }
        if (session.getStartTime() == null || session.getEndTime() == null
                || !session.getStartTime().isBefore(session.getEndTime())) {
            throw new IllegalArgumentException("Session end time must be after its start time.");
        }
        if (session.getRadiusMeters() == null || session.getRadiusMeters() <= 0) {
            throw new IllegalArgumentException("Every attendance session requires a geofence radius greater than zero.");
        }
        if (session.getLatitude() == null || session.getLongitude() == null
                || session.getLatitude() < -90 || session.getLatitude() > 90
                || session.getLongitude() < -180 || session.getLongitude() > 180) {
            throw new IllegalArgumentException("Every attendance session requires valid geofence coordinates.");
        }
    }

}