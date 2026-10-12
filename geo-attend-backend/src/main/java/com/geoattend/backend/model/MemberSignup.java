package com.geoattend.backend.model;

import java.time.LocalDateTime;

public record MemberSignup(
        Long id,
        String name,
        String email,
        String passwordHash,
        Long requestedBatchId,
        String requestedBatchName,
        String studentId,
        String status,
        String rejectionReason,
        Long reviewedBy,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt) {
}
