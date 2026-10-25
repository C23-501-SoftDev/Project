package com.knowledgebase.application.service;

import com.knowledgebase.domain.rag.RagException;
import com.knowledgebase.infrastructure.config.RagProperties;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

/** Per-process resource limits for the local model: no external billing or persistent user text. */
@Component
public class RagRequestLimiter {
    private final RagProperties properties;
    private final Clock clock;
    private final Semaphore concurrent = new Semaphore(2);
    private final Map<Long, Window> windows = new HashMap<>();
    private LocalDate day;
    private int dailyCount;
    @org.springframework.beans.factory.annotation.Autowired
    public RagRequestLimiter(RagProperties properties) { this(properties, Clock.systemUTC()); }
    RagRequestLimiter(RagProperties properties, Clock clock) { this.properties = properties; this.clock = clock; }
    public synchronized Lease acquire(Long userId) {
        var today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (!today.equals(day)) { day = today; dailyCount = 0; windows.clear(); }
        long minute = clock.instant().getEpochSecond() / 60;
        windows.entrySet().removeIf(e -> e.getValue().minute != minute);
        Window window = windows.computeIfAbsent(userId, id -> new Window(minute));
        if (window.count >= properties.getRequestsPerMinute() || dailyCount >= properties.getDailyRequests()
                || !concurrent.tryAcquire()) throw new RagException(RagException.Code.RATE_LIMITED);
        window.count++; dailyCount++;
        return new Lease();
    }
    private static class Window {
        final long minute; int count;
        Window(long minute) { this.minute = minute; }
    }
    public final class Lease implements AutoCloseable {
        private boolean closed;
        @Override public synchronized void close() { if (!closed) { closed = true; concurrent.release(); } }
    }
}
