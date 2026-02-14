package com.fmr.ec3.oscc.receiver.config;

import com.fmr.ec3.oscc.receiver.EventDeduplicator;
import com.fmr.ec3.oscc.receiver.OrphanedStateWatchdog;
import com.fmr.ec3.oscc.receiver.handler.InfraEventHandler;
import com.fmr.ec3.oscc.receiver.handler.SipEventHandler;
import com.fmr.ec3.oscc.receiver.listener.InfraHeartbeatListener;
import com.fmr.ec3.oscc.receiver.listener.InfraLifecycleListener;
import com.fmr.ec3.oscc.receiver.listener.SipEventListener;
import com.fmr.ec3.oscc.receiver.state.AgentStateWriter;
import com.fmr.ec3.oscc.receiver.state.CallStateWriter;
import com.fmr.ec3.oscc.receiver.state.InfraStateWriter;
import com.fmr.ec3.oscc.receiver.state.QueueStateWriter;
import com.fmr.ec3.oscc.receiver.websocket.WebSocketBroadcaster;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Auto-configuration for kafka-receiver. Registers all receiver beans
 * so that adding kafka-receiver as a dependency is sufficient — no
 * {@code @ComponentScan} required in the host application.
 *
 * <p>Every bean uses {@code @ConditionalOnMissingBean} so the host app
 * (or standalone mode's component scan) can override any component.
 */
@AutoConfiguration
@EnableScheduling
@EnableConfigurationProperties(ReceiverProperties.class)
@Import(KafkaConsumerConfig.class)
public class KafkaReceiverAutoConfiguration {

    // ── State writers ────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    public AgentStateWriter agentStateWriter(StringRedisTemplate redisTemplate) {
        return new AgentStateWriter(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public CallStateWriter callStateWriter(StringRedisTemplate redisTemplate) {
        return new CallStateWriter(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public InfraStateWriter infraStateWriter(StringRedisTemplate redisTemplate) {
        return new InfraStateWriter(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public QueueStateWriter queueStateWriter(StringRedisTemplate redisTemplate) {
        return new QueueStateWriter(redisTemplate);
    }

    // ── WebSocket ────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(SimpMessagingTemplate.class)
    public WebSocketBroadcaster webSocketBroadcaster(
            SimpMessagingTemplate messagingTemplate,
            StringRedisTemplate redisTemplate,
            ReceiverProperties props) {
        return new WebSocketBroadcaster(messagingTemplate, redisTemplate, props);
    }

    // ── Event processing ─────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    public EventDeduplicator eventDeduplicator(ReceiverProperties props) {
        return new EventDeduplicator(props);
    }

    @Bean
    @ConditionalOnMissingBean
    public SipEventHandler sipEventHandler(
            AgentStateWriter agentWriter,
            CallStateWriter callWriter,
            QueueStateWriter queueWriter,
            WebSocketBroadcaster broadcaster) {
        return new SipEventHandler(agentWriter, callWriter, queueWriter, broadcaster);
    }

    @Bean
    @ConditionalOnMissingBean
    public InfraEventHandler infraEventHandler(
            InfraStateWriter infraWriter,
            WebSocketBroadcaster broadcaster) {
        return new InfraEventHandler(infraWriter, broadcaster);
    }

    // ── Kafka listeners ──────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    public SipEventListener sipEventListener(
            EventDeduplicator deduplicator,
            SipEventHandler handler) {
        return new SipEventListener(deduplicator, handler);
    }

    @Bean
    @ConditionalOnMissingBean
    public InfraHeartbeatListener infraHeartbeatListener(
            EventDeduplicator deduplicator,
            InfraEventHandler handler) {
        return new InfraHeartbeatListener(deduplicator, handler);
    }

    @Bean
    @ConditionalOnMissingBean
    public InfraLifecycleListener infraLifecycleListener(
            EventDeduplicator deduplicator,
            InfraEventHandler handler) {
        return new InfraLifecycleListener(deduplicator, handler);
    }

    // ── Watchdog ─────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    public OrphanedStateWatchdog orphanedStateWatchdog(
            StringRedisTemplate redisTemplate,
            ReceiverProperties props) {
        return new OrphanedStateWatchdog(redisTemplate, props);
    }
}
