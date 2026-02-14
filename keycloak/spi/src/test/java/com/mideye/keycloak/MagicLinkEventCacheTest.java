package com.mideye.keycloak;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MagicLinkEventCache}.
 */
class MagicLinkEventCacheTest {

    private MagicLinkEventCache cache;
    private String realmId;

    @BeforeEach
    void setUp() {
        // Use unique realm IDs per test to avoid cross-test interference
        realmId = "test-realm-" + UUID.randomUUID();
        cache = MagicLinkEventCache.getInstance(realmId);
        cache.clearEventLog();
        cache.resetStats();
    }

    // ── Instance management ────────────────────────────────────

    @Nested
    @DisplayName("Instance management")
    class InstanceManagement {

        @Test
        void sameRealmReturnsSameInstance() {
            MagicLinkEventCache c1 = MagicLinkEventCache.getInstance(realmId);
            MagicLinkEventCache c2 = MagicLinkEventCache.getInstance(realmId);
            assertSame(c1, c2);
        }

        @Test
        void differentRealmsReturnDifferentInstances() {
            MagicLinkEventCache c1 = MagicLinkEventCache.getInstance("realm-a-" + UUID.randomUUID());
            MagicLinkEventCache c2 = MagicLinkEventCache.getInstance("realm-b-" + UUID.randomUUID());
            assertNotSame(c1, c2);
        }

        @Test
        void nullRealmIdThrows() {
            assertThrows(IllegalArgumentException.class, () -> MagicLinkEventCache.getInstance(null));
        }

        @Test
        void blankRealmIdThrows() {
            assertThrows(IllegalArgumentException.class, () -> MagicLinkEventCache.getInstance("  "));
        }

        @Test
        void removeInstanceClearsCache() {
            cache.recordEvent("user1", "***1234", "success", "TOUCH_ACCEPTED",
                "127.0.0.1", 500, null);
            assertEquals(1, cache.getEventLogSize());

            MagicLinkEventCache.removeInstance(realmId);

            // New instance should be clean
            MagicLinkEventCache fresh = MagicLinkEventCache.getInstance(realmId);
            assertEquals(0, fresh.getEventLogSize());
        }
    }

    // ── Recording events ───────────────────────────────────────

    @Nested
    @DisplayName("Recording events")
    class RecordingEvents {

        @Test
        void recordsSuccessEvent() {
            cache.recordEvent("alice", "***1234", "success", "TOUCH_ACCEPTED",
                "10.0.0.1", 1500, null);

            assertEquals(1, cache.getEventLogSize());
            assertEquals(1, cache.getTotalAttempts());
            assertEquals(1, cache.getTotalSuccess());
            assertEquals(0, cache.getTotalRejected());

            List<MagicLinkEventCache.EventEntry> events = cache.getRecentEvents(10);
            assertEquals(1, events.size());
            MagicLinkEventCache.EventEntry e = events.getFirst();
            assertEquals("alice", e.getUsername());
            assertEquals("***1234", e.getPhoneNumber());
            assertEquals("success", e.getOutcome());
            assertEquals("TOUCH_ACCEPTED", e.getResponseCode());
            assertEquals("10.0.0.1", e.getIpAddress());
            assertEquals(1500, e.getDurationMs());
            assertNull(e.getErrorMessage());
            assertNotNull(e.getTimestamp());
        }

        @Test
        void recordsRejectedEvent() {
            cache.recordEvent("bob", "***5678", "rejected", "TOUCH_REJECTED",
                "10.0.0.2", 3000, null);

            assertEquals(1, cache.getTotalRejected());
        }

        @Test
        void recordsTimeoutEvent() {
            cache.recordEvent("charlie", "***9012", "timeout", null,
                "10.0.0.3", 120000, "Request timed out");

            assertEquals(1, cache.getTotalTimeout());
        }

        @Test
        void recordsErrorEvent() {
            cache.recordEvent("dave", "***3456", "error", null,
                "10.0.0.4", 50, "Connection refused");

            assertEquals(1, cache.getTotalErrors());
        }

        @Test
        void recordsNoPhoneEvent() {
            cache.recordEvent("eve", null, "no_phone", null,
                "10.0.0.5", 0, "No phone attribute");

            assertEquals(1, cache.getTotalNoPhone());
        }

