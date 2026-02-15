package com.fmr.ec3.oscc.receiver;

import com.fmr.ec3.oscc.receiver.config.ReceiverProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-partition LRU cache of recent eventIds for deduplication.
 */
public class EventDeduplicator {

    private static final Logger log = LoggerFactory.getLogger(EventDeduplicator.class);

    private final int maxSize;
    private final ConcurrentHashMap<Integer, LinkedHashMap<String, Boolean>> partitionCaches =
        new ConcurrentHashMap<>();

    public EventDeduplicator(ReceiverProperties props) {
        this.maxSize = props.getDedupCacheSize();
    }

    /**
     * Returns true if this event has already been seen (duplicate).
     */
    public boolean isDuplicate(int partition, String eventId) {
        if (eventId == null) {
            log.warn("Event with null eventId on partition {} - cannot deduplicate, processing anyway", partition);
            return false;
        }

        LinkedHashMap<String, Boolean> cache = partitionCaches.computeIfAbsent(partition,
            k -> new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > maxSize;
                }
            });

        synchronized (cache) {
            if (cache.containsKey(eventId)) {
                return true;
            }
            cache.put(eventId, Boolean.TRUE);
            return false;
        }
    }
}
