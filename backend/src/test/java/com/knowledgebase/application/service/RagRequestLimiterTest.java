package com.knowledgebase.application.service;

import com.knowledgebase.domain.rag.RagException;
import com.knowledgebase.infrastructure.config.RagProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class RagRequestLimiterTest {
    private RagProperties properties;
    private MutableClock clock;
    private RagRequestLimiter limiter;

    @BeforeEach
    void setUp() {
        properties = new RagProperties();
        clock = new MutableClock(Instant.parse("2026-10-26T00:00:05Z"));
        limiter = new RagRequestLimiter(properties, clock);
    }

    @Test
    void perUserMinuteQuotaResetsAtNextWindowAndDoesNotAffectAnotherUser() {
        properties.setRequestsPerMinute(2);
        limiter.acquire(1L).close();
        limiter.acquire(1L).close();
        assertLimited(1L);
        limiter.acquire(2L).close();
        clock.now = Instant.parse("2026-10-26T00:01:00Z");
        limiter.acquire(1L).close();
    }

    @Test
    void dailyQuotaIsGlobalAndResetsOnUtcDayBoundary() {
        properties.setDailyRequests(2);
        limiter.acquire(1L).close();
        limiter.acquire(2L).close();
        assertLimited(3L);
        clock.now = Instant.parse("2026-10-26T23:59:59Z");
        assertLimited(1L);
        clock.now = Instant.parse("2026-10-27T00:00:00Z");
        limiter.acquire(3L).close();
    }

    @Test
    void permitsAtMostTwoConcurrentRequestsAndCloseIsIdempotent() {
        var first = limiter.acquire(1L);
        var second = limiter.acquire(2L);
        assertLimited(3L);
        first.close();
        first.close();
        var third = limiter.acquire(3L);
        assertLimited(4L); // Closing the same lease twice must not create a third permit.
        second.close();
        third.close();
        limiter.acquire(4L).close();
    }

    @Test
    void failedConcurrencyAdmissionDoesNotConsumeDailyQuota() {
        properties.setDailyRequests(3);
        var first = limiter.acquire(1L);
        var second = limiter.acquire(2L);
        assertLimited(3L);
        first.close();
        limiter.acquire(3L).close();
        second.close();
        assertLimited(4L);
    }

    @Test
    void tryWithResourcesReleasesPermitEvenWhenWorkThrows() {
        assertThrows(IllegalStateException.class, () -> {
            try (var ignored = limiter.acquire(1L)) {
                throw new IllegalStateException("work failed");
            }
        });
        try (var first = limiter.acquire(2L); var second = limiter.acquire(3L)) {
            assertLimited(4L);
        }
    }

    private void assertLimited(Long userId) {
        assertEquals(RagException.Code.RATE_LIMITED,
                assertThrows(RagException.class, () -> limiter.acquire(userId)).getCode());
    }

    private static class MutableClock extends Clock {
        private Instant now;
        private MutableClock(Instant now) { this.now = now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
