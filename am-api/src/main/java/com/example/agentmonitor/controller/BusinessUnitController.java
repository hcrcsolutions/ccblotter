package com.example.agentmonitor.controller;

import com.example.agentmonitor.entity.BusinessUnitEntity;
import com.example.agentmonitor.repository.BusinessUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/business-units")
@RequiredArgsConstructor
public class BusinessUnitController {

    private final BusinessUnitRepository businessUnitRepository;

    @GetMapping
    public ResponseEntity<List<String>> getBusinessUnits() {
        List<String> names = businessUnitRepository.findAllByOrderByNameAsc()
                .stream()
                .map(BusinessUnitEntity::getName)
                .toList();
        return ResponseEntity.ok(names);
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> createBusinessUnit(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String trimmed = name.trim();
        if (businessUnitRepository.existsById(trimmed)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        BusinessUnitEntity entity = BusinessUnitEntity.builder()
                .name(trimmed)
                .build();
        businessUnitRepository.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("name", trimmed));
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> deleteBusinessUnit(@PathVariable String name) {
        businessUnitRepository.deleteById(name);
        return ResponseEntity.noContent().build();
    }
}
