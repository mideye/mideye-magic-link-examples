package com.mideye.keycloak;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for static utility methods in {@link MideyeMagicLinkAuthenticator}.
 */
class MideyeMagicLinkAuthenticatorTest {

    // ── parseResponseCode ──────────────────────────────────────

    @Nested
    @DisplayName("parseResponseCode")
    class ParseResponseCode {

        @Test
        void parsesAccepted() {
            assertEquals("TOUCH_ACCEPTED",
                MideyeMagicLinkAuthenticator.parseResponseCode("{\"code\":\"TOUCH_ACCEPTED\"}"));
        }

        @Test
        void parsesRejected() {
            assertEquals("TOUCH_REJECTED",
                MideyeMagicLinkAuthenticator.parseResponseCode("{\"code\":\"TOUCH_REJECTED\"}"));
        }

        @Test
        void parsesTimeout() {
            assertEquals("TOUCH_TIMEOUT",
                MideyeMagicLinkAuthenticator.parseResponseCode("{\"code\":\"TOUCH_TIMEOUT\"}"));
        }

        @Test
        void parsesWithExtraFields() {
            assertEquals("TOUCH_ACCEPTED",
                MideyeMagicLinkAuthenticator.parseResponseCode(
                    "{\"code\":\"TOUCH_ACCEPTED\",\"message\":\"ok\"}"));
        }

        @Test
        void returnsUnknownForNull() {
            assertEquals("UNKNOWN", MideyeMagicLinkAuthenticator.parseResponseCode(null));
        }

        @Test
        void returnsUnknownForBlank() {
            assertEquals("UNKNOWN", MideyeMagicLinkAuthenticator.parseResponseCode(""));
            assertEquals("UNKNOWN", MideyeMagicLinkAuthenticator.parseResponseCode("  "));
        }

        @Test
        void returnsUnknownForMalformedJson() {
            assertEquals("UNKNOWN", MideyeMagicLinkAuthenticator.parseResponseCode("{\"status\":\"ok\"}"));
            assertEquals("UNKNOWN", MideyeMagicLinkAuthenticator.parseResponseCode("not json"));
        }

        @Test
        void returnsUnknownForIncompleteJson() {
            assertEquals("UNKNOWN", MideyeMagicLinkAuthenticator.parseResponseCode("{\"code\":\""));
        }
    }

    // ── maskPhoneNumber ────────────────────────────────────────

    @Nested
    @DisplayName("maskPhoneNumber")
    class MaskPhoneNumber {

        @Test
        void masksNormalPhoneNumber() {
            assertEquals("***4567", MideyeMagicLinkAuthenticator.maskPhoneNumber("+46701234567"));
        }

        @Test
        void masksShortNumber() {
            assertEquals("***6789", MideyeMagicLinkAuthenticator.maskPhoneNumber("56789"));
        }

        @Test
        void masksFourDigitNumber() {
            assertEquals("****", MideyeMagicLinkAuthenticator.maskPhoneNumber("1234"));
        }

        @Test
        void masksThreeDigitNumber() {
            assertEquals("****", MideyeMagicLinkAuthenticator.maskPhoneNumber("123"));
        }

        @Test
        void masksNull() {
            assertEquals("****", MideyeMagicLinkAuthenticator.maskPhoneNumber(null));
        }

        @Test
        void masksEmpty() {
            assertEquals("****", MideyeMagicLinkAuthenticator.maskPhoneNumber(""));
        }
    }
}
