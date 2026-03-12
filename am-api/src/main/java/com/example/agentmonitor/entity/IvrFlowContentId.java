package com.example.agentmonitor.entity;

import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IvrFlowContentId implements Serializable {

    private UUID flowId;
    private int version;
}
