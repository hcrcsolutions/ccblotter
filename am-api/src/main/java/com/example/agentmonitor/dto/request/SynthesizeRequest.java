package com.example.agentmonitor.dto.request;

import lombok.Data;

@Data
public class SynthesizeRequest {

    private String text;
    private String voiceId;
    private boolean cacheable;
}
