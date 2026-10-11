package com.geoattend.backend.controller;

import com.geoattend.backend.model.ApiResponse;
import com.geoattend.backend.model.Attendance;
import com.geoattend.backend.model.LocationCheckRequest;
import com.geoattend.backend.model.ManualAttendanceRequest;
import com.geoattend.backend.security.ClubUser;
import com.geoattend.backend.service.AttendanceService;
import com.geoattend.backend.service.AttendanceTrackingService;
import com.geoattend.backend.service.ClubUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;
        private final AttendanceTrackingService attendanceTrackingService;
    private final ClubUserService clubUserService;

    public AttendanceController(
            AttendanceService attendanceService,
                        AttendanceTrackingService attendanceTrackingService,
            ClubUserService clubUserService) {

        this.attendanceService = attendanceService;
                this.attendanceTrackingService = attendanceTrackingService;
        this.clubUserService = clubUserService;
    }

        @PostMapping("/location-check")
        public ResponseEntity<?> recordLocationCheck(
                        @RequestBody LocationCheckRequest request,
                        Authentication authentication) {
                try {
                        Long memberId = Long.valueOf(authentication.getName());
                        return ResponseEntity.ok(attendanceTrackingService.recordLocationCheck(memberId, request));
                } catch (RuntimeException e) {
                        return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
                }
        }

        @GetMapping("/my/tracking")
        public ResponseEntity<?> getMyTracking(Authentication authentication) {
                try {
                        Long memberId = Long.valueOf(authentication.getName());
                        return ResponseEntity.ok(attendanceTrackingService.getMyTracking(memberId));
                } catch (RuntimeException e) {
                        return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
                }
        }

        @GetMapping("/session/{sessionId}/tracking")
        public ResponseEntity<?> getSessionTracking(@PathVariable Long sessionId) {
                try {
                        return ResponseEntity.ok(attendanceTrackingService.getSessionTracking(sessionId));
                } catch (RuntimeException e) {
                        return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
                }
        }

    @PostMapping
    public ResponseEntity<ApiResponse> markAttendance(
            @RequestBody Attendance attendance) {

        try {
            int result = attendanceService.markAttendance(attendance);

            if (result > 0) {
                return ResponseEntity.ok(
                        new ApiResponse(
                                true,
                                "Attendance marked successfully"
                        )
                );
            }

            return ResponseEntity.badRequest()
                    .body(new ApiResponse(
                            false,
                            "Failed to mark attendance"
                    ));

        } catch (RuntimeException e) {

            return ResponseEntity.badRequest()
                    .body(new ApiResponse(
                            false,
                            e.getMessage()
                    ));
        }
    }

        @GetMapping("/session/{sessionId}/checks")
        public ResponseEntity<?> getSessionLocationChecks(@PathVariable Long sessionId) {
                try {
                        return ResponseEntity.ok(attendanceTrackingService.getSessionLocationAudit(sessionId));
                } catch (RuntimeException e) {
                        return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
                }
        }

        @PostMapping("/tracking/start")
        public ResponseEntity<?> startTracking(
                        @RequestBody LocationCheckRequest request,
                        Authentication authentication) {
                try {
                        Long memberId = Long.valueOf(authentication.getName());
                        return ResponseEntity.ok(attendanceTrackingService.startTracking(memberId, request.getSessionId()));
                } catch (RuntimeException e) {
                        return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
                }
        }

    @PostMapping("/self")
    public ResponseEntity<?> recordSelfLocationCheck(
            @RequestBody LocationCheckRequest request,
            Authentication authentication) {

        try {
            boolean isMember = authentication != null
                    && authentication.getAuthorities().stream()
                    .anyMatch(authority -> "ROLE_MEMBER".equals(authority.getAuthority()));

            if (!isMember) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ApiResponse(false, "Members can only mark their own attendance"));
            }

            Long memberId = Long.valueOf(authentication.getName());
            return ResponseEntity.ok(attendanceTrackingService.recordLocationCheck(memberId, request));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @PostMapping("/manual")
    public ResponseEntity<ApiResponse> markManualAttendance(
            @RequestBody ManualAttendanceRequest request,
            Authentication authentication) {

        try {
            Long clubUserId = Long.valueOf(
                    authentication.getName()
            );

            ClubUser clubUser = clubUserService.getClubUser(clubUserId);

            int result = attendanceService.markManualAttendance(
                    request,
                    clubUser
            );

            if (result > 0) {
                return ResponseEntity.ok(
                        new ApiResponse(
                                true,
                                "Manual attendance marked successfully"
                        )
                );
            }

            return ResponseEntity.badRequest()
                    .body(new ApiResponse(
                            false,
                            "Failed to mark manual attendance"
                    ));

        } catch (RuntimeException e) {

            return ResponseEntity.badRequest()
                    .body(new ApiResponse(
                            false,
                            e.getMessage()
                    ));
        }
    }

        @PutMapping("/{attendanceId}")
        public ResponseEntity<ApiResponse> updateAttendance(
                        @PathVariable Long attendanceId,
                        @RequestBody Attendance attendance) {
                try {
                        int result = attendanceService.updateAttendance(attendanceId, attendance);
                        if (result > 0) {
                                return ResponseEntity.ok(new ApiResponse(true, "Attendance record updated successfully"));
                        }
                        return ResponseEntity.notFound().build();
                } catch (RuntimeException e) {
                        return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
                }
        }

        @DeleteMapping("/{attendanceId}")
        public ResponseEntity<ApiResponse> deleteAttendance(@PathVariable Long attendanceId) {
                int result = attendanceService.deleteAttendance(attendanceId);
                if (result > 0) {
                        return ResponseEntity.ok(new ApiResponse(true, "Attendance record deleted successfully"));
                }
                return ResponseEntity.notFound().build();
        }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<?> getAttendanceBySession(
            @PathVariable Long sessionId) {

        try {

            return ResponseEntity.ok(
                    attendanceService.getAttendanceBySessionId(sessionId)
            );

        } catch (RuntimeException e) {

            return ResponseEntity
                    .badRequest()
                    .body(new ApiResponse(
                            false,
                            e.getMessage()
                    ));
        }
    }

    @GetMapping("/member/{memberId}")
    public ResponseEntity<?> getAttendanceByMember(
            @PathVariable Long memberId,
            Authentication authentication) {

        try {
            boolean isMember = authentication != null
                    && authentication.getAuthorities().stream()
                    .anyMatch(authority -> "ROLE_MEMBER".equals(authority.getAuthority()));

            if (isMember && !memberId.equals(Long.valueOf(authentication.getName()))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ApiResponse(false, "You can only view your own attendance history"));
            }

            return ResponseEntity.ok(
                    attendanceService.getAttendanceByMemberId(memberId)
            );

        } catch (RuntimeException e) {

            return ResponseEntity
                    .badRequest()
                    .body(new ApiResponse(
                            false,
                            e.getMessage()
                    ));
        }
    }

    @GetMapping("/my")
    public ResponseEntity<?> getMyAttendance(Authentication authentication) {
        try {
            boolean isMember = authentication != null
                    && authentication.getAuthorities().stream()
                    .anyMatch(authority -> "ROLE_MEMBER".equals(authority.getAuthority()));

            if (!isMember) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ApiResponse(false, "Only members can access their own attendance"));
            }

            Long memberId = Long.valueOf(authentication.getName());
            return ResponseEntity.ok(attendanceService.getAttendanceByMemberId(memberId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }
}