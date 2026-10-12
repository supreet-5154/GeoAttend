package com.geoattend.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.geoattend.backend.model.Batch;
import com.geoattend.backend.model.MemberSignup;
import com.geoattend.backend.model.RegisterRequest;
import com.geoattend.backend.repository.BatchRepository;
import com.geoattend.backend.repository.ClubUserRepository;
import com.geoattend.backend.repository.MemberRepository;
import com.geoattend.backend.repository.MemberSignupRepository;
import com.geoattend.backend.security.LoginUser;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class RegistrationSecurityAndRestrictionTest {

    @Mock
    private MemberSignupRepository memberSignupRepository;
    @Mock
    private BatchRepository batchRepository;
    @Mock
    private ClubUserRepository clubUserRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private MemberSignupApprovalService signupApprovalService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        signupApprovalService = new MemberSignupApprovalService(
                memberSignupRepository,
                memberRepository,
                batchRepository,
                clubUserRepository,
                new AuthService(passwordEncoder));
    }

    @Test
    void cannotRegisterWithArchivedBatch() {
        Long archivedBatchId = 99L;
        RegisterRequest request = new RegisterRequest("Sam", "sam@example.com", "password123", archivedBatchId, "STU99");

        when(batchRepository.findById(archivedBatchId)).thenReturn(Optional.of(
                new Batch(archivedBatchId, "Archived Cohort", "ARCHIVED", LocalDateTime.now(), LocalDateTime.now(), 0)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                signupApprovalService.register(request)
        );
        assertEquals(400, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Cannot request assignment to an archived batch"));
    }

    @Test
    void cannotRegisterWithExistingMemberEmail() {
        RegisterRequest request = new RegisterRequest("Existing", "existing@example.com", "password123", null, null);

        when(clubUserRepository.getLoginUserByEmail("existing@example.com")).thenReturn(null);
        when(memberRepository.getLoginUserByEmail("existing@example.com")).thenReturn(
                new LoginUser(5L, "Existing", "existing@example.com", "MEMBER", "hashed"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                signupApprovalService.register(request)
        );
        assertEquals(400, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("already exists"));
    }

    @Test
    void cannotRegisterWhenAlreadyPending() {
        RegisterRequest request = new RegisterRequest("Pending Guy", "pending@example.com", "password123", null, null);

        when(clubUserRepository.getLoginUserByEmail("pending@example.com")).thenReturn(null);
        when(memberRepository.getLoginUserByEmail("pending@example.com")).thenReturn(null);
        when(memberSignupRepository.findPendingByEmail("pending@example.com")).thenReturn(Optional.of(
                new MemberSignup(1L, "Pending Guy", "pending@example.com", "hash", null, null, null, "PENDING", null, null, null, LocalDateTime.now())
        ));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                signupApprovalService.register(request)
        );
        assertEquals(400, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("already pending"));
    }

    @Test
    void cannotRegisterWithShortPassword() {
        RegisterRequest request = new RegisterRequest("Short Pwd", "short@example.com", "12345", null, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                signupApprovalService.register(request)
        );
        assertEquals(400, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Password must be at least 6 characters"));
    }

    @Test
    void validRegistrationIsSavedWithHashedPasswordInPendingState() {
        RegisterRequest request = new RegisterRequest("Alice", "alice@example.com", "securePassword123", 1L, "ID-001");

        when(batchRepository.findById(1L)).thenReturn(Optional.of(
                new Batch(1L, "5th Gen", "ACTIVE", LocalDateTime.now(), LocalDateTime.now(), 5)));
        when(clubUserRepository.getLoginUserByEmail("alice@example.com")).thenReturn(null);
        when(memberRepository.getLoginUserByEmail("alice@example.com")).thenReturn(null);
        when(memberSignupRepository.findPendingByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("securePassword123")).thenReturn("bcrypt-securePassword123");
        when(memberSignupRepository.insert(eq("Alice"), eq("alice@example.com"), eq("bcrypt-securePassword123"), eq(1L), eq("ID-001")))
                .thenReturn(1);

        assertDoesNotThrow(() -> signupApprovalService.register(request));
        verify(memberSignupRepository).insert("Alice", "alice@example.com", "bcrypt-securePassword123", 1L, "ID-001");
    }
}
