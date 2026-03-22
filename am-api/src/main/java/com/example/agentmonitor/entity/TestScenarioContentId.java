package com.example.agentmonitor.entity;

import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestScenarioContentId implements Serializable {

    private UUID scenarioId;
    private int version;
}
