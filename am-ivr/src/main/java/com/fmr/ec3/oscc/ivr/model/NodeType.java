package com.fmr.ec3.oscc.ivr.model;

/**
 * Types of nodes available in an IVR flow.
 */
public enum NodeType {
    PLAY_PROMPT,
    COLLECT_DTMF,
    MENU,
    TRANSFER,
    HANGUP,
    SET_VARIABLE,
    CONDITION,
    SUB_FLOW,
    WAIT,
    HTTP_REQUEST,
    ASR_COLLECT,
    NLU_INTENT
}
