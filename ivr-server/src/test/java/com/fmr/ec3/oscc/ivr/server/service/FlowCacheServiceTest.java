package com.fmr.ec3.oscc.ivr.server.service;

import com.fmr.ec3.oscc.ivr.model.FlowDefinition;
import com.fmr.ec3.oscc.ivr.server.exception.FlowNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowCacheServiceTest {

    private FlowCacheService service;

    @BeforeEach
    void setUp() {
        service = new FlowCacheService();
    }

    @Test
    void putAndGet() {
        FlowDefinition flow = FlowDefinition.builder()
                .id("f1")
                .name("Test Flow")
                .build();

        service.put(flow);

        assertThat(service.get("f1")).isSameAs(flow);
    }

    @Test
    void getNotFound() {
        assertThatThrownBy(() -> service.get("missing"))
                .isInstanceOf(FlowNotFoundException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void remove() {
        service.put(FlowDefinition.builder().id("f1").name("Flow").build());
        service.remove("f1");

        assertThat(service.size()).isZero();
    }

    @Test
    void removeNotFound() {
        assertThatThrownBy(() -> service.remove("missing"))
                .isInstanceOf(FlowNotFoundException.class);
    }

    @Test
    void listFlowIdsSorted() {
        service.put(FlowDefinition.builder().id("z-flow").name("Z").build());
        service.put(FlowDefinition.builder().id("a-flow").name("A").build());
        service.put(FlowDefinition.builder().id("m-flow").name("M").build());

        List<String> ids = service.listFlowIds();

        assertThat(ids).containsExactly("a-flow", "m-flow", "z-flow");
    }

    @Test
    void overwrite() {
        service.put(FlowDefinition.builder().id("f1").name("V1").version(1).build());

        FlowDefinition v2 = FlowDefinition.builder().id("f1").name("V2").version(2).build();
        service.put(v2);

        assertThat(service.get("f1").getName()).isEqualTo("V2");
        assertThat(service.size()).isEqualTo(1);
    }

    @Test
    void putRejectsOlderVersion() {
        service.put(FlowDefinition.builder().id("f1").name("V3").version(3).build());

        service.put(FlowDefinition.builder().id("f1").name("V1").version(1).build());

        assertThat(service.get("f1").getName()).isEqualTo("V3");
        assertThat(service.get("f1").getVersion()).isEqualTo(3);
    }

    @Test
    void putAcceptsSameVersion() {
        service.put(FlowDefinition.builder().id("f1").name("V2-old").version(2).build());

        service.put(FlowDefinition.builder().id("f1").name("V2-new").version(2).build());

        assertThat(service.get("f1").getName()).isEqualTo("V2-new");
    }

    @Test
    void size() {
        assertThat(service.size()).isZero();

        service.put(FlowDefinition.builder().id("f1").name("A").build());
        service.put(FlowDefinition.builder().id("f2").name("B").build());

        assertThat(service.size()).isEqualTo(2);
    }
}