        @Test
        void newestFirstOrdering() {
            cache.recordEvent("first", null, "success", null, null, 100, null);
            cache.recordEvent("second", null, "success", null, null, 200, null);
            cache.recordEvent("third", null, "success", null, null, 300, null);

            List<MagicLinkEventCache.EventEntry> events = cache.getRecentEvents(10);
            assertEquals(3, events.size());
            assertEquals("third", events.get(0).getUsername());
            assertEquals("second", events.get(1).getUsername());
            assertEquals("first", events.get(2).getUsername());
        }

        @Test
        void respectsMaxSize() {
            cache.applyConfig(100, 1);  // Minimum allowed is 100

            for (int i = 0; i < 120; i++) {
                cache.recordEvent("user" + i, null, "success", null, null, 100, null);
            }

            assertEquals(100, cache.getEventLogSize());
            assertEquals(120, cache.getTotalAttempts());

            // Newest event should be first
            List<MagicLinkEventCache.EventEntry> events = cache.getRecentEvents(10);
            assertEquals("user119", events.getFirst().getUsername());
        }
    }

    // ── Query events ───────────────────────────────────────────

    @Nested
    @DisplayName("Query events")
    class QueryEvents {

        @Test
        void getRecentEventsLimitedByParameter() {
            for (int i = 0; i < 20; i++) {
                cache.recordEvent("user" + i, null, "success", null, null, 100, null);
            }

            List<MagicLinkEventCache.EventEntry> events = cache.getRecentEvents(5);
            assertEquals(5, events.size());
        }

        @Test
        void getRecentEventsReturnsAllWhenLimitExceedsSize() {
            cache.recordEvent("user1", null, "success", null, null, 100, null);
            cache.recordEvent("user2", null, "success", null, null, 100, null);

            List<MagicLinkEventCache.EventEntry> events = cache.getRecentEvents(100);
            assertEquals(2, events.size());
        }

        @Test
        void getTopUsernamesOrderedByCount() {
            cache.recordEvent("alice", null, "success", null, null, 100, null);
            cache.recordEvent("alice", null, "success", null, null, 100, null);
            cache.recordEvent("alice", null, "rejected", null, null, 100, null);
            cache.recordEvent("bob", null, "success", null, null, 100, null);
            cache.recordEvent("charlie", null, "success", null, null, 100, null);
            cache.recordEvent("charlie", null, "success", null, null, 100, null);

            List<Map.Entry<String, Long>> top = cache.getTopUsernames(10);
            assertEquals(3, top.size());
            assertEquals("alice", top.get(0).getKey());
            assertEquals(3L, top.get(0).getValue());
            assertEquals("charlie", top.get(1).getKey());
            assertEquals(2L, top.get(1).getValue());
            assertEquals("bob", top.get(2).getKey());
            assertEquals(1L, top.get(2).getValue());
        }

        @Test
        void getTopUsernamesLimited() {
            cache.recordEvent("alice", null, "success", null, null, 100, null);
            cache.recordEvent("alice", null, "success", null, null, 100, null);
            cache.recordEvent("bob", null, "success", null, null, 100, null);
            cache.recordEvent("charlie", null, "success", null, null, 100, null);

            List<Map.Entry<String, Long>> top = cache.getTopUsernames(1);
            assertEquals(1, top.size());
            assertEquals("alice", top.getFirst().getKey());
        }
    }

    // ── Config application ─────────────────────────────────────

    @Nested
    @DisplayName("Config application")
    class ConfigApplication {

        @Test
        void applyConfigSetsValues() {
            cache.applyConfig(500, 12);
            assertEquals(500, cache.getMaxEventLogSize());
            assertEquals(12, cache.getEventTtlHours());
        }

        @Test
        void applyConfigClampsMinimum() {
            cache.applyConfig(10, 0);
            assertEquals(100, cache.getMaxEventLogSize()); // Minimum 100
            assertEquals(1, cache.getEventTtlHours());     // Math.max(1, 0) = 1
        }

        @Test
        void applyConfigClampsMaximum() {
            cache.applyConfig(100_000, 1);
            assertEquals(50_000, cache.getMaxEventLogSize()); // HARD_MAX_EVENT_LOG_SIZE
        }
    }

