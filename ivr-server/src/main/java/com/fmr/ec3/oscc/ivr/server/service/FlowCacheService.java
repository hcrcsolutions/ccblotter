package com.fmr.ec3.oscc.ivr.server.service;

import com.fmr.ec3.oscc.ivr.model.FlowDefinition;
import com.fmr.ec3.oscc.ivr.server.exception.FlowNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class FlowCacheService {

    private final ConcurrentHashMap<String, FlowDefinition> cache = new ConcurrentHashMap<>();

    public void put(FlowDefinition flow) {
        cache.merge(flow.getId(), flow, (existing, incoming) -> {
            if (incoming.getVersion() >= existing.getVersion()) {
                return incoming;
            }
            log.debug("Rejected stale flow '{}' (id={}, version {} < cached {})",
                    incoming.getName(), incoming.getId(),
                    incoming.getVersion(), existing.getVersion());
            return existing;
        });
        log.debug("Cached flow '{}' (id={}, version={})",
                flow.getName(), flow.getId(), flow.getVersion());
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
