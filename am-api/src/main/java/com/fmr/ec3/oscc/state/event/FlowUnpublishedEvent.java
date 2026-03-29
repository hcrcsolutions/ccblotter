package com.fmr.ec3.oscc.state.event;

import java.util.UUID;

public record FlowUnpublishedEvent(
        UUID flowId
) {}
