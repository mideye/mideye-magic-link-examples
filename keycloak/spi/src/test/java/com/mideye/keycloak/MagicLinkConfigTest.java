package com.mideye.keycloak;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MagicLinkConfig}.
 */
class MagicLinkConfigTest {

    // ── Defaults ───────────────────────────────────────────────

    @Nested
    @DisplayName("Default config")
    class Defaults {

        @Test
        void defaultConstructorUsesDefaults() {
            MagicLinkConfig c = new MagicLinkConfig();
            assertEquals(MagicLinkConfig.DEFAULT_PHONE_ATTRIBUTE, c.getPhoneAttribute());
            assertEquals(MagicLinkConfig.DEFAULT_TIMEOUT_SECONDS, c.getTimeoutSeconds());
            assertFalse(c.isSkipTlsVerify());
            assertEquals(MagicLinkConfig.DEFAULT_EVENT_LOG_MAX_SIZE, c.getEventLogMaxSize());
            assertEquals(MagicLinkConfig.DEFAULT_EVENT_TTL_HOURS, c.getEventTtlHours());
            assertEquals(MagicLinkConfig.DEFAULT_DASHBOARD_ROLE, c.getDashboardRole());
        }

        @Test
        void fromNullMapReturnsDefaults() {
            MagicLinkConfig c = MagicLinkConfig.fromMap(null);
            assertEquals(MagicLinkConfig.DEFAULT_PHONE_ATTRIBUTE, c.getPhoneAttribute());
            assertEquals(MagicLinkConfig.DEFAULT_TIMEOUT_SECONDS, c.getTimeoutSeconds());
        }

        @Test
        void fromEmptyMapReturnsDefaults() {
            MagicLinkConfig c = MagicLinkConfig.fromMap(new HashMap<>());
            assertEquals(MagicLinkConfig.DEFAULT_PHONE_ATTRIBUTE, c.getPhoneAttribute());
            assertEquals(MagicLinkConfig.DEFAULT_TIMEOUT_SECONDS, c.getTimeoutSeconds());
        }
    }

    // ── fromMap parsing ────────────────────────────────────────

    @Nested
    @DisplayName("fromMap parsing")
    class FromMap {

        @Test
        void parsesAllFields() {
            Map<String, String> map = new HashMap<>();
            map.put(MagicLinkConfig.KEY_MIDEYE_URL, "https://mideye.example.com");
            map.put(MagicLinkConfig.KEY_API_KEY, "secret-key-123");
            map.put(MagicLinkConfig.KEY_PHONE_ATTRIBUTE, "mobile");
            map.put(MagicLinkConfig.KEY_TIMEOUT_SECONDS, "60");
            map.put(MagicLinkConfig.KEY_SKIP_TLS_VERIFY, "true");
            map.put(MagicLinkConfig.KEY_EVENT_LOG_MAX_SIZE, "500");
            map.put(MagicLinkConfig.KEY_EVENT_TTL_HOURS, "24");
            map.put(MagicLinkConfig.KEY_DASHBOARD_ROLE, "my-custom-role");

            MagicLinkConfig c = MagicLinkConfig.fromMap(map);

            assertEquals("https://mideye.example.com", c.getMideyeUrl());
            assertEquals("secret-key-123", c.getApiKey());
            assertEquals("mobile", c.getPhoneAttribute());
            assertEquals(60, c.getTimeoutSeconds());
            assertTrue(c.isSkipTlsVerify());
            assertEquals(500, c.getEventLogMaxSize());
            assertEquals(24, c.getEventTtlHours());
            assertEquals("my-custom-role", c.getDashboardRole());
        }