    // ── Statistics ──────────────────────────────────────────────

    @Nested
    @DisplayName("Statistics")
    class Statistics {

        @Test
        void statsAccumulateCorrectly() {
            cache.recordEvent("u1", null, "success", null, null, 100, null);
            cache.recordEvent("u2", null, "success", null, null, 100, null);
            cache.recordEvent("u3", null, "rejected", null, null, 100, null);
            cache.recordEvent("u4", null, "timeout", null, null, 100, null);
            cache.recordEvent("u5", null, "error", null, null, 100, null);
            cache.recordEvent("u6", null, "no_phone", null, null, 100, null);

            assertEquals(6, cache.getTotalAttempts());
            assertEquals(2, cache.getTotalSuccess());
            assertEquals(1, cache.getTotalRejected());
            assertEquals(1, cache.getTotalTimeout());
            assertEquals(1, cache.getTotalErrors());
            assertEquals(1, cache.getTotalNoPhone());
        }

        @Test
        void resetStatsClearsCounters() {
            cache.recordEvent("u1", null, "success", null, null, 100, null);
            cache.recordEvent("u2", null, "error", null, null, 100, null);

            cache.resetStats();

            assertEquals(0, cache.getTotalAttempts());
            assertEquals(0, cache.getTotalSuccess());
            assertEquals(0, cache.getTotalErrors());
        }

        @Test
        void clearEventLogClearsEvents() {
            cache.recordEvent("u1", null, "success", null, null, 100, null);
            assertEquals(1, cache.getEventLogSize());

            cache.clearEventLog();
            assertEquals(0, cache.getEventLogSize());
        }
    }

    // ── Pruning ────────────────────────────────────────────────

    @Nested
    @DisplayName("Event pruning")
    class Pruning {

        @Test
        void pruneRemovesNothing_whenAllFresh() {
            cache.applyConfig(1000, 1);
            cache.recordEvent("u1", null, "success", null, null, 100, null);
            cache.recordEvent("u2", null, "success", null, null, 100, null);

            int pruned = cache.pruneOldEvents();
            assertEquals(0, pruned);
            assertEquals(2, cache.getEventLogSize());
        }
    }

    // ── EventEntry serialization ───────────────────────────────

    @Nested
    @DisplayName("EventEntry serialization")
    class EventEntryJson {

        @Test
        void toJsonContainsAllFields() {
            MagicLinkEventCache.EventEntry e = new MagicLinkEventCache.EventEntry(
                "alice", "***1234", "success", "TOUCH_ACCEPTED",
                "10.0.0.1", Instant.parse("2024-01-15T10:30:00Z"), 1500, null);

            String json = e.toJson();
            assertTrue(json.contains("\"username\":\"alice\""));
            assertTrue(json.contains("\"phoneNumber\":\"***1234\""));
            assertTrue(json.contains("\"outcome\":\"success\""));
            assertTrue(json.contains("\"responseCode\":\"TOUCH_ACCEPTED\""));
            assertTrue(json.contains("\"ipAddress\":\"10.0.0.1\""));
            assertTrue(json.contains("\"durationMs\":1500"));
            assertTrue(json.contains("\"timestamp\":\"2024-01-15T10:30:00Z\""));
        }

        @Test
        void toJsonHandlesNulls() {
            MagicLinkEventCache.EventEntry e = new MagicLinkEventCache.EventEntry(
                null, null, "error", null, null, Instant.now(), 0, "some error");

            String json = e.toJson();
            assertTrue(json.contains("\"username\":\"\""));
            assertTrue(json.contains("\"phoneNumber\":\"\""));
            assertTrue(json.contains("\"errorMessage\":\"some error\""));
        }

        @Test
        void toJsonEscapesSpecialCharacters() {
            MagicLinkEventCache.EventEntry e = new MagicLinkEventCache.EventEntry(
                "user\"name", null, "error", null, null, Instant.now(), 0,
                "error with \"quotes\" and \nnewline");

            String json = e.toJson();
            assertTrue(json.contains("user\\\"name"));
            assertTrue(json.contains("\\n"));
        }
    }
}
