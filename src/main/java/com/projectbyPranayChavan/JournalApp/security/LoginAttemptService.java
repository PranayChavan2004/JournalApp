package com.projectbyPranayChavan.JournalApp.security;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

@Component
public class LoginAttemptService {

    // after 5 wrong passwords within 15 minutes the same login is locked for 15 minutes.
    static final int MAX_FAILURES = 5;
    static final long WINDOW_MS = 15 * 60 * 1000L;
    static final long LOCK_MS = 15 * 60 * 1000L;
    private static final int CLEANUP_THRESHOLD = 10_000;

    private record State(int failures, long windowStart, long lockedUntil) {
    }

    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public LoginAttemptService() {
        this(System::currentTimeMillis);
    }

    LoginAttemptService(LongSupplier clock) {
        this.clock = clock;
    }

    public boolean isBlocked(String key) {
        State state = states.get(key);
        return state != null && state.lockedUntil() > clock.getAsLong();
    }

    public void recordFailure(String key) {
        long now = clock.getAsLong();
        states.compute(key, (k, s) -> {
            if (s == null || now - s.windowStart() > WINDOW_MS) {
                return new State(1, now, 0);
            }
            int failures = s.failures() + 1;
            return new State(failures, s.windowStart(), failures >= MAX_FAILURES ? now + LOCK_MS : 0);
        });
        if (states.size() > CLEANUP_THRESHOLD) {
            states.entrySet().removeIf(e -> now - e.getValue().windowStart() > WINDOW_MS
                    && e.getValue().lockedUntil() <= now);
        }
    }

    public void reset(String key) {
        states.remove(key);
    }
}
