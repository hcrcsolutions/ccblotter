package com.fmr.ec3.oscc.state.dto.request;

import lombok.Data;

@Data
public class SynthesizeRequest {

    private String text;
    private String voiceId;
    private boolean cacheable;
}
