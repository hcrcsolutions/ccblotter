package com.fmr.ec3.oscc.ivr.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fmr.ec3.oscc.ivr.model.FlowDefinition;
import com.fmr.ec3.oscc.ivr.model.FlowDefinitionFactory;
import com.fmr.ec3.oscc.ivr.model.FlowStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class FlowCacheLoader implements ApplicationRunner {

    private static final String LOAD_PUBLISHED_FLOWS_SQL = """
            SELECT f.id, f.name, f.description,
                   f.entry_node_id,
                   f.max_session_duration_seconds, f.max_steps,
                   f.version,
                   c.content::text
            FROM osccstate.ivr_flows f
            JOIN osccstate.ivr_flow_content c
              ON c.flow_id = f.id AND c.version = f.version
            WHERE f.status = 'PUBLISHED'
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final FlowCacheService flowCacheService;
    private final KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;
    private final boolean dbInitEnabled;

    public FlowCacheLoader(JdbcTemplate jdbcTemplate,
                           ObjectMapper objectMapper,
                           FlowCacheService flowCacheService,
                           KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry,
                           @Value("${ivr.flow-cache.db-init-enabled:true}") boolean dbInitEnabled) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.flowCacheService = flowCacheService;
        this.kafkaListenerEndpointRegistry = kafkaListenerEndpointRegistry;
        this.dbInitEnabled = dbInitEnabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (dbInitEnabled) {
            try {
                loadFlowsFromDb();
            } catch (Exception e) {
                log.warn("Failed to initialize flow cache from DB"
                        + " — cache will be populated via Kafka events",
                        e);
            }
        }

        kafkaListenerEndpointRegistry.start();
        log.info("Kafka flow event listener started");
    }

    private void loadFlowsFromDb() {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(LOAD_PUBLISHED_FLOWS_SQL);
        int loaded = 0;

        for (Map<String, Object> row : rows) {
            String flowId = row.get("id").toString();
            try {
                FlowDefinition flow =
                        FlowDefinitionFactory.fromContentJson(
                                objectMapper,
                                flowId,
                                (String) row.get("name"),
                                (String) row.get("description"),
                                (String) row.get("entry_node_id"),
                                (int) row.get(
                                        "max_session_duration_seconds"),
                                (int) row.get("max_steps"),
                                (int) row.get("version"),
                                FlowStatus.PUBLISHED,
                                (String) row.get("content"));
                flowCacheService.put(flow);
                loaded++;
            } catch (Exception e) {
                log.warn("Skipping corrupt flow id={}: {}",
                        flowId, e.getMessage());
            }
        }

        log.info("Flow cache initialized from DB: "
                + "loaded {} published flow(s)", loaded);
    }
}
