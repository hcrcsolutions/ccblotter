package com.fmr.ec3.oscc.ivr.server.controller;

import com.fmr.ec3.oscc.ivr.server.dto.ExecuteNodeRequest;
import com.fmr.ec3.oscc.ivr.server.dto.ExecuteNodeResult;
import com.fmr.ec3.oscc.ivr.server.service.NodeExecutionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ExecutionController {

    private final NodeExecutionService nodeExecutionService;

    public ExecutionController(NodeExecutionService nodeExecutionService) {
        this.nodeExecutionService = nodeExecutionService;
    }

    @PostMapping("/execute")
    public ExecuteNodeResult executeNode(@Valid @RequestBody ExecuteNodeRequest request) {
        return nodeExecutionService.executeNode(request);
    }
}
