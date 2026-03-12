package com.fmr.ec3.oscc.ivr.server.controller;

import com.fmr.ec3.oscc.ivr.model.FlowDefinition;
import com.fmr.ec3.oscc.ivr.server.service.FlowCacheService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/flows")
public class FlowCacheController {

    private final FlowCacheService flowCacheService;

    public FlowCacheController(FlowCacheService flowCacheService) {
        this.flowCacheService = flowCacheService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void cacheFlow(@RequestBody FlowDefinition flow) {
        flowCacheService.put(flow);
    }

    @GetMapping
    public List<String> listFlows() {
        return flowCacheService.listFlowIds();
    }

    @DeleteMapping("/{flowId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFlow(@PathVariable String flowId) {
        flowCacheService.remove(flowId);
    }
}
