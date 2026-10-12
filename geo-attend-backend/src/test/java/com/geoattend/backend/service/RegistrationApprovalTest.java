package com.geoattend.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.geoattend.backend.model.Batch;
import com.geoattend.backend.model.Member;
import com.geoattend.backend.model.MemberSignup;
import com.geoattend.backend.repository.BatchRepository;
import com.geoattend.backend.repository.ClubUserRepository;
import com.geoattend.backend.repository.MemberRepository;
import com.geoattend.backend.repository.MemberSignupRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class RegistrationApprovalTest {

    @Mock
    private MemberSignupRepository memberSignupRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private BatchRepository batchRepository;
    @Mock
    private ClubUserRepository clubUserRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private MemberSignupApprovalService approvalService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        approvalService = new MemberSignupApprovalService(
                memberSignupRepository,
                memberRepository,
                batchRepository,
                clubUserRepository,
                new AuthService(passwordEncoder));
    }

    @Test
    void approvalByAdminCreatesMemberWithBatchAndMarksApproved() {
        Long signupId = 10L;
        Long adminStaffId = 1L; // Admin user ID
        Long requestedBatchId = 2L;

        MemberSignup signup = new MemberSignup(
                signupId, "Jane Doe", "jane@example.com", "bcrypt-hashed-pw",
                requestedBatchId, "6th Generation", "STU100", "PENDING", null, null, null, LocalDateTime.now());

        when(memberSignupRepository.findPendingByIdForUpdate(signupId)).thenReturn(Optional.of(signup));
        when(clubUserRepository.getLoginUserByEmail(signup.email())).thenReturn(null);
        when(memberRepository.getLoginUserByEmail(signup.email())).thenReturn(null);
        when(batchRepository.findById(requestedBatchId)).thenReturn(Optional.of(
                new Batch(requestedBatchId, "6th Generation", "ACTIVE", LocalDateTime.now(), LocalDateTime.now(), 10)));
        when(memberRepository.addMember(any(Member.class))).thenReturn(1);
        when(memberSignupRepository.approve(signupId, adminStaffId)).thenReturn(1);

        approvalService.approve(signupId, requestedBatchId, adminStaffId);

        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).addMember(memberCaptor.capture());
        Member createdMember = memberCaptor.getValue();
        assertEquals("Jane Doe", createdMember.getName());
        assertEquals("jane@example.com", createdMember.getEmail());
        assertEquals("MEMBER", createdMember.getRole()); // Role is always strictly MEMBER
        assertEquals(requestedBatchId, createdMember.getBatchId());
        assertEquals("STU100", createdMember.getStudentId());
        assertEquals("bcrypt-hashed-pw", createdMember.getPasswordHash()); // Preserves user's hashed password

        verify(memberSignupRepository).approve(signupId, adminStaffId);
    }

    @Test
    void approvalByClubUserWithDifferentBatchAssignmentSucceeds() {
        Long signupId = 11L;
        Long clubUserStaffId = 2L; // Club User ID
        Long requestedBatchId = 1L;
        Long staffSelectedBatchId = 3L; // Staff assigns different batch

        MemberSignup signup = new MemberSignup(
                signupId, "Bob Smith", "bob@example.com", "bcrypt-pwd",
                requestedBatchId, "5th Generation", null, "PENDING", null, null, null, LocalDateTime.now());

        when(memberSignupRepository.findPendingByIdForUpdate(signupId)).thenReturn(Optional.of(signup));
        when(clubUserRepository.getLoginUserByEmail(signup.email())).thenReturn(null);
        when(memberRepository.getLoginUserByEmail(signup.email())).thenReturn(null);
        when(batchRepository.findById(staffSelectedBatchId)).thenReturn(Optional.of(
                new Batch(staffSelectedBatchId, "7th Generation", "ACTIVE", LocalDateTime.now(), LocalDateTime.now(), 5)));
        when(memberRepository.addMember(any(Member.class))).thenReturn(1);
        when(memberSignupRepository.approve(signupId, clubUserStaffId)).thenReturn(1);

        // Club User approves and selects a different batch
        approvalService.approve(signupId, staffSelectedBatchId, clubUserStaffId);

        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).addMember(memberCaptor.capture());
        assertEquals(staffSelectedBatchId, memberCaptor.getValue().getBatchId());
        verify(memberSignupRepository).approve(signupId, clubUserStaffId);
    }

    @Test
    void oneApprovalIsSufficientAndSecondApprovalThrowsConflict() {
        Long signupId = 12L;
        Long staffId = 1L;

        MemberSignup signup = new MemberSignup(
                signupId, "Charlie", "charlie@example.com", "pwd-hash", null, null, null, "PENDING", null, null, null, LocalDateTime.now());

        when(memberSignupRepository.findPendingByIdForUpdate(signupId)).thenReturn(Optional.of(signup));
        when(clubUserRepository.getLoginUserByEmail(signup.email())).thenReturn(null);
        when(memberRepository.getLoginUserByEmail(signup.email())).thenReturn(null);
        when(memberRepository.addMember(any(Member.class))).thenReturn(1);
        // First approval succeeds: returns 1
        when(memberSignupRepository.approve(signupId, staffId)).thenReturn(1);

        approvalService.approve(signupId, null, staffId);
        verify(memberSignupRepository).approve(signupId, staffId);

        // Second approval attempt: signup row is already APPROVED, so approve update returns 0 rows updated
        when(memberSignupRepository.approve(signupId, staffId)).thenReturn(0);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                approvalService.approve(signupId, null, staffId)
        );
        assertEquals(409, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("already been reviewed"));
    }

    @Test
    void rejectionByAdminOrClubUserWithReasonSucceeds() {
        Long signupId = 15L;
        Long staffId = 2L; // Club User or Admin
        String reason = "Applicant is not currently registered for the spring semester.";

        MemberSignup signup = new MemberSignup(
                signupId, "David", "david@example.com", "pwd", null, null, null, "PENDING", null, null, null, LocalDateTime.now());

        when(memberSignupRepository.findPendingByIdForUpdate(signupId)).thenReturn(Optional.of(signup));
        when(memberSignupRepository.reject(signupId, reason, staffId)).thenReturn(1);

        approvalService.reject(signupId, reason, staffId);

        verify(memberSignupRepository).reject(signupId, reason, staffId);
    }

    @Test
    void secondRejectionThrowsConflict() {
        Long signupId = 16L;
        Long staffId = 1L;

        MemberSignup signup = new MemberSignup(
                signupId, "Eve", "eve@example.com", "pwd", null, null, null, "PENDING", null, null, null, LocalDateTime.now());

        when(memberSignupRepository.findPendingByIdForUpdate(signupId)).thenReturn(Optional.of(signup));
        when(memberSignupRepository.reject(signupId, "Incomplete info", staffId)).thenReturn(0);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                approvalService.reject(signupId, "Incomplete info", staffId)
        );
        assertEquals(409, ex.getStatusCode().value());
    }
}
