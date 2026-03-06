package com.fmr.ec3.oscc.sipserver;

import com.fmr.ec3.oscc.common.payload.infra.SessionBreakdownDto;
import com.fmr.ec3.oscc.sipserver.model.DialogRole;
import com.fmr.ec3.oscc.sipserver.model.Direction;
import com.fmr.ec3.oscc.sipserver.model.Interaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe store for active interactions with O(1) session counts.
 *
 * <p>All mutations go through {@code compute()} which serializes per-key,
 * preventing races between concurrent updates/removes on the same interaction.
 * Atomic counters are updated inside compute(), so they stay consistent
 * with the map contents.</p>
 *
 * <p>Invariants enforced:
 * <ul>
 *   <li>{@code totalCount == interactions.size()}</li>
 *   <li>{@code totalCount == inbound + outbound}</li>
 *   <li>{@code totalCount == ivr + queue + agent + onHold}</li>
 *   <li>No counter is ever negative</li>
 * </ul>
 */
@Component
public class InteractionStore {

    private static final Logger log = LoggerFactory.getLogger(InteractionStore.class);

    private final ConcurrentHashMap<String, Interaction> interactions = new ConcurrentHashMap<>();

    private final AtomicInteger totalCount = new AtomicInteger();
    private final EnumMap<Direction, AtomicInteger> directionCounts;
    private final EnumMap<DialogRole, AtomicInteger> legBCounts;

    public InteractionStore() {
        directionCounts = new EnumMap<>(Direction.class);
        for (Direction d : Direction.values()) {
            directionCounts.put(d, new AtomicInteger());
        }

        legBCounts = new EnumMap<>(DialogRole.class);
        for (DialogRole role : DialogRole.values()) {
            if (role.isLegB()) {
                legBCounts.put(role, new AtomicInteger());
            }
        }
    }

    /**
     * Adds a new interaction. The initial leg B role determines which
     * session category it counts toward (IVR, QUEUE, etc.).
     *
     * @throws IllegalStateException    if an interaction with this ID already exists
     * @throws IllegalArgumentException if initialLegBRole is not a leg B state
     */
    public Interaction addInteraction(String id, Direction direction, DialogRole initialLegBRole) {
        if (!initialLegBRole.isLegB()) {
            throw new IllegalArgumentException(
                    "Initial leg B role must be a leg B state, got: " + initialLegBRole);
        }

        Interaction[] created = new Interaction[1];
        interactions.compute(id, (key, existing) -> {
            if (existing != null) {
                throw new IllegalStateException("Interaction already exists: " + id);
            }
            Interaction interaction = new Interaction(id, direction, initialLegBRole);
            totalCount.incrementAndGet();
            directionCounts.get(direction).incrementAndGet();
            legBCounts.get(initialLegBRole).incrementAndGet();
            created[0] = interaction;
            return interaction;
        });

        log.debug("Added interaction {}: direction={}, legB={}",
                id, direction, initialLegBRole);
        return created[0];
    }

    /**
     * Transitions the leg B role for an existing interaction.
     * Atomically decrements the old role counter and increments the new one.
     *
     * @throws IllegalArgumentException if newRole is not a leg B state
     */
    public void updateLegBRole(String id, DialogRole newRole) {
        if (!newRole.isLegB()) {
            throw new IllegalArgumentException(
                    "New role must be a leg B state, got: " + newRole);
        }

        interactions.compute(id, (key, interaction) -> {
            if (interaction == null) {
                log.warn("updateLegBRole called for unknown interaction: {}", id);
                return null;
            }
            DialogRole oldRole = interaction.getActiveLegBRole();
            if (oldRole == newRole) {
                return interaction;
            }
            safeDecrement(legBCounts.get(oldRole), "legB." + oldRole.name());
            legBCounts.get(newRole).incrementAndGet();
            interaction.setActiveLegBRole(newRole);
            return interaction;
        });
    }

    /**
     * Removes an interaction and adjusts all counters.
     * Returns the removed interaction, or null if it didn't exist.
     */
    public Interaction removeInteraction(String id) {
        Interaction[] removed = new Interaction[1];
        interactions.compute(id, (key, interaction) -> {
            if (interaction == null) {
                log.warn("removeInteraction called for unknown interaction: {}", id);
                return null;
            }
            safeDecrement(totalCount, "total");
            safeDecrement(directionCounts.get(interaction.getDirection()),
                    "direction." + interaction.getDirection().name());
            safeDecrement(legBCounts.get(interaction.getActiveLegBRole()),
                    "legB." + interaction.getActiveLegBRole().name());
            removed[0] = interaction;
            return null; // removes from map
        });

        if (removed[0] != null) {
            log.debug("Removed interaction {}", id);
        }
        return removed[0];
    }

    /**
     * Returns the interaction for the given ID, or null.
     */
    public Interaction get(String id) {
        return interactions.get(id);
    }

    // ---- O(1) count accessors ----

    public int getActiveSessions() {
        return totalCount.get();
    }

    public SessionBreakdownDto getSessionBreakdown() {
        return new SessionBreakdownDto(
                directionCounts.get(Direction.INBOUND).get(),
                directionCounts.get(Direction.OUTBOUND).get(),
                legBCounts.get(DialogRole.IVR).get(),
                legBCounts.get(DialogRole.QUEUE).get(),
                legBCounts.get(DialogRole.AGENT).get(),
                legBCounts.get(DialogRole.ON_HOLD).get()
        );
    }

    /**
     * Returns the underlying map for iteration when needed
     * (e.g., simulation ticks). Callers must NOT modify the map directly —
     * use {@link #addInteraction}, {@link #updateLegBRole},
     * and {@link #removeInteraction} instead.
     */
    public Map<String, Interaction> getInteractions() {
        return interactions;
    }

    /**
     * Decrements a counter, but clamps at 0 if it would go negative.
     * A negative counter means a bug — log it as ERROR so it's visible
     * without crashing the call flow.
     */
    private void safeDecrement(AtomicInteger counter, String name) {
        int prev = counter.getAndUpdate(v -> {
            if (v <= 0) {
                return 0;
            }
            return v - 1;
        });
        if (prev <= 0) {
            log.error("Counter underflow detected for '{}' — was {} before decrement. "
                    + "This indicates a bug in interaction lifecycle management.", name, prev);
        }
    }
}
