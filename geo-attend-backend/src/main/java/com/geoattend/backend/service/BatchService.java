package com.geoattend.backend.service;

import com.geoattend.backend.model.Batch;
import com.geoattend.backend.model.PagedResponse;
import com.geoattend.backend.repository.BatchRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class BatchService {

    private final BatchRepository batchRepository;

    public BatchService(BatchRepository batchRepository) {
        this.batchRepository = batchRepository;
    }

    public List<Batch> getAllBatches() {
        return batchRepository.findAll();
    }

    public PagedResponse<Batch> getPagedBatches(String search, String status,
                                               String sortBy, String sortDirection,
                                               int page, int size) {
        int pageNum = Math.max(0, page);
        int pageSize = Math.max(1, Math.min(size, 100));
        int offset = pageNum * pageSize;

        long total = batchRepository.countBatches(search, status);
        List<Batch> content = batchRepository.findBatches(search, status, sortBy, sortDirection, offset, pageSize);
        return new PagedResponse<>(content, pageNum, pageSize, total);
    }

    public List<Batch> getFilteredBatches(String search, String status,
                                         String sortBy, String sortDirection) {
        return batchRepository.findBatches(search, status, sortBy, sortDirection, null, null);
    }

    public List<Batch> getActiveBatches() {
        return batchRepository.findActive();
    }

    public Batch getBatchById(Long id) {
        return batchRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch not found with ID: " + id));
    }

    @Transactional
    public Batch createBatch(String name, String status) {
        if (name == null || name.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Batch name is required.");
        }
        String trimmedName = name.trim();
        String initialStatus = (status == null || status.isBlank()) ? "ACTIVE" : status.trim().toUpperCase();
        if (!"ACTIVE".equals(initialStatus) && !"ARCHIVED".equals(initialStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Batch status must be ACTIVE or ARCHIVED.");
        }

        if (batchRepository.findByName(trimmedName).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A batch with name '" + trimmedName + "' already exists.");
        }

        try {
            batchRepository.create(trimmedName, initialStatus);
            return batchRepository.findByName(trimmedName)
                    .orElseThrow(() -> new IllegalStateException("Failed to retrieve created batch."));
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A batch with name '" + trimmedName + "' already exists.");
        }
    }

    @Transactional
    public Batch updateBatch(Long id, String name, String status) {
        Batch existing = getBatchById(id);
        String updatedName = (name != null && !name.trim().isEmpty()) ? name.trim() : existing.getName();
        String updatedStatus = (status != null && !status.trim().isEmpty()) ? status.trim().toUpperCase() : existing.getStatus();

        if (!"ACTIVE".equals(updatedStatus) && !"ARCHIVED".equals(updatedStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Batch status must be ACTIVE or ARCHIVED.");
        }

        if (!existing.getName().equalsIgnoreCase(updatedName)) {
            batchRepository.findByName(updatedName).ifPresent(b -> {
                if (!b.getId().equals(id)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "A batch with name '" + updatedName + "' already exists.");
                }
            });
        }

        batchRepository.update(id, updatedName, updatedStatus);
        return getBatchById(id);
    }

    @Transactional
    public Batch setBatchStatus(Long id, String status) {
        getBatchById(id);
        String newStatus = status != null ? status.trim().toUpperCase() : "";
        if (!"ACTIVE".equals(newStatus) && !"ARCHIVED".equals(newStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status. Must be ACTIVE or ARCHIVED.");
        }
        batchRepository.updateStatus(id, newStatus);
        return getBatchById(id);
    }

    @Transactional
    public Batch archiveBatch(Long id) {
        return setBatchStatus(id, "ARCHIVED");
    }

    @Transactional
    public Batch activateBatch(Long id) {
        return setBatchStatus(id, "ACTIVE");
    }
}
