package com.fmr.ec3.oscc.state.entity;

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
