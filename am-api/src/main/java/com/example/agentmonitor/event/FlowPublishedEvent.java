package com.example.agentmonitor.event;

import com.fmr.ec3.oscc.ivr.model.FlowDefinition;

import java.util.UUID;

public record FlowPublishedEvent(
        UUID flowId,
        FlowDefinition definition,
        int version
) {}
