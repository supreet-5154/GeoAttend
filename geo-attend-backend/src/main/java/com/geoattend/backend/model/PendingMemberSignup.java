package com.geoattend.backend.model;

import java.time.LocalDateTime;

public record PendingMemberSignup(
        Long id,
        String name,
        String email,
        Long requestedBatchId,
        String requestedBatchName,
        String studentId,
        LocalDateTime createdAt,
        String status,
        String rejectionReason) {
}
