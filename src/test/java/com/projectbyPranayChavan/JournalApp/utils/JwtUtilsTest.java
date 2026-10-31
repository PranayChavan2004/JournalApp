package com.projectbyPranayChavan.JournalApp.utils;

import com.projectbyPranayChavan.JournalApp.entities.User;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilsTest {

    private static final String SECRET = "unit-test-secret-which-is-definitely-longer-than-32-chars";

    private JwtUtils utils(String secret, long minutes) {
        return new JwtUtils(secret, minutes, "journal-app", new MockEnvironment());
    }

    private User user(int version) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setUsername("alice");
        u.setTokenVersion(version);
        return u;
    }

    @Test
    void validTokenRoundTrips() {
        JwtUtils jwt = utils(SECRET, 60);
        User u = user(3);

        var parsed = jwt.parse(jwt.generateToken(u));

        assertThat(parsed).isPresent();
        assertThat(parsed.get().userId()).isEqualTo(u.getId());
        assertThat(parsed.get().version()).isEqualTo(3);
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtUtils jwt = utils(SECRET, 60);
        String token = jwt.generateToken(user(0));
        String tampered = token.substring(0, token.length() - 3) + (token.endsWith("AAA") ? "BBB" : "AAA");

        assertThat(jwt.parse(tampered)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String token = utils("another-secret-that-is-also-longer-than-32-characters", 60).generateToken(user(0));

        assertThat(utils(SECRET, 60).parse(token)).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() {
        JwtUtils jwt = utils(SECRET, -10); // expired 10 minutes ago (beyond the 30 s clock skew)

        assertThat(jwt.parse(jwt.generateToken(user(0)))).isEmpty();
    }

    @Test
    void garbageIsRejectedWithoutThrowing() {
        JwtUtils jwt = utils(SECRET, 60);

        assertThat(jwt.parse("not-a-jwt")).isEmpty();
        assertThat(jwt.parse("")).isEmpty();
        assertThat(jwt.parse(null)).isEmpty();
    }

    @Test
    void shortSecretIsRefused() {
        assertThatThrownBy(() -> utils("too-short", 60)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void missingSecretIsRefusedInProduction() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThatThrownBy(() -> new JwtUtils("", 60, "journal-app", prod)).isInstanceOf(IllegalStateException.class);
    }
}
