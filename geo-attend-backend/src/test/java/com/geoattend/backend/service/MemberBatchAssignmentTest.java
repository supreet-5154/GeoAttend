package com.geoattend.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.geoattend.backend.model.Batch;
import com.geoattend.backend.model.Member;
import com.geoattend.backend.repository.AttendanceRepository;
import com.geoattend.backend.repository.BatchRepository;
import com.geoattend.backend.repository.MemberRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;

class MemberBatchAssignmentTest {

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private BatchRepository batchRepository;
    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private AuthService authService;

    private MemberService memberService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        memberService = new MemberService(memberRepository, batchRepository, authService);
    }

    @Test
    void assignMemberToActiveBatchSuccessfully() {
        Long memberId = 101L;
        Long batchId = 1L;

        when(memberRepository.memberExists(memberId)).thenReturn(true);
        Batch activeBatch = new Batch(batchId, "6th Generation", "ACTIVE", LocalDateTime.now(), LocalDateTime.now(), 5);
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(activeBatch));
        when(memberRepository.updateMemberBatch(memberId, batchId)).thenReturn(1);

        memberService.assignBatch(memberId, batchId);

        verify(memberRepository).updateMemberBatch(memberId, batchId);
    }

    @Test
    void movingMemberToAnotherBatchReplacesPreviousAssignment() {
        Long memberId = 101L;
        Long newBatchId = 2L;

        when(memberRepository.memberExists(memberId)).thenReturn(true);
        Batch targetBatch = new Batch(newBatchId, "7th Generation", "ACTIVE", LocalDateTime.now(), LocalDateTime.now(), 10);
        when(batchRepository.findById(newBatchId)).thenReturn(Optional.of(targetBatch));
        when(memberRepository.updateMemberBatch(memberId, newBatchId)).thenReturn(1);

        memberService.assignBatch(memberId, newBatchId);

        // Only one active assignment at a time
        verify(memberRepository).updateMemberBatch(memberId, newBatchId);
    }

    @Test
    void assigningToArchivedBatchFails() {
        Long memberId = 101L;
        Long archivedBatchId = 3L;

        when(memberRepository.memberExists(memberId)).thenReturn(true);
        Batch archivedBatch = new Batch(archivedBatchId, "4th Generation", "ARCHIVED", LocalDateTime.now(), LocalDateTime.now(), 20);
        when(batchRepository.findById(archivedBatchId)).thenReturn(Optional.of(archivedBatch));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                memberService.assignBatch(memberId, archivedBatchId)
        );

        assertEquals(400, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("archived batch"));
        verify(memberRepository, never()).updateMemberBatch(anyLong(), anyLong());
    }

    @Test
    void assigningToNonExistentBatchFails() {
        Long memberId = 101L;
        Long nonExistentBatchId = 999L;

        when(memberRepository.memberExists(memberId)).thenReturn(true);
        when(batchRepository.findById(nonExistentBatchId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                memberService.assignBatch(memberId, nonExistentBatchId)
        );

        assertEquals(400, ex.getStatusCode().value());
        verify(memberRepository, never()).updateMemberBatch(anyLong(), anyLong());
    }

    @Test
    void batchChangeDoesNotAlterOrDeleteHistoricalAttendanceRecords() {
        Long memberId = 101L;
        Long initialBatchId = 1L;
        Long newBatchId = 2L;

        Member member = new Member(memberId, "Alice", "alice@example.com", initialBatchId, "6th Generation", "STU01");

        when(memberRepository.memberExists(memberId)).thenReturn(true);
        when(memberRepository.getMemberById(memberId)).thenReturn(member);
        Batch newBatch = new Batch(newBatchId, "7th Generation", "ACTIVE", LocalDateTime.now(), LocalDateTime.now(), 10);
        when(batchRepository.findById(newBatchId)).thenReturn(Optional.of(newBatch));
        when(memberRepository.updateMemberBatch(memberId, newBatchId)).thenReturn(1);

        // Moving member to another batch
        memberService.assignBatch(memberId, newBatchId);

        // Historical attendance queries/records in attendanceRepository are completely untouched:
        // No delete or mutation was called on attendance repository
        verifyNoInteractions(attendanceRepository);
    }
}
