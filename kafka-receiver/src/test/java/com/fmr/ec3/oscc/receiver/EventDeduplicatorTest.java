package com.fmr.ec3.oscc.receiver;

import com.fmr.ec3.oscc.receiver.config.ReceiverProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EventDeduplicatorTest {

    private EventDeduplicator deduplicator;

    @BeforeEach
    void setUp() {
        ReceiverProperties props = new ReceiverProperties();
        props.setDedupCacheSize(5);
        deduplicator = new EventDeduplicator(props);
    }

    @Test
    void firstOccurrenceIsNotDuplicate() {
        assertFalse(deduplicator.isDuplicate(0, "evt-1"));
    }

    @Test
    void secondOccurrenceIsDuplicate() {
        deduplicator.isDuplicate(0, "evt-1");
        assertTrue(deduplicator.isDuplicate(0, "evt-1"));
    }

    @Test
    void differentPartitionsAreIndependent() {
        deduplicator.isDuplicate(0, "evt-1");
        // Same eventId on different partition is NOT a duplicate
        assertFalse(deduplicator.isDuplicate(1, "evt-1"));
    }

    @Test
    void differentEventIdsAreNotDuplicates() {
        deduplicator.isDuplicate(0, "evt-1");
        assertFalse(deduplicator.isDuplicate(0, "evt-2"));
    }

    @Test
    void lruEvictsOldestEntries() {
        // Cache size is 5
        for (int i = 1; i <= 6; i++) {
            deduplicator.isDuplicate(0, "evt-" + i);
        }

        // evt-1 should have been evicted (size > 5)
        assertFalse(deduplicator.isDuplicate(0, "evt-1"));
        // evt-6 should still be in cache
        assertTrue(deduplicator.isDuplicate(0, "evt-6"));
    }

    @Test
    void multiplePartitionsEachHaveOwnCache() {
        // Fill partition 0
        for (int i = 1; i <= 5; i++) {
            deduplicator.isDuplicate(0, "evt-" + i);
        }
        // Fill partition 1
        for (int i = 1; i <= 5; i++) {
            deduplicator.isDuplicate(1, "evt-" + i);
        }

        // Both partitions should still have their entries
        assertTrue(deduplicator.isDuplicate(0, "evt-5"));
        assertTrue(deduplicator.isDuplicate(1, "evt-5"));
    }
}
