package com.geoattend.backend.controller;

import com.geoattend.backend.model.ApiResponse;
import com.geoattend.backend.model.AttendanceSession;
import com.geoattend.backend.service.AttendanceSessionService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
public class AttendanceSessionController {

    private final AttendanceSessionService attendanceSessionService;

    public AttendanceSessionController(
            AttendanceSessionService attendanceSessionService) {
        this.attendanceSessionService = attendanceSessionService;
    }

    @GetMapping
    public List<AttendanceSession> getAllSessions() {
        return attendanceSessionService.getAllSessions();
    }

    @PostMapping
    public ResponseEntity<ApiResponse> createSession(
            @RequestBody AttendanceSession session) {
        try {
            int result = attendanceSessionService.createSession(session);
            if (result > 0) {
                return ResponseEntity.ok(new ApiResponse(true, "Attendance session created successfully"));
            }
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Failed to create attendance session"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @PutMapping("/{sessionId}")
    public ResponseEntity<ApiResponse> updateSession(
            @PathVariable Long sessionId,
            @RequestBody AttendanceSession session) {
        try {
            int result = attendanceSessionService.updateSession(sessionId, session);
            if (result > 0) {
                return ResponseEntity.ok(new ApiResponse(true, "Attendance session updated successfully"));
            }
            return ResponseEntity.notFound().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<ApiResponse> deleteSession(@PathVariable Long sessionId) {
        try {
            int result = attendanceSessionService.deleteSession(sessionId);
            if (result > 0) {
                return ResponseEntity.ok(new ApiResponse(true, "Attendance session deleted successfully"));
            }
            return ResponseEntity.notFound().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }
}