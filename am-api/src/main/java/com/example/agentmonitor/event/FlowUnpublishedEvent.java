package com.example.agentmonitor.event;

import java.util.UUID;

public record FlowUnpublishedEvent(
        UUID flowId
) {}
