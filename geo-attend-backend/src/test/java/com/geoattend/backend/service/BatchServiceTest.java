package com.geoattend.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.geoattend.backend.model.Batch;
import com.geoattend.backend.repository.BatchRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;

class BatchServiceTest {

    @Mock
    private BatchRepository batchRepository;

    private BatchService batchService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        batchService = new BatchService(batchRepository);
    }

    @Test
    void createBatchSuccessfully() {
        Batch created = new Batch(10L, "7th Generation", "ACTIVE", LocalDateTime.now(), LocalDateTime.now(), 0);
        when(batchRepository.findByName("7th Generation")).thenReturn(Optional.empty(), Optional.of(created));
        when(batchRepository.create("7th Generation", "ACTIVE")).thenReturn(1);

        Batch result = batchService.createBatch("7th Generation", "ACTIVE");
        assertNotNull(result);
        assertEquals("7th Generation", result.getName());
        assertEquals("ACTIVE", result.getStatus());
        verify(batchRepository).create("7th Generation", "ACTIVE");
    }

    @Test
    void createBatchWithDuplicateNameFails() {
        Batch existing = new Batch(1L, "5th Generation", "ACTIVE", LocalDateTime.now(), LocalDateTime.now(), 5);
        when(batchRepository.findByName("5th Generation")).thenReturn(Optional.of(existing));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                batchService.createBatch("5th Generation", "ACTIVE")
        );
        assertEquals(409, ex.getStatusCode().value());
        verify(batchRepository, never()).create(anyString(), anyString());
    }

    @Test
    void createBatchWithBlankNameFails() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                batchService.createBatch("   ", "ACTIVE")
        );
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void archiveBatchSuccessfully() {
        Batch activeBatch = new Batch(2L, "6th Generation", "ACTIVE", LocalDateTime.now(), LocalDateTime.now(), 12);
        Batch archivedBatch = new Batch(2L, "6th Generation", "ARCHIVED", LocalDateTime.now(), LocalDateTime.now(), 12);

        when(batchRepository.findById(2L)).thenReturn(Optional.of(activeBatch), Optional.of(archivedBatch));
        when(batchRepository.updateStatus(2L, "ARCHIVED")).thenReturn(1);

        Batch result = batchService.archiveBatch(2L);
        assertEquals("ARCHIVED", result.getStatus());
        verify(batchRepository).updateStatus(2L, "ARCHIVED");
    }

    @Test
    void activateBatchSuccessfully() {
        Batch archivedBatch = new Batch(3L, "5th Generation", "ARCHIVED", LocalDateTime.now(), LocalDateTime.now(), 8);
        Batch activeBatch = new Batch(3L, "5th Generation", "ACTIVE", LocalDateTime.now(), LocalDateTime.now(), 8);

        when(batchRepository.findById(3L)).thenReturn(Optional.of(archivedBatch), Optional.of(activeBatch));
        when(batchRepository.updateStatus(3L, "ACTIVE")).thenReturn(1);

        Batch result = batchService.activateBatch(3L);
        assertEquals("ACTIVE", result.getStatus());
        verify(batchRepository).updateStatus(3L, "ACTIVE");
    }

    @Test
    void getBatchByIdThrowsNotFoundWhenMissing() {
        when(batchRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                batchService.getBatchById(999L)
        );
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void getAllBatchesReturnsBatchesWithMemberCounts() {
        List<Batch> batches = List.of(
                new Batch(1L, "5th Generation", "ACTIVE", LocalDateTime.now(), LocalDateTime.now(), 15),
                new Batch(2L, "6th Generation", "ACTIVE", LocalDateTime.now(), LocalDateTime.now(), 20)
        );
        when(batchRepository.findAll()).thenReturn(batches);

        List<Batch> result = batchService.getAllBatches();
        assertEquals(2, result.size());
        assertEquals(15, result.get(0).getMemberCount());
        assertEquals(20, result.get(1).getMemberCount());
    }
}
