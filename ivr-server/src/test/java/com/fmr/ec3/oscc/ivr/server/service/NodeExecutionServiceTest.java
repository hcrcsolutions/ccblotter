package com.fmr.ec3.oscc.ivr.server.service;

import com.fmr.ec3.oscc.ivr.IvrFlowException;
import com.fmr.ec3.oscc.ivr.model.FlowDefinition;
import com.fmr.ec3.oscc.ivr.model.FlowNode;
import com.fmr.ec3.oscc.ivr.model.NodeType;
import com.fmr.ec3.oscc.ivr.server.dto.ExecuteNodeRequest;
import com.fmr.ec3.oscc.ivr.server.exception.FlowNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeExecutionServiceTest {

    @Mock
    private FlowCacheService flowCacheService;

    private NodeExecutionService service;

    @BeforeEach
    void setUp() {
        service = new NodeExecutionService(flowCacheService);
    }

    @Test
    void flowNotFound() {
        when(flowCacheService.get("missing"))
                .thenThrow(new FlowNotFoundException("missing"));

        ExecuteNodeRequest request = new ExecuteNodeRequest();
        request.setFlowId("missing");
        request.setNodeId("n1");
        request.setCallId("c1");
        request.setVariables(Map.of());

        assertThatThrownBy(() -> service.executeNode(request))
                .isInstanceOf(FlowNotFoundException.class);
    }

    @Test
    void nodeNotFound() {
        FlowDefinition flow = FlowDefinition.builder()
                .id("f1")
                .name("Test")
                .nodes(List.of())
                .build();
        when(flowCacheService.get("f1")).thenReturn(flow);

        ExecuteNodeRequest request = new ExecuteNodeRequest();
        request.setFlowId("f1");
        request.setNodeId("missing-node");
        request.setCallId("c1");
        request.setVariables(Map.of());

        assertThatThrownBy(() -> service.executeNode(request))
                .isInstanceOf(IvrFlowException.class)
                .hasMessageContaining("Node not found");
    }

    @Test
    void unsupportedNodeType() {
        FlowNode asrNode = FlowNode.builder()
                .id("n1")
                .type(NodeType.ASR_COLLECT)
                .config(new HashMap<>())
                .build();
        FlowDefinition flow = FlowDefinition.builder()
                .id("f1")
                .name("Test")
                .nodes(List.of(asrNode))
                .build();
        when(flowCacheService.get("f1")).thenReturn(flow);

        ExecuteNodeRequest request = new ExecuteNodeRequest();
        request.setFlowId("f1");
        request.setNodeId("n1");
        request.setCallId("c1");
        request.setVariables(Map.of());

        assertThatThrownBy(() -> service.executeNode(request))
                .isInstanceOf(IvrFlowException.class)
                .hasMessageContaining("Unsupported node type in ivr-server");
    }
}
