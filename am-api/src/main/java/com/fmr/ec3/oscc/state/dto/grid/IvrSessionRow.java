package com.fmr.ec3.oscc.state.dto.grid;

import com.fmr.ec3.oscc.state.model.IvrState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * IVR session data for the grid view.
 * Same fields as IvrSession, used in GridResponse.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IvrSessionRow {

    private String id;

    private String callId;

    private String originator;

    private String flowId;

    private IvrState state;

    private String currentStep;

    private int stepCount;

    private Instant startTime;

    private Instant endTime;

    private boolean authenticated;
}