        @Test
        void invalidIntegersFallBackToDefaults() {
            Map<String, String> map = new HashMap<>();
            map.put(MagicLinkConfig.KEY_TIMEOUT_SECONDS, "not-a-number");
            map.put(MagicLinkConfig.KEY_EVENT_LOG_MAX_SIZE, "abc");
            map.put(MagicLinkConfig.KEY_EVENT_TTL_HOURS, "");

            MagicLinkConfig c = MagicLinkConfig.fromMap(map);

            assertEquals(MagicLinkConfig.DEFAULT_TIMEOUT_SECONDS, c.getTimeoutSeconds());
            assertEquals(MagicLinkConfig.DEFAULT_EVENT_LOG_MAX_SIZE, c.getEventLogMaxSize());
            assertEquals(MagicLinkConfig.DEFAULT_EVENT_TTL_HOURS, c.getEventTtlHours());
        }

        @Test
        void blankValuesFallBackToDefaults() {
            Map<String, String> map = new HashMap<>();
            map.put(MagicLinkConfig.KEY_PHONE_ATTRIBUTE, "   ");
            map.put(MagicLinkConfig.KEY_DASHBOARD_ROLE, "");

            MagicLinkConfig c = MagicLinkConfig.fromMap(map);

            assertEquals(MagicLinkConfig.DEFAULT_PHONE_ATTRIBUTE, c.getPhoneAttribute());
            assertEquals(MagicLinkConfig.DEFAULT_DASHBOARD_ROLE, c.getDashboardRole());
        }

        @Test
        void skipTlsVerifyFalseVariants() {
            Map<String, String> map = new HashMap<>();
            map.put(MagicLinkConfig.KEY_SKIP_TLS_VERIFY, "false");
            assertFalse(MagicLinkConfig.fromMap(map).isSkipTlsVerify());

            map.put(MagicLinkConfig.KEY_SKIP_TLS_VERIFY, "");
            assertFalse(MagicLinkConfig.fromMap(map).isSkipTlsVerify());

            map.put(MagicLinkConfig.KEY_SKIP_TLS_VERIFY, "yes");
            assertFalse(MagicLinkConfig.fromMap(map).isSkipTlsVerify());
        }

        @Test
        void skipTlsVerifyTrueCaseInsensitive() {
            Map<String, String> map = new HashMap<>();
            map.put(MagicLinkConfig.KEY_SKIP_TLS_VERIFY, "TRUE");
            assertTrue(MagicLinkConfig.fromMap(map).isSkipTlsVerify());

            map.put(MagicLinkConfig.KEY_SKIP_TLS_VERIFY, "True");
            assertTrue(MagicLinkConfig.fromMap(map).isSkipTlsVerify());
        }

        @Test
        void trimsWhitespace() {
            Map<String, String> map = new HashMap<>();
            map.put(MagicLinkConfig.KEY_MIDEYE_URL, "  https://example.com  ");
            map.put(MagicLinkConfig.KEY_TIMEOUT_SECONDS, "  30  ");

            MagicLinkConfig c = MagicLinkConfig.fromMap(map);
            assertEquals("https://example.com", c.getMideyeUrl());
            assertEquals(30, c.getTimeoutSeconds());
        }
    }

    // ── isConfigured ───────────────────────────────────────────

    @Nested
    @DisplayName("isConfigured")
    class IsConfigured {

        @Test
        void configuredWhenUrlAndApiKeySet() {
            Map<String, String> map = new HashMap<>();
            map.put(MagicLinkConfig.KEY_MIDEYE_URL, "https://mideye.example.com");
            map.put(MagicLinkConfig.KEY_API_KEY, "key123");
            assertTrue(MagicLinkConfig.fromMap(map).isConfigured());
        }

        @Test
        void notConfiguredWhenUrlMissing() {
            Map<String, String> map = new HashMap<>();
            map.put(MagicLinkConfig.KEY_API_KEY, "key123");
            assertFalse(MagicLinkConfig.fromMap(map).isConfigured());
        }

        @Test
        void notConfiguredWhenApiKeyMissing() {
            Map<String, String> map = new HashMap<>();
            map.put(MagicLinkConfig.KEY_MIDEYE_URL, "https://mideye.example.com");
            assertFalse(MagicLinkConfig.fromMap(map).isConfigured());
        }

