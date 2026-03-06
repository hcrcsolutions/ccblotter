package com.fmr.ec3.oscc.sipserver;

import com.fmr.ec3.oscc.common.payload.infra.SessionBreakdownDto;
import com.fmr.ec3.oscc.sipserver.model.DialogRole;
import com.fmr.ec3.oscc.sipserver.model.Direction;
import com.fmr.ec3.oscc.sipserver.model.Interaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class InteractionStoreTest {

    private InteractionStore store;

    @BeforeEach
    void setUp() {
        store = new InteractionStore();
    }

    // ---- Invariant helper ----

    private void assertInvariants() {
        int total = store.getActiveSessions();
        SessionBreakdownDto bd = store.getSessionBreakdown();

        assertEquals(total, bd.inboundSessions() + bd.outboundSessions(),
                "total must equal inbound + outbound");
        assertEquals(total,
                bd.ivrSessions() + bd.queueSessions()
                        + bd.agentSessions() + bd.onHoldSessions(),
                "total must equal sum of leg B counts");
        assertTrue(total >= 0, "total must not be negative");
        assertTrue(bd.inboundSessions() >= 0, "inbound must not be negative");
        assertTrue(bd.outboundSessions() >= 0, "outbound must not be negative");
        assertTrue(bd.ivrSessions() >= 0, "ivr must not be negative");
        assertTrue(bd.queueSessions() >= 0, "queue must not be negative");
        assertTrue(bd.agentSessions() >= 0, "agent must not be negative");
        assertTrue(bd.onHoldSessions() >= 0, "onHold must not be negative");
    }

    @Nested
    class AddInteraction {

        @Test
        void incrementsCountersCorrectly() {
            store.addInteraction("call-1", Direction.INBOUND, DialogRole.IVR);

            assertEquals(1, store.getActiveSessions());
            SessionBreakdownDto bd = store.getSessionBreakdown();
            assertEquals(1, bd.inboundSessions());
            assertEquals(0, bd.outboundSessions());
            assertEquals(1, bd.ivrSessions());
            assertEquals(0, bd.queueSessions());
            assertEquals(0, bd.agentSessions());
            assertEquals(0, bd.onHoldSessions());
            assertInvariants();
        }

        @Test
        void returnsCreatedInteraction() {
            Interaction interaction = store.addInteraction(
                    "call-1", Direction.OUTBOUND, DialogRole.AGENT);

            assertNotNull(interaction);
            assertEquals("call-1", interaction.getId());
            assertEquals(Direction.OUTBOUND, interaction.getDirection());
            assertEquals(DialogRole.AGENT, interaction.getActiveLegBRole());
        }

        @Test
        void multipleInteractionsAccumulateCorrectly() {
            store.addInteraction("call-1", Direction.INBOUND, DialogRole.IVR);
            store.addInteraction("call-2", Direction.INBOUND, DialogRole.QUEUE);
            store.addInteraction("call-3", Direction.OUTBOUND, DialogRole.AGENT);

            assertEquals(3, store.getActiveSessions());
            SessionBreakdownDto bd = store.getSessionBreakdown();
            assertEquals(2, bd.inboundSessions());
            assertEquals(1, bd.outboundSessions());
            assertEquals(1, bd.ivrSessions());
            assertEquals(1, bd.queueSessions());
            assertEquals(1, bd.agentSessions());
            assertInvariants();
        }

        @Test
        void duplicateIdThrowsIllegalState() {
            store.addInteraction("call-1", Direction.INBOUND, DialogRole.QUEUE);

            assertThrows(IllegalStateException.class,
                    () -> store.addInteraction("call-1", Direction.INBOUND, DialogRole.IVR));

            // Counters must not have changed from the failed add
            assertEquals(1, store.getActiveSessions());
            assertInvariants();
        }

        @Test
        void nonLegBRoleThrowsIllegalArgument() {
            assertThrows(IllegalArgumentException.class,
                    () -> store.addInteraction("call-1", Direction.INBOUND, DialogRole.CALLER));
            assertThrows(IllegalArgumentException.class,
                    () -> store.addInteraction("call-2", Direction.INBOUND, DialogRole.RECORDING));

            assertEquals(0, store.getActiveSessions());
            assertInvariants();
        }
    }

    @Nested
    class UpdateLegBRole {

        @Test
        void transitionsCountersCorrectly() {
            store.addInteraction("call-1", Direction.INBOUND, DialogRole.IVR);

            store.updateLegBRole("call-1", DialogRole.QUEUE);

            SessionBreakdownDto bd = store.getSessionBreakdown();
            assertEquals(0, bd.ivrSessions());
            assertEquals(1, bd.queueSessions());
            assertEquals(1, store.getActiveSessions());
            assertInvariants();
        }

        @Test
        void fullLifecycleTransitions() {
            store.addInteraction("call-1", Direction.INBOUND, DialogRole.IVR);
            assertInvariants();

            // IVR -> QUEUE
            store.updateLegBRole("call-1", DialogRole.QUEUE);
            assertEquals(1, store.getSessionBreakdown().queueSessions());
            assertInvariants();

            // QUEUE -> AGENT
            store.updateLegBRole("call-1", DialogRole.AGENT);
            assertEquals(1, store.getSessionBreakdown().agentSessions());
            assertEquals(0, store.getSessionBreakdown().queueSessions());
            assertInvariants();

            // AGENT -> ON_HOLD
            store.updateLegBRole("call-1", DialogRole.ON_HOLD);
            assertEquals(1, store.getSessionBreakdown().onHoldSessions());
            assertEquals(0, store.getSessionBreakdown().agentSessions());
            assertInvariants();

            // ON_HOLD -> AGENT (resume)
            store.updateLegBRole("call-1", DialogRole.AGENT);
            assertEquals(1, store.getSessionBreakdown().agentSessions());
            assertEquals(0, store.getSessionBreakdown().onHoldSessions());
            assertInvariants();
        }

        @Test
        void sameRoleIsNoOp() {
            store.addInteraction("call-1", Direction.INBOUND, DialogRole.AGENT);

            store.updateLegBRole("call-1", DialogRole.AGENT);

            assertEquals(1, store.getSessionBreakdown().agentSessions());
            assertInvariants();
        }

        @Test
        void unknownInteractionIsNoOp() {
            // Should not throw, just warn
            store.updateLegBRole("nonexistent", DialogRole.AGENT);

            assertEquals(0, store.getActiveSessions());
            assertInvariants();
        }

        @Test
        void nonLegBRoleThrowsIllegalArgument() {
            store.addInteraction("call-1", Direction.INBOUND, DialogRole.QUEUE);

            assertThrows(IllegalArgumentException.class,
                    () -> store.updateLegBRole("call-1", DialogRole.CALLER));
            assertThrows(IllegalArgumentException.class,
                    () -> store.updateLegBRole("call-1", DialogRole.RECORDING));

            // Original state unchanged
            assertEquals(1, store.getSessionBreakdown().queueSessions());
            assertInvariants();
        }

        @Test
        void directionUnchangedAfterLegBUpdate() {
            store.addInteraction("call-1", Direction.OUTBOUND, DialogRole.QUEUE);

            store.updateLegBRole("call-1", DialogRole.AGENT);

            SessionBreakdownDto bd = store.getSessionBreakdown();
            assertEquals(0, bd.inboundSessions());
            assertEquals(1, bd.outboundSessions());
            assertInvariants();
        }
    }

    @Nested
    class RemoveInteraction {

        @Test
        void decrementsAllCounters() {
            store.addInteraction("call-1", Direction.INBOUND, DialogRole.AGENT);

            Interaction removed = store.removeInteraction("call-1");

            assertNotNull(removed);
            assertEquals("call-1", removed.getId());
            assertEquals(0, store.getActiveSessions());
            SessionBreakdownDto bd = store.getSessionBreakdown();
            assertEquals(0, bd.inboundSessions());
            assertEquals(0, bd.agentSessions());
            assertInvariants();
        }

        @Test
        void removesFromMap() {
            store.addInteraction("call-1", Direction.INBOUND, DialogRole.QUEUE);

            store.removeInteraction("call-1");

            assertNull(store.get("call-1"));
        }

        @Test
        void unknownInteractionReturnsNull() {
            Interaction result = store.removeInteraction("nonexistent");

            assertNull(result);
            assertEquals(0, store.getActiveSessions());
            assertInvariants();
        }

        @Test
        void removeAfterLegBTransitionDecrementsCorrectCounter() {
            store.addInteraction("call-1", Direction.INBOUND, DialogRole.IVR);
            store.updateLegBRole("call-1", DialogRole.AGENT);

            store.removeInteraction("call-1");

            assertEquals(0, store.getActiveSessions());
            SessionBreakdownDto bd = store.getSessionBreakdown();
            assertEquals(0, bd.ivrSessions());
            assertEquals(0, bd.agentSessions());
            assertInvariants();
        }

        @Test
        void doubleRemoveIsIdempotent() {
            store.addInteraction("call-1", Direction.INBOUND, DialogRole.QUEUE);

            store.removeInteraction("call-1");
            Interaction secondRemove = store.removeInteraction("call-1");

            assertNull(secondRemove);
            assertEquals(0, store.getActiveSessions());
            assertInvariants();
        }
    }

    @Nested
    class AddAndRemoveMany {

        @Test
        void addAndRemoveAllReturnsToZero() {
            for (int i = 0; i < 100; i++) {
                Direction dir = i % 3 == 0 ? Direction.OUTBOUND : Direction.INBOUND;
                DialogRole role = DialogRole.values()[1 + (i % 4)]; // IVR, QUEUE, AGENT, ON_HOLD
                store.addInteraction("call-" + i, dir, role);
            }

            assertEquals(100, store.getActiveSessions());
            assertInvariants();

            for (int i = 0; i < 100; i++) {
                store.removeInteraction("call-" + i);
            }

            assertEquals(0, store.getActiveSessions());
            SessionBreakdownDto bd = store.getSessionBreakdown();
            assertEquals(0, bd.inboundSessions());
            assertEquals(0, bd.outboundSessions());
            assertEquals(0, bd.ivrSessions());
            assertEquals(0, bd.queueSessions());
            assertEquals(0, bd.agentSessions());
            assertEquals(0, bd.onHoldSessions());
            assertInvariants();
        }
    }

    @Nested
    class ConcurrencyTests {

        @Test
        void concurrentAddsAndRemovesPreserveInvariants() throws InterruptedException {
            int threadCount = 8;
            int opsPerThread = 500;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger errors = new AtomicInteger();

            for (int t = 0; t < threadCount; t++) {
                int threadId = t;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < opsPerThread; i++) {
                            String id = "t" + threadId + "-" + i;
                            DialogRole role = DialogRole.values()[1 + (i % 4)];
                            Direction dir = i % 2 == 0
                                    ? Direction.INBOUND : Direction.OUTBOUND;
                            store.addInteraction(id, dir, role);
                        }
                        for (int i = 0; i < opsPerThread; i++) {
                            String id = "t" + threadId + "-" + i;
                            store.removeInteraction(id);
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
            executor.shutdown();

            assertEquals(0, errors.get(), "no exceptions during concurrent ops");
            assertEquals(0, store.getActiveSessions());
            assertInvariants();
        }

        @Test
        void concurrentUpdatesPreserveInvariants() throws InterruptedException {
            int interactionCount = 200;
            for (int i = 0; i < interactionCount; i++) {
                Direction dir = i % 2 == 0 ? Direction.INBOUND : Direction.OUTBOUND;
                store.addInteraction("call-" + i, dir, DialogRole.QUEUE);
            }

            int threadCount = 8;
            int transitionsPerThread = 500;
            DialogRole[] legBRoles = {
                DialogRole.IVR, DialogRole.QUEUE,
                DialogRole.AGENT, DialogRole.ON_HOLD
            };
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger errors = new AtomicInteger();

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        java.util.Random rng = new java.util.Random();
                        for (int i = 0; i < transitionsPerThread; i++) {
                            String id = "call-" + rng.nextInt(interactionCount);
                            DialogRole newRole = legBRoles[rng.nextInt(legBRoles.length)];
                            store.updateLegBRole(id, newRole);
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
            executor.shutdown();

            assertEquals(0, errors.get(), "no exceptions during concurrent updates");
            assertEquals(interactionCount, store.getActiveSessions());
            assertInvariants();
        }

        @Test
        void concurrentMixedOpsPreserveInvariants() throws InterruptedException {
            int threadCount = 8;
            int opsPerThread = 500;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
            DialogRole[] legBRoles = {
                DialogRole.IVR, DialogRole.QUEUE,
                DialogRole.AGENT, DialogRole.ON_HOLD
            };

            for (int t = 0; t < threadCount; t++) {
                int threadId = t;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        java.util.Random rng = new java.util.Random();
                        for (int i = 0; i < opsPerThread; i++) {
                            String id = "t" + threadId + "-" + i;
                            Direction dir = rng.nextBoolean()
                                    ? Direction.INBOUND : Direction.OUTBOUND;
                            DialogRole role = legBRoles[rng.nextInt(legBRoles.length)];

                            store.addInteraction(id, dir, role);

                            // Random transitions
                            int transitions = rng.nextInt(5);
                            for (int j = 0; j < transitions; j++) {
                                store.updateLegBRole(
                                        id, legBRoles[rng.nextInt(legBRoles.length)]);
                            }

                            store.removeInteraction(id);
                        }
                    } catch (Exception e) {
                        errors.add(e);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
            executor.shutdown();

            assertTrue(errors.isEmpty(),
                    "no exceptions: " + (errors.isEmpty() ? "" : errors.get(0)));
            assertEquals(0, store.getActiveSessions());
            assertInvariants();
        }
    }

    @Nested
    class GetInteraction {

        @Test
        void returnsNullForUnknown() {
            assertNull(store.get("nonexistent"));
        }

        @Test
        void returnsExistingInteraction() {
            store.addInteraction("call-1", Direction.INBOUND, DialogRole.AGENT);

            Interaction interaction = store.get("call-1");

            assertNotNull(interaction);
            assertEquals("call-1", interaction.getId());
        }

        @Test
        void returnsNullAfterRemoval() {
            store.addInteraction("call-1", Direction.INBOUND, DialogRole.QUEUE);
            store.removeInteraction("call-1");

            assertNull(store.get("call-1"));
        }
    }

    @Nested
    class EmptyStore {

        @Test
        void freshStoreHasAllZeros() {
            assertEquals(0, store.getActiveSessions());
            SessionBreakdownDto bd = store.getSessionBreakdown();
            assertEquals(0, bd.inboundSessions());
            assertEquals(0, bd.outboundSessions());
            assertEquals(0, bd.ivrSessions());
            assertEquals(0, bd.queueSessions());
            assertEquals(0, bd.agentSessions());
            assertEquals(0, bd.onHoldSessions());
            assertInvariants();
        }
    }
}
