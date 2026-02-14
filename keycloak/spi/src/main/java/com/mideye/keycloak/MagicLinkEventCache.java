package com.mideye.keycloak;

import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-realm in-memory cache of recent Magic Link authentication events.
 *
 * <p>Stores the latest authentication attempts (success/failure) as a ring
 * buffer, pruned by configurable max size and TTL. Each realm has its own
 * isolated cache instance.</p>
 *
 * <p>Thread-safe: all event log operations are synchronized on an internal lock.</p>
 */
public class MagicLinkEventCache {

    private static final Logger LOG = Logger.getLogger(MagicLinkEventCache.class);

    /** Per-realm cache instances keyed by realm ID. */
    private static final ConcurrentHashMap<String, MagicLinkEventCache> REALM_CACHES = new ConcurrentHashMap<>();

    /** Absolute upper bound to protect Keycloak heap. */
    private static final int HARD_MAX_EVENT_LOG_SIZE = 50_000;

    /**
     * Get the cache instance for a specific realm.
     */
    public static MagicLinkEventCache getInstance(String realmId) {
        if (realmId == null || realmId.isBlank()) {
            throw new IllegalArgumentException("Realm ID must not be null or blank");
        }
        return REALM_CACHES.computeIfAbsent(realmId, id -> {
            LOG.infof("MagicLink: Creating new per-realm event cache for realm [%s]", id);
            return new MagicLinkEventCache();
        });
    }

    /**
     * @deprecated Use {@link #getInstance(String)} with a realm ID instead.
     */
    @Deprecated(forRemoval = true)
    public static MagicLinkEventCache getInstance() {
        return getInstance("__default__");
    }

    /**
     * Remove a realm's cache.
     */
    public static void removeInstance(String realmId) {
        MagicLinkEventCache removed = REALM_CACHES.remove(realmId);
        if (removed != null) {
            removed.clearEventLog();
            LOG.infof("MagicLink: Removed per-realm event cache for realm [%s]", realmId);
        }
    }

    // ── Event log entry ────────────────────────────────────────

    /**
     * A single Magic Link authentication event.
     */
    public static class EventEntry {
        private final String username;
        private final String phoneNumber;
        private final String outcome;
        private final String responseCode;
        private final String ipAddress;
        private final Instant timestamp;
        private final long durationMs;
        private final String errorMessage;

        public EventEntry(String username, String phoneNumber, String outcome,
                          String responseCode, String ipAddress, Instant timestamp,
                          long durationMs, String errorMessage) {
            this.username = username;
            this.phoneNumber = phoneNumber;
            this.outcome = outcome;
            this.responseCode = responseCode;
            this.ipAddress = ipAddress;
            this.timestamp = timestamp;
            this.durationMs = durationMs;
            this.errorMessage = errorMessage;
        }

        public String getUsername()      { return username; }
        public String getPhoneNumber()   { return phoneNumber; }
        public String getOutcome()       { return outcome; }
        public String getResponseCode()  { return responseCode; }
        public String getIpAddress()     { return ipAddress; }
        public Instant getTimestamp()     { return timestamp; }
        public long getDurationMs()      { return durationMs; }
        public String getErrorMessage()  { return errorMessage; }

        public String toJson() {
            return "{\"username\":\"" + esc(username) + "\""
                + ",\"phoneNumber\":\"" + esc(phoneNumber) + "\""
                + ",\"outcome\":\"" + esc(outcome) + "\""
                + ",\"responseCode\":\"" + esc(responseCode) + "\""
                + ",\"ipAddress\":\"" + esc(ipAddress) + "\""
                + ",\"timestamp\":\"" + timestamp.toString() + "\""
                + ",\"durationMs\":" + durationMs
                + ",\"errorMessage\":\"" + esc(errorMessage) + "\""
                + "}";
        }

        private static String esc(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n");
        }
    }

    // ── Storage ────────────────────────────────────────────────

    private volatile int maxEventLogSize = MagicLinkConfig.DEFAULT_EVENT_LOG_MAX_SIZE;
    private volatile long eventTtlSeconds = MagicLinkConfig.DEFAULT_EVENT_TTL_HOURS * 3600L;

    private final LinkedList<EventEntry> eventLog = new LinkedList<>();
    private final Object eventLogLock = new Object();

    // ── Statistics ──────────────────────────────────────────────

    private final java.util.concurrent.atomic.AtomicLong totalAttempts = new java.util.concurrent.atomic.AtomicLong(0);
    private final java.util.concurrent.atomic.AtomicLong totalSuccess = new java.util.concurrent.atomic.AtomicLong(0);
    private final java.util.concurrent.atomic.AtomicLong totalRejected = new java.util.concurrent.atomic.AtomicLong(0);
    private final java.util.concurrent.atomic.AtomicLong totalErrors = new java.util.concurrent.atomic.AtomicLong(0);
    private final java.util.concurrent.atomic.AtomicLong totalNoPhone = new java.util.concurrent.atomic.AtomicLong(0);
    private final java.util.concurrent.atomic.AtomicLong totalTimeout = new java.util.concurrent.atomic.AtomicLong(0);

