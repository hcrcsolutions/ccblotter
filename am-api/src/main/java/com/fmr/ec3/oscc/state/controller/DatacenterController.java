package com.fmr.ec3.oscc.state.controller;

import com.fmr.ec3.oscc.state.entity.DatacenterEntity;
import com.fmr.ec3.oscc.state.repository.DatacenterRepository;
import com.fmr.ec3.oscc.state.repository.NodeRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/datacenters")
@RequiredArgsConstructor
@Slf4j
@Validated
public class DatacenterController {

    private final DatacenterRepository datacenterRepository;
    private final NodeRepository nodeRepository;

    /**
     * List all datacenters.
     */
    @GetMapping
    public ResponseEntity<List<DatacenterEntity>> listDatacenters() {
        List<DatacenterEntity> datacenters = datacenterRepository.findAll();
        return ResponseEntity.ok(datacenters);
    }

    /**
     * Get a datacenter by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<DatacenterEntity> getDatacenter(@PathVariable String id) {
        return datacenterRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a new datacenter.
     */
    @PostMapping
    public ResponseEntity<?> createDatacenter(@Valid @RequestBody DatacenterRequest request) {
        if (datacenterRepository.existsById(request.id())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "DATACENTER_EXISTS", "message", "Datacenter already exists: " + request.id()));
        }

        Instant now = Instant.now();
        DatacenterEntity datacenter = DatacenterEntity.builder()
                .id(request.id())
                .region(request.region())
                .displayName(request.displayName())
                .createdAt(now)
                .updatedAt(now)
                .build();

        datacenterRepository.save(datacenter);
        log.info("Created datacenter: {} ({})", request.id(), request.region());

        return ResponseEntity.status(HttpStatus.CREATED).body(datacenter);
    }

    /**
     * Update a datacenter.
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateDatacenter(
            @PathVariable String id,
            @Valid @RequestBody DatacenterRequest request) {

        return datacenterRepository.findById(id)
                .map(existing -> {
                    existing.setRegion(request.region());
                    existing.setDisplayName(request.displayName());
                    existing.setUpdatedAt(Instant.now());
                    datacenterRepository.save(existing);
                    log.info("Updated datacenter: {}", id);
                    return ResponseEntity.ok(existing);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete a datacenter.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteDatacenter(@PathVariable String id) {
        if (!datacenterRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        // Check if datacenter has nodes
        long nodeCount = nodeRepository.countByDatacenterId(id);
        if (nodeCount > 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error", "DATACENTER_HAS_NODES",
                            "message", "Cannot delete datacenter with " + nodeCount + " nodes. Remove nodes first."
                    ));
        }

        datacenterRepository.deleteById(id);
        log.info("Deleted datacenter: {}", id);

        return ResponseEntity.noContent().build();
    }

    /**
     * Request DTO for creating/updating datacenters.
     */
    public record DatacenterRequest(
            @NotBlank(message = "ID is required") String id,
            @NotBlank(message = "Region is required") String region,
            String displayName
    ) {}
}
