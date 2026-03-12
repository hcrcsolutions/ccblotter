package com.fmr.ec3.oscc.ivr.server.controller;

import com.fmr.ec3.oscc.ivr.server.exception.FlowNotFoundException;
import com.fmr.ec3.oscc.ivr.server.exception.GlobalExceptionHandler;
import com.fmr.ec3.oscc.ivr.server.service.FlowCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FlowCacheController.class)
@Import(GlobalExceptionHandler.class)
class FlowCacheControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FlowCacheService flowCacheService;

    @Test
    void postFlowDefinition() throws Exception {
        String json = """
                {
                  "id": "flow-1",
                  "name": "Test Flow",
                  "version": 1,
                  "entryNodeId": "n1",
                  "nodes": [
                    {
                      "id": "n1",
                      "type": "HTTP_REQUEST",
                      "label": "Call API",
                      "config": {
                        "url": "https://httpbin.org/get",
                        "method": "GET",
                        "responseVariable": "r",
                        "timeoutSeconds": 10
                      }
                    }
                  ],
                  "edges": [],
                  "variables": []
                }
                """;

        mockMvc.perform(post("/api/v1/flows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());

        verify(flowCacheService).put(any());
    }

    @Test
    void listFlows() throws Exception {
        when(flowCacheService.listFlowIds())
                .thenReturn(List.of("flow-1", "flow-2"));

        mockMvc.perform(get("/api/v1/flows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("flow-1"))
                .andExpect(jsonPath("$[1]").value("flow-2"));
    }

    @Test
    void deleteFlow() throws Exception {
        mockMvc.perform(delete("/api/v1/flows/flow-1"))
                .andExpect(status().isNoContent());

        verify(flowCacheService).remove("flow-1");
    }

    @Test
    void deleteFlowNotFound() throws Exception {
        doThrow(new FlowNotFoundException("flow-1"))
                .when(flowCacheService).remove("flow-1");

        mockMvc.perform(delete("/api/v1/flows/flow-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("FLOW_NOT_FOUND"));
    }
}
