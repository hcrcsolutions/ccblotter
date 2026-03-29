package com.fmr.ec3.oscc.state.controller;

import com.fmr.ec3.oscc.state.dto.response.RecordingDto;
import com.fmr.ec3.oscc.state.service.RecordingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/recordings")
@RequiredArgsConstructor
@Slf4j
public class RecordingController {

    private final RecordingService recordingService;

    @PostMapping
    public ResponseEntity<RecordingDto> upload(@RequestParam("file") MultipartFile file) throws IOException {
        RecordingDto dto = recordingService.store(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping
    public ResponseEntity<List<RecordingDto>> listAll() throws IOException {
        return ResponseEntity.ok(recordingService.listAll());
    }

    @GetMapping("/{filename}")
    public ResponseEntity<Resource> download(@PathVariable String filename) {
        Resource resource = recordingService.loadAsResource(filename);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/wav"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(resource);
    }

    @DeleteMapping("/{filename}")
    public ResponseEntity<Void> delete(@PathVariable String filename) throws IOException {
        recordingService.delete(filename);
        return ResponseEntity.noContent().build();
    }
}
