package com.projectbyPranayChavan.JournalApp.security;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final LoginAttemptService service = new LoginAttemptService(now::get);

    @Test
    void locksAfterFiveFailures() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES - 1; i++) {
            service.recordFailure("ip|alice");
            assertThat(service.isBlocked("ip|alice")).isFalse();
        }
        service.recordFailure("ip|alice");

        assertThat(service.isBlocked("ip|alice")).isTrue();
        assertThat(service.isBlocked("ip|bob")).isFalse(); // other logins are unaffected
    }

    @Test
    void lockExpires() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES; i++) {
            service.recordFailure("k");
        }
        assertThat(service.isBlocked("k")).isTrue();

        now.addAndGet(LoginAttemptService.LOCK_MS + 1);

        assertThat(service.isBlocked("k")).isFalse();
    }

    @Test
    void successfulLoginClearsTheCounter() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES - 1; i++) {
            service.recordFailure("k");
        }
        service.reset("k");
        service.recordFailure("k");

        assertThat(service.isBlocked("k")).isFalse();
    }
}
