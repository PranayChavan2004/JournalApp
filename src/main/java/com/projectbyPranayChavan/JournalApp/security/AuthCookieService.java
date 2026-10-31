package com.projectbyPranayChavan.JournalApp.security;

import com.projectbyPranayChavan.JournalApp.utils.JwtUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class AuthCookieService {

    private final JwtUtils jwtUtils;
    private final String cookieName;
    private final boolean secure;

    public AuthCookieService(JwtUtils jwtUtils,
                             @Value("${app.cookie.name:JOURNAL_TOKEN}") String cookieName,
                             @Value("${app.cookie.secure:true}") boolean secure) {
        this.jwtUtils = jwtUtils;
        this.cookieName = cookieName;
        this.secure = secure;
    }

    public void addAuthCookie(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(token, Duration.ofSeconds(jwtUtils.getExpirationSeconds())).toString());
    }

    public void clearAuthCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build("", Duration.ZERO).toString());
    }

    public Optional<String> readToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
