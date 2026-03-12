package com.fmr.ec3.oscc.ivr.server.service;

import com.fmr.ec3.oscc.ivr.model.FlowDefinition;
import com.fmr.ec3.oscc.ivr.server.exception.FlowNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FlowCacheService {

    private static final Logger log = LoggerFactory.getLogger(FlowCacheService.class);

    private final ConcurrentHashMap<String, FlowDefinition> cache = new ConcurrentHashMap<>();

    public void put(FlowDefinition flow) {
        cache.put(flow.getId(), flow);
        log.debug("Cached flow '{}' (id={})", flow.getName(), flow.getId());
    }

    public FlowDefinition get(String flowId) {
        FlowDefinition flow = cache.get(flowId);
        if (flow == null) {
            throw new FlowNotFoundException(flowId);
        }
        return flow;
    }

    public void remove(String flowId) {
        if (cache.remove(flowId) == null) {
            throw new FlowNotFoundException(flowId);
        }
        log.debug("Removed flow from cache: {}", flowId);
    }

    public List<String> listFlowIds() {
        return cache.keySet().stream().sorted().toList();
    }

    public boolean removeIfPresent(String flowId) {
        boolean removed = cache.remove(flowId) != null;
        if (removed) {
            log.debug("Removed flow from cache: {}", flowId);
        }
        return removed;
    }

    public int size() {
        return cache.size();
    }
}
