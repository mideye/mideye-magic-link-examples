package com.mideye.keycloak;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CSV export methods in {@link MagicLinkDashboardResource}.
 */
class MagicLinkDashboardResourceTest {

    // ── buildEventsCsv ─────────────────────────────────────────

    @Nested
    @DisplayName("buildEventsCsv")
    class BuildEventsCsv {

        @Test
        void emptyListProducesHeaderOnly() {
            String csv = MagicLinkDashboardResource.buildEventsCsv(List.of());
            assertEquals("Timestamp,Username,Phone Number,Outcome,Response Code,IP Address,Duration (ms),Error\n", csv);
        }

        @Test
        void singleEventProducesHeaderAndOneRow() {
            MagicLinkEventCache.EventEntry e = new MagicLinkEventCache.EventEntry(
                "alice", "***1234", "success", "TOUCH_ACCEPTED",
                "10.0.0.1", Instant.parse("2024-01-15T10:30:00Z"), 1500, null);

            String csv = MagicLinkDashboardResource.buildEventsCsv(List.of(e));
            String[] lines = csv.split("\n");
            assertEquals(2, lines.length);
            assertTrue(lines[1].contains("alice"));
            assertTrue(lines[1].contains("***1234"));
            assertTrue(lines[1].contains("success"));
            assertTrue(lines[1].contains("TOUCH_ACCEPTED"));
            assertTrue(lines[1].contains("1500"));
        }

        @Test
        void multipleEventsProduceMultipleRows() {
            MagicLinkEventCache.EventEntry e1 = new MagicLinkEventCache.EventEntry(
                "alice", "***1234", "success", "TOUCH_ACCEPTED",
                "10.0.0.1", Instant.now(), 1500, null);
            MagicLinkEventCache.EventEntry e2 = new MagicLinkEventCache.EventEntry(
                "bob", "***5678", "rejected", "TOUCH_REJECTED",
                "10.0.0.2", Instant.now(), 3000, null);

            String csv = MagicLinkDashboardResource.buildEventsCsv(List.of(e1, e2));
            String[] lines = csv.split("\n");
            assertEquals(3, lines.length); // header + 2 data rows
        }

        @Test
        void handlesNullFields() {
            MagicLinkEventCache.EventEntry e = new MagicLinkEventCache.EventEntry(
                null, null, "error", null, null, Instant.now(), 0, "Connection refused");

            String csv = MagicLinkDashboardResource.buildEventsCsv(List.of(e));
            String[] lines = csv.split("\n");
            assertEquals(2, lines.length);
            assertTrue(lines[1].contains("error"));
            assertTrue(lines[1].contains("Connection refused"));
        }

        @Test
        void escapesCommasInFields() {
            MagicLinkEventCache.EventEntry e = new MagicLinkEventCache.EventEntry(
                "user,name", "***1234", "error", null,
                "10.0.0.1", Instant.now(), 0, "error, with comma");

            String csv = MagicLinkDashboardResource.buildEventsCsv(List.of(e));
            assertTrue(csv.contains("\"user,name\""));
            assertTrue(csv.contains("\"error, with comma\""));
        }

        @Test
        void escapesQuotesInFields() {
            MagicLinkEventCache.EventEntry e = new MagicLinkEventCache.EventEntry(
                "user\"name", null, "error", null,
                null, Instant.now(), 0, "error with \"quotes\"");

            String csv = MagicLinkDashboardResource.buildEventsCsv(List.of(e));
            assertTrue(csv.contains("\"user\"\"name\""));
            assertTrue(csv.contains("\"error with \"\"quotes\"\"\""));
        }
    }

    // ── escapeCsv ──────────────────────────────────────────────

    @Nested
    @DisplayName("escapeCsv")
    class EscapeCsv {

        @Test
        void nullReturnsEmpty() {
            assertEquals("", MagicLinkDashboardResource.escapeCsv(null));
        }

        @Test
        void simpleStringUnchanged() {
            assertEquals("hello", MagicLinkDashboardResource.escapeCsv("hello"));
        }

        @Test
        void commaWrapsInQuotes() {
            assertEquals("\"hello,world\"", MagicLinkDashboardResource.escapeCsv("hello,world"));
        }

        @Test
        void quoteEscapedAndWrapped() {
            assertEquals("\"say \"\"hi\"\"\"", MagicLinkDashboardResource.escapeCsv("say \"hi\""));
        }

        @Test
        void newlineWrapsInQuotes() {
            assertEquals("\"line1\nline2\"", MagicLinkDashboardResource.escapeCsv("line1\nline2"));
        }
    }
}
