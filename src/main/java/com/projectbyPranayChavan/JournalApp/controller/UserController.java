package com.projectbyPranayChavan.JournalApp.controller;

import com.projectbyPranayChavan.JournalApp.api.response.WeatherResponse;
import com.projectbyPranayChavan.JournalApp.dto.UpdateUserRequest;
import com.projectbyPranayChavan.JournalApp.dto.UserResponse;
import com.projectbyPranayChavan.JournalApp.dto.WeatherView;
import com.projectbyPranayChavan.JournalApp.entities.User;
import com.projectbyPranayChavan.JournalApp.exception.ApiException;
import com.projectbyPranayChavan.JournalApp.security.AuthCookieService;
import com.projectbyPranayChavan.JournalApp.service.UserService;
import com.projectbyPranayChavan.JournalApp.service.WeatherService;
import com.projectbyPranayChavan.JournalApp.utils.JwtUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.regex.Pattern;

/*
 * SECURITY: this controller only ever works on the signed-in user ("me").
 * REMOVED because they were critical holes.
 * Listing users is now an admin-only feature (AdminController).
 */
@Tag(name = "User APIs")
@RestController
@RequestMapping("/user")
public class UserController {

    private static final Pattern CITY = Pattern.compile("^\\p{L}[\\p{L} .'-]{0,59}$");

    private final UserService userService;
    private final WeatherService weatherService;
    private final JwtUtils jwtUtils;
    private final AuthCookieService cookieService;
    private final String defaultCity;

    public UserController(UserService userService,
                          WeatherService weatherService,
                          JwtUtils jwtUtils,
                          AuthCookieService cookieService,
                          @Value("${weather.default-city:Mumbai}") String defaultCity) {
        this.userService = userService;
        this.weatherService = weatherService;
        this.jwtUtils = jwtUtils;
        this.cookieService = cookieService;
        this.defaultCity = defaultCity;
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return UserResponse.from(userService.getByUsername(authentication.getName()));
    }

    @PutMapping
    public ResponseEntity<UserResponse> updateUser(@Valid @RequestBody UpdateUserRequest request,
                                                   Authentication authentication,
                                                   HttpServletResponse response) {
        User current = userService.getByUsername(authentication.getName());
        int versionBefore = current.getTokenVersion();

        User updated = userService.updateProfile(current.getId(), request);

        if (updated.getTokenVersion() != versionBefore) {
            // password changed: older tokens are now invalid, so give this browser a fresh one
            cookieService.addAuthCookie(response, jwtUtils.generateToken(updated));
        }
        return ResponseEntity.ok(UserResponse.from(updated));
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteUser(Authentication authentication, HttpServletResponse response) {
        User current = userService.getByUsername(authentication.getName());
        userService.deleteAccount(current.getId());
        cookieService.clearAuthCookie(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/weather")
    public WeatherView weather(@RequestParam(required = false) String city) {
        String chosen = (city == null || city.isBlank()) ? defaultCity : city.trim();
        if (!CITY.matcher(chosen).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter a valid city name");
        }
        return weatherService.getWeather(chosen)
                .map(WeatherResponse::getCurrent)
                .map(c -> new WeatherView(
                        true,
                        chosen,
                        c.getTemperature(),
                        c.getFeelslike(),
                        c.getWeatherDescriptions() == null || c.getWeatherDescriptions().isEmpty()
                                ? null : c.getWeatherDescriptions().get(0)))
                .orElseGet(() -> WeatherView.unavailable(chosen));
    }
}
