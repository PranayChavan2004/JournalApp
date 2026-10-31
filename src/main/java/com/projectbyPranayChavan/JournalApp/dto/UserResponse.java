package com.projectbyPranayChavan.JournalApp.dto;

import com.projectbyPranayChavan.JournalApp.entities.User;

import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String email,
        boolean sentimentAnalysis,
        List<String> roles,
        String provider
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.isSentimentAnalysis(),
                user.getRoles() == null ? List.of() : List.copyOf(user.getRoles()),
                user.effectiveProvider().name()
        );
    }
}
