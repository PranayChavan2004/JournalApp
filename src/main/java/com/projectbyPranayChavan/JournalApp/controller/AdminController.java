package com.projectbyPranayChavan.JournalApp.controller;

import com.projectbyPranayChavan.JournalApp.cache.AppCache;
import com.projectbyPranayChavan.JournalApp.dto.UserDTO;
import com.projectbyPranayChavan.JournalApp.dto.UserResponse;
import com.projectbyPranayChavan.JournalApp.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/* Double protection: the URL rule in SpringSecurity.java AND @PreAuthorize here. */
@Tag(name = "Admin APIs")
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserService userService;
    private final AppCache appCache;

    public AdminController(UserService userService, AppCache appCache) {
        this.userService = userService;
        this.appCache = appCache;
    }

    @GetMapping("/all-users")
    public List<UserResponse> getAllUsers() {
        return userService.getAll().stream().map(UserResponse::from).toList();
    }

    // Create another admin (only an existing admin can do this)
    @PostMapping("/create-admin-user")
    public ResponseEntity<UserResponse> createAdmin(@Valid @RequestBody UserDTO user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(userService.createAdmin(user)));
    }

    @PostMapping("/clear-app-cache")
    public ResponseEntity<Void> clearAppCache() {
        appCache.init();
        return ResponseEntity.noContent().build();
    }
}
