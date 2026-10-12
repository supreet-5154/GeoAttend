package com.geoattend.backend.controller;

import com.geoattend.backend.model.ApiResponse;
import com.geoattend.backend.model.Batch;
import com.geoattend.backend.model.PagedResponse;
import com.geoattend.backend.service.BatchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/batches")
public class BatchController {

    private final BatchService batchService;

    public BatchController(BatchService batchService) {
        this.batchService = batchService;
    }

    @GetMapping
    public ResponseEntity<?> getAllBatches(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "name") String sortBy,
            @RequestParam(required = false, defaultValue = "asc") String sortDirection,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        if (page != null && size != null) {
            PagedResponse<Batch> response = batchService.getPagedBatches(
                    search, status, sortBy, sortDirection, page, size);
            return ResponseEntity.ok(response);
        } else {
            List<Batch> list = batchService.getFilteredBatches(
                    search, status, sortBy, sortDirection);
            return ResponseEntity.ok()
                    .header("X-Total-Count", String.valueOf(list.size()))
                    .body(list);
        }
    }

    @GetMapping("/active")
    public List<Batch> getActiveBatches() {
        return batchService.getActiveBatches();
    }

    @GetMapping("/{id}")
    public Batch getBatchById(@PathVariable Long id) {
        return batchService.getBatchById(id);
    }

    @PostMapping
    public ResponseEntity<?> createBatch(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        String status = body.getOrDefault("status", "ACTIVE");
        Batch batch = batchService.createBatch(name, status);
        return ResponseEntity.ok(batch);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateBatch(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String name = body.get("name");
        String status = body.get("status");
        Batch batch = batchService.updateBatch(id, name, status);
        return ResponseEntity.ok(batch);
    }

    @PutMapping("/{id}/archive")
    public ResponseEntity<?> archiveBatch(@PathVariable Long id) {
        Batch batch = batchService.archiveBatch(id);
        return ResponseEntity.ok(batch);
    }

    @PutMapping("/{id}/activate")
    public ResponseEntity<?> activateBatch(@PathVariable Long id) {
        Batch batch = batchService.activateBatch(id);
        return ResponseEntity.ok(batch);
    }
}
