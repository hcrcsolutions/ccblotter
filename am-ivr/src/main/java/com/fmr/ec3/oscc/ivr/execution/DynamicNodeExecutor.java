package com.fmr.ec3.oscc.ivr.execution;

import com.fmr.ec3.oscc.ivr.model.FlowNode;

/**
 * Executes a dynamic IVR node (HTTP callout, AI, etc.).
 */
public interface DynamicNodeExecutor {

    /**
     * Executes the given node with the provided session context.
     *
     * @param node    the flow node to execute
     * @param context the current session context
     * @return the execution result with updated variables
     */
    ExecutionResult execute(FlowNode node, SessionContext context);
}