        @Test
        void notConfiguredWhenBothBlank() {
            Map<String, String> map = new HashMap<>();
            map.put(MagicLinkConfig.KEY_MIDEYE_URL, "");
            map.put(MagicLinkConfig.KEY_API_KEY, "  ");
            assertFalse(MagicLinkConfig.fromMap(map).isConfigured());
        }
    }

    // ── toJson ─────────────────────────────────────────────────

    @Nested
    @DisplayName("toJson")
    class ToJson {

        @Test
        void containsAllFields() {
            Map<String, String> map = new HashMap<>();
            map.put(MagicLinkConfig.KEY_MIDEYE_URL, "https://example.com");
            map.put(MagicLinkConfig.KEY_API_KEY, "secret");
            MagicLinkConfig c = MagicLinkConfig.fromMap(map);

            String json = c.toJson();
            assertTrue(json.contains("\"mideyeUrl\":\"https://example.com\""));
            assertTrue(json.contains("\"phoneAttribute\":\"phoneNumber\""));
            assertTrue(json.contains("\"timeoutSeconds\":120"));
            assertTrue(json.contains("\"configured\":true"));
        }

        @Test
        void apiKeyNotExposed() {
            Map<String, String> map = new HashMap<>();
            map.put(MagicLinkConfig.KEY_API_KEY, "super-secret-key");
            MagicLinkConfig c = MagicLinkConfig.fromMap(map);
            String json = c.toJson();
            assertFalse(json.contains("super-secret-key"), "API key must not appear in toJson");
        }

        @Test
        void escapesSpecialCharacters() {
            Map<String, String> map = new HashMap<>();
            map.put(MagicLinkConfig.KEY_PHONE_ATTRIBUTE, "phone\"attr");
            MagicLinkConfig c = MagicLinkConfig.fromMap(map);
            String json = c.toJson();
            assertTrue(json.contains("phone\\\"attr"));
        }
    }

    // ── Parsing helpers ────────────────────────────────────────

    @Nested
    @DisplayName("Parsing helpers")
    class ParsingHelpers {

        @Test
        void parseIntHandlesVariousInputs() {
            assertEquals(42, MagicLinkConfig.parseInt("42", 0));
            assertEquals(0, MagicLinkConfig.parseInt(null, 0));
            assertEquals(0, MagicLinkConfig.parseInt("", 0));
            assertEquals(99, MagicLinkConfig.parseInt("abc", 99));
            assertEquals(10, MagicLinkConfig.parseInt("  10  ", 0));
        }

        @Test
        void parseBoolHandlesVariousInputs() {
            assertTrue(MagicLinkConfig.parseBool("true", false));
            assertTrue(MagicLinkConfig.parseBool("TRUE", false));
            assertFalse(MagicLinkConfig.parseBool("false", true));
            assertFalse(MagicLinkConfig.parseBool("yes", false));
            assertFalse(MagicLinkConfig.parseBool(null, false));
            assertTrue(MagicLinkConfig.parseBool(null, true));
        }

        @Test
        void parseStringHandlesVariousInputs() {
            assertEquals("hello", MagicLinkConfig.parseString("hello", "default"));
            assertEquals("default", MagicLinkConfig.parseString(null, "default"));
            assertEquals("default", MagicLinkConfig.parseString("  ", "default"));
            assertEquals("trimmed", MagicLinkConfig.parseString("  trimmed  ", "default"));
        }

        @Test
        void escapeJsonHandlesSpecialChars() {
            assertEquals("", MagicLinkConfig.escapeJson(null));
            assertEquals("hello", MagicLinkConfig.escapeJson("hello"));
            assertEquals("he\\\"llo", MagicLinkConfig.escapeJson("he\"llo"));
            assertEquals("line\\none", MagicLinkConfig.escapeJson("line\none"));
            assertEquals("back\\\\slash", MagicLinkConfig.escapeJson("back\\slash"));
        }
    }
}
