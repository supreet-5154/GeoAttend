package com.geoattend.backend.service;

import com.geoattend.backend.model.Attendance;
import com.geoattend.backend.model.AttendanceSession;
import com.geoattend.backend.model.ManualAttendanceRequest;
import com.geoattend.backend.repository.AttendanceRepository;
import com.geoattend.backend.repository.AttendanceSessionRepository;
import com.geoattend.backend.repository.AttendanceTrackingRepository;
import com.geoattend.backend.repository.MemberRepository;
import com.geoattend.backend.security.ClubUser;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final MemberRepository memberRepository;
    private final AttendanceSessionRepository attendanceSessionRepository;
    private final AttendanceTrackingRepository attendanceTrackingRepository;
    private final ClubUserService clubUserService;

    public AttendanceService(
            AttendanceRepository attendanceRepository,
            MemberRepository memberRepository,
            AttendanceSessionRepository attendanceSessionRepository,
            AttendanceTrackingRepository attendanceTrackingRepository,
            ClubUserService clubUserService) {

        this.attendanceRepository = attendanceRepository;
        this.memberRepository = memberRepository;
        this.attendanceSessionRepository = attendanceSessionRepository;
        this.attendanceTrackingRepository = attendanceTrackingRepository;
        this.clubUserService = clubUserService;
    }

    public int markAttendance(Attendance attendance) {

        attendance.setAttendanceMethod("GEOFENCE");

        if (!memberRepository.memberExists(attendance.getMemberId())) {
            throw new RuntimeException("Member does not exist");
        }

        if (!attendanceSessionRepository.sessionExists(attendance.getSessionId())) {
            throw new RuntimeException("Attendance session does not exist");
        }

        AttendanceSession session =
                attendanceSessionRepository.getSessionById(
                        attendance.getSessionId()
                );

        if (!session.isLocationEnabled()) {
            throw new RuntimeException("This session has no geofence configured. Ask an administrator to update the session location.");
        }

        LocalDateTime now = LocalDateTime.now();

        if (now.isBefore(session.getStartTime())
                || now.isAfter(session.getEndTime())) {

            throw new RuntimeException("Attendance session is not active");
        }

        if ("GEOFENCE".equals(attendance.getAttendanceMethod())) {

            if (attendance.getLatitude() == null
                    || attendance.getLongitude() == null) {
                throw new RuntimeException(
                        "Location is required for geofence attendance"
                );
            }

            double distance = calculateDistance(
                    session.getLatitude(),
                    session.getLongitude(),
                    attendance.getLatitude(),
                    attendance.getLongitude()
            );

            if (distance > session.getRadiusMeters()) {
                throw new RuntimeException(
                        "You are outside the attendance area"
                );
            }
        }

        if (attendanceRepository.attendanceExists(
                attendance.getMemberId(),
                attendance.getSessionId())) {

            throw new RuntimeException(
                    "Member has already marked attendance for this session"
            );
        }

        attendance.setMarkedAt(LocalDateTime.now());
        attendance.setAttendanceMethod("GEOFENCE");

        return attendanceRepository.markAttendance(attendance);
    }

    public int markManualAttendance(
            ManualAttendanceRequest request,
            ClubUser clubUser) {

        if (!clubUserService.canMarkManualAttendance(clubUser)) {
            throw new RuntimeException(
                    "User is not authorized for manual attendance"
            );
        }

        if (!memberRepository.memberExists(request.getMemberId())) {
            throw new RuntimeException("Member does not exist");
        }

        if (!attendanceSessionRepository.sessionExists(
                request.getSessionId())) {
            throw new RuntimeException(
                    "Attendance session does not exist"
            );
        }

        AttendanceSession session =
                attendanceSessionRepository.getSessionById(
                        request.getSessionId()
                );

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        if (now.isBefore(session.getStartTime())) {

            throw new RuntimeException(
                "Manual attendance cannot be recorded before the session starts"
            );
        }

        if (attendanceRepository.attendanceExists(
                request.getMemberId(),
                request.getSessionId())) {

            throw new RuntimeException(
                    "Member has already marked attendance for this session"
            );
        }

        Attendance attendance = new Attendance();

        attendance.setSessionId(request.getSessionId());
        attendance.setMemberId(request.getMemberId());
        attendance.setMarkedAt(java.time.LocalDateTime.now());
        attendance.setLatitude(null);
        attendance.setLongitude(null);
        attendance.setAttendanceMethod("MANUAL");

        int result = attendanceRepository.markAttendance(attendance);
        if (result > 0) {
            attendanceTrackingRepository.markManualOverride(
                request.getMemberId(), request.getSessionId(), LocalDateTime.now());
        }
        return result;
    }

    public int updateAttendance(Long attendanceId, Attendance attendance) {
        if (!attendanceRepository.attendanceExistsById(attendanceId)) {
            return 0;
        }
        if (attendance.getMemberId() == null || !memberRepository.memberExists(attendance.getMemberId())) {
            throw new IllegalArgumentException("Select an existing member.");
        }
        if (attendance.getSessionId() == null || !attendanceSessionRepository.sessionExists(attendance.getSessionId())) {
            throw new IllegalArgumentException("Select an existing session.");
        }
        if (attendance.getMarkedAt() == null) {
            throw new IllegalArgumentException("Attendance time is required.");
        }

        AttendanceSession session = attendanceSessionRepository.getSessionById(attendance.getSessionId());
        if (attendance.getMarkedAt().isBefore(session.getStartTime())
                || attendance.getMarkedAt().isAfter(session.getEndTime())) {
            throw new IllegalArgumentException("Attendance time must be within the selected session.");
        }

        if (attendanceRepository.attendanceExistsForAnotherRecord(
                attendance.getMemberId(), attendance.getSessionId(), attendanceId)) {
            throw new IllegalArgumentException("This member already has attendance for the selected session.");
        }

        if ("GEOFENCE".equals(attendance.getAttendanceMethod())) {
            if (!session.isLocationEnabled()) {
                throw new IllegalArgumentException("Geofence attendance requires a geofence-enabled session.");
            }
            if (attendance.getLatitude() == null || attendance.getLongitude() == null) {
                throw new IllegalArgumentException("Location is required for geofence attendance.");
            }
            double distance = calculateDistance(
                    session.getLatitude(),
                    session.getLongitude(),
                    attendance.getLatitude(),
                    attendance.getLongitude()
            );
            if (distance > session.getRadiusMeters()) {
                throw new IllegalArgumentException("The attendance location is outside the session geofence.");
            }
        } else if ("MANUAL".equals(attendance.getAttendanceMethod())) {
            attendance.setLatitude(null);
            attendance.setLongitude(null);
        } else {
            throw new IllegalArgumentException("Attendance method must be GEOFENCE or MANUAL.");
        }

        int result = attendanceRepository.updateAttendance(attendanceId, attendance);
        if (result > 0 && "MANUAL".equals(attendance.getAttendanceMethod())) {
            attendanceTrackingRepository.markManualOverride(
                attendance.getMemberId(), attendance.getSessionId(), LocalDateTime.now());
        }
        return result;
    }

    public int deleteAttendance(Long attendanceId) {
        if (!attendanceRepository.attendanceExistsById(attendanceId)) {
            return 0;
        }
        return attendanceRepository.deleteAttendance(attendanceId);
    }

    public List<Attendance> getAttendanceBySessionId(Long sessionId) {

        if (!attendanceSessionRepository.sessionExists(sessionId)) {
            throw new RuntimeException(
                    "Attendance session does not exist"
            );
        }

        return attendanceRepository.getAttendanceBySessionId(sessionId);
    }

    public List<Attendance> getAttendanceByMemberId(Long memberId) {

        if (!memberRepository.memberExists(memberId)) {
            throw new RuntimeException("Member does not exist");
        }

        return attendanceRepository.getAttendanceByMemberId(memberId);
    }

    private double calculateDistance(
            double lat1,
            double lon1,
            double lat2,
            double lon2) {

        final double EARTH_RADIUS = 6371000; // meters

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2)
                * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c;
    }

}