package com.projectbyPranayChavan.JournalApp.controller;

import com.projectbyPranayChavan.JournalApp.dto.AuthResponse;
import com.projectbyPranayChavan.JournalApp.dto.LoginRequest;
import com.projectbyPranayChavan.JournalApp.dto.UserDTO;
import com.projectbyPranayChavan.JournalApp.dto.UserResponse;
import com.projectbyPranayChavan.JournalApp.entities.User;
import com.projectbyPranayChavan.JournalApp.exception.ApiException;
import com.projectbyPranayChavan.JournalApp.security.AuthCookieService;
import com.projectbyPranayChavan.JournalApp.security.LoginAttemptService;
import com.projectbyPranayChavan.JournalApp.service.UserService;
import com.projectbyPranayChavan.JournalApp.utils.JwtUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;

/** Endpoints that need no sign-in: health check, sign-up, login, logout, UI config. */
@Slf4j
@Tag(name = "Public APIs")
@RestController
@RequestMapping("/public")
public class PublicController {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final JwtUtils jwtUtil;
    private final AuthCookieService cookieService;
    private final LoginAttemptService loginAttempts;
    private final boolean googleEnabled;

    public PublicController(AuthenticationManager authenticationManager,
                            UserService userService,
                            JwtUtils jwtUtil,
                            AuthCookieService cookieService,
                            LoginAttemptService loginAttempts,
                            @Value("${spring.security.oauth2.client.registration.google.client-id:not-configured}") String googleClientId) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.cookieService = cookieService;
        this.loginAttempts = loginAttempts;
        this.googleEnabled = googleClientId != null && !googleClientId.isBlank() && !"not-configured".equals(googleClientId);
    }

    @GetMapping("/health-check")
    public String health() {
        return "okk working fine...";
    }

    /** Lets the web UI know whether to show the "Continue with Google" button. */
    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of("googleEnabled", googleEnabled);
    }

    // Sign up: nobody is signed in yet
    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signup(@Valid @RequestBody UserDTO user) {
        User created = userService.registerUser(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(created));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest http,
                                              HttpServletResponse response) {
        String login = request.getUsername().trim();
        String attemptKey = http.getRemoteAddr() + "|" + login.toLowerCase(Locale.ROOT);

        if (loginAttempts.isBlocked(attemptKey)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed attempts. Please wait 15 minutes and try again.");
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(login, request.getPassword()));
        } catch (AuthenticationException e) {
            loginAttempts.recordFailure(attemptKey);
            // same answer for "no such user" and "wrong password" so accounts cannot be probed
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Incorrect username or password");
        }

        loginAttempts.reset(attemptKey);
        User user = userService.getByUsername(authentication.getName());
        String jwt = jwtUtil.generateToken(user);
        cookieService.addAuthCookie(response, jwt);

        return ResponseEntity.ok(new AuthResponse(jwt, "Bearer", jwtUtil.getExpirationSeconds(), UserResponse.from(user)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        cookieService.clearAuthCookie(response);
        return ResponseEntity.noContent().build();
    }
}
