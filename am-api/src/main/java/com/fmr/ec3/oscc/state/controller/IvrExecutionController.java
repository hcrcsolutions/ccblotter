package com.fmr.ec3.oscc.state.controller;

import com.fmr.ec3.oscc.state.dto.request.ExecuteNodeRequest;
import com.fmr.ec3.oscc.state.dto.response.ExecuteNodeResultDto;
import com.fmr.ec3.oscc.state.service.IvrExecutionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ivr")
@RequiredArgsConstructor
@Slf4j
@Validated
public class IvrExecutionController {

    private final IvrExecutionService executionService;

    /**
     * Execute a dynamic IVR node (HTTP callout, AI, etc.).
     */
    @PostMapping("/execute")
    public ResponseEntity<ExecuteNodeResultDto> executeNode(
            @Valid @RequestBody ExecuteNodeRequest request) {
        log.debug("Execute node request: flow={} node={}", request.getFlowId(), request.getNodeId());
        return ResponseEntity.ok(executionService.executeNode(request));
    }
}