    private MagicLinkEventCache() {
        // Singleton per realm
    }

    // ── Config ─────────────────────────────────────────────────

    public void applyConfig(int maxSize, int ttlHours) {
        this.maxEventLogSize = Math.max(100, Math.min(maxSize, HARD_MAX_EVENT_LOG_SIZE));
        this.eventTtlSeconds = Math.max(1, ttlHours) * 3600L;
    }

    public int getMaxEventLogSize() { return maxEventLogSize; }
    public int getEventTtlHours()   { return (int) (eventTtlSeconds / 3600); }

    // ── Record events ──────────────────────────────────────────

    /**
     * Record a Magic Link authentication event.
     *
     * @param username     the Keycloak username
     * @param phoneNumber  the phone number used (masked for privacy in logs)
     * @param outcome      "success", "rejected", "timeout", "error", "no_phone", "not_configured"
     * @param responseCode the raw API response code (e.g., "TOUCH_ACCEPTED", "TOUCH_REJECTED")
     * @param ipAddress    the client IP address
     * @param durationMs   how long the API call took in milliseconds
     * @param errorMessage optional error message (for failures)
     */
    public void recordEvent(String username, String phoneNumber, String outcome,
                            String responseCode, String ipAddress, long durationMs,
                            String errorMessage) {
        EventEntry entry = new EventEntry(
            username, phoneNumber, outcome, responseCode, ipAddress,
            Instant.now(), durationMs, errorMessage);

        synchronized (eventLogLock) {
            eventLog.addFirst(entry);
            while (eventLog.size() > maxEventLogSize) {
                eventLog.removeLast();
            }
        }

        // Update stats
        totalAttempts.incrementAndGet();
        switch (outcome != null ? outcome : "") {
            case "success"  -> totalSuccess.incrementAndGet();
            case "rejected" -> totalRejected.incrementAndGet();
            case "error"    -> totalErrors.incrementAndGet();
            case "no_phone" -> totalNoPhone.incrementAndGet();
            case "timeout"  -> totalTimeout.incrementAndGet();
        }
    }

    // ── Query events ───────────────────────────────────────────

    /**
     * Get recent events (newest first), up to limit.
     */
    public List<EventEntry> getRecentEvents(int limit) {
        synchronized (eventLogLock) {
            int count = Math.min(limit, eventLog.size());
            return new ArrayList<>(eventLog.subList(0, count));
        }
    }

    public int getEventLogSize() {
        synchronized (eventLogLock) {
            return eventLog.size();
        }
    }

    /**
     * Prune events older than the configured TTL.
     *
     * @return number of entries pruned
     */
    public int pruneOldEvents() {
        Instant cutoff = Instant.now().minusSeconds(eventTtlSeconds);
        int pruned = 0;
        synchronized (eventLogLock) {
            while (!eventLog.isEmpty() && eventLog.getLast().getTimestamp().isBefore(cutoff)) {
                eventLog.removeLast();
                pruned++;
            }
        }
        if (pruned > 0) {
            LOG.debugf("MagicLink: Pruned %d expired event log entries", pruned);
        }
        return pruned;
    }

    /**
     * Get the top usernames by authentication attempt count.
     */
    public List<Map.Entry<String, Long>> getTopUsernames(int limit) {
        Map<String, Long> counts = new LinkedHashMap<>();
        synchronized (eventLogLock) {
            for (EventEntry e : eventLog) {
                String user = e.getUsername();
                if (user != null && !user.isEmpty()) {
                    counts.merge(user, 1L, Long::sum);
                }
            }
        }
        return counts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(limit)
            .toList();
    }

    // ── Statistics ──────────────────────────────────────────────

    public long getTotalAttempts()  { return totalAttempts.get(); }
    public long getTotalSuccess()   { return totalSuccess.get(); }
    public long getTotalRejected()  { return totalRejected.get(); }
    public long getTotalErrors()    { return totalErrors.get(); }
    public long getTotalNoPhone()   { return totalNoPhone.get(); }
    public long getTotalTimeout()   { return totalTimeout.get(); }

    public void resetStats() {
        totalAttempts.set(0);
        totalSuccess.set(0);
        totalRejected.set(0);
        totalErrors.set(0);
        totalNoPhone.set(0);
        totalTimeout.set(0);
        LOG.info("MagicLink: Statistics counters reset");
    }

    public void clearEventLog() {
        synchronized (eventLogLock) {
            eventLog.clear();
        }
        LOG.info("MagicLink: Event log cleared");
    }
}
