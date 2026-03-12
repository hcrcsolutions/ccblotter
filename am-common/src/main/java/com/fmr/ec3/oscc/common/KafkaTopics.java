package com.fmr.ec3.oscc.common;

public final class KafkaTopics {

    private KafkaTopics() {}

    public static final String SIP_EVENTS = "ccblotter.sip.events";
    public static final String IVR_EVENTS = "ccblotter.ivr.events";
    public static final String INFRA_HEARTBEATS = "ccblotter.infra.heartbeats";
    public static final String INFRA_LIFECYCLE = "ccblotter.infra.lifecycle";
    public static final String IVR_FLOWS = "ccblotter.ivr.flows";
}
