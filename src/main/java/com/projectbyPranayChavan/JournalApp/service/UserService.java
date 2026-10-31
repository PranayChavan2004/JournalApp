package com.projectbyPranayChavan.JournalApp.service;

import com.projectbyPranayChavan.JournalApp.dto.UpdateUserRequest;
import com.projectbyPranayChavan.JournalApp.dto.UserDTO;
import com.projectbyPranayChavan.JournalApp.entities.User;
import com.projectbyPranayChavan.JournalApp.enums.AuthProvider;
import com.projectbyPranayChavan.JournalApp.exception.ApiException;
import com.projectbyPranayChavan.JournalApp.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User registerUser(UserDTO dto) {
        return create(dto, List.of("USER"));
    }

    @Transactional
    public User createAdmin(UserDTO dto) {
        return create(dto, List.of("USER", "ADMIN"));
    }

    private User create(UserDTO dto, List<String> roles) {
        String username = dto.getUserName().trim();
        String email = normalizeEmail(dto.getEmail());

        if (dto.isSentimentAnalysis() && email == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Add an e-mail address to receive the weekly mood summary");
        }
        if (userRepository.existsByUsername(username)) {
            throw new ApiException(HttpStatus.CONFLICT, "That username is already taken");
        }
        if (email != null && userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with that e-mail already exists");
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .sentimentAnalysis(dto.isSentimentAnalysis())
                .password(passwordEncoder.encode(dto.getPassword()))
                .roles(new ArrayList<>(roles))
                .provider(AuthProvider.LOCAL)
                .build();
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // two people registering the same name at the same instant
            throw new ApiException(HttpStatus.CONFLICT, "That username or e-mail is already in use");
        }
    }


    @Transactional
    public User findOrCreateGoogleUser(String email) {
        String normalized = normalizeEmail(email);
        User existing = userRepository.findFirstByEmail(normalized);
        if (existing != null) {
            if (existing.effectiveProvider() == AuthProvider.GOOGLE) {
                return existing;
            }
            throw new ApiException(HttpStatus.CONFLICT, "account_exists");
        }
        if (userRepository.existsByUsername(normalized)) {
            throw new ApiException(HttpStatus.CONFLICT, "account_exists");
        }
        User user = User.builder()
                .username(normalized)
                .email(normalized)
                .password(passwordEncoder.encode(UUID.randomUUID().toString())) // unusable: Google users never type a password
                .roles(new ArrayList<>(List.of("USER")))
                .provider(AuthProvider.GOOGLE)
                .build();
        return userRepository.save(user);
    }


    @Transactional
    public User updateProfile(UUID userId, UpdateUserRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        boolean google = user.effectiveProvider() == AuthProvider.GOOGLE;

        if (req.getUsername() != null && !req.getUsername().isBlank()
                && !req.getUsername().trim().equals(user.getUsername())) {
            if (google) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Your username comes from your Google account");
            }
            String username = req.getUsername().trim();
            if (userRepository.existsByUsername(username)) {
                throw new ApiException(HttpStatus.CONFLICT, "That username is already taken");
            }
            user.setUsername(username);
        }

        if (req.getEmail() != null) {
            String email = normalizeEmail(req.getEmail());
            if (!java.util.Objects.equals(email, user.getEmail())) {
                if (google) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Your e-mail comes from your Google account");
                }
                if (email != null && userRepository.existsByEmail(email)) {
                    throw new ApiException(HttpStatus.CONFLICT, "An account with that e-mail already exists");
                }
                user.setEmail(email);
            }
        }

        if (req.getSentimentAnalysis() != null) {
            user.setSentimentAnalysis(req.getSentimentAnalysis());
        }
        if (user.isSentimentAnalysis() && user.getEmail() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Add an e-mail address to receive the weekly mood summary");
        }

        if (req.getNewPassword() != null && !req.getNewPassword().isBlank()) {
            if (google) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Your account signs in with Google and has no password");
            }
            if (req.getCurrentPassword() == null
                    || !passwordEncoder.matches(req.getCurrentPassword(), user.getPassword())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Current password is incorrect");
            }
            user.setPassword(passwordEncoder.encode(req.getNewPassword()));
            user.setTokenVersion(user.getTokenVersion() + 1); // revokes every older token
        }

        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "That username or e-mail is already in use");
        }
    }

    /** Deletes the account and (through cascade) all of its journal entries. */
    @Transactional
    public void deleteAccount(UUID userId) {
        userRepository.findById(userId).ifPresent(userRepository::delete);
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public List<User> getAll() {
        return userRepository.findAll();
    }

    public User findByUserName(String username) {
        return userRepository.findByUsername(username);
    }

    /** Accepts a username or an e-mail address. */
    public User findByLogin(String login) {
        if (login == null) {
            return null;
        }
        String trimmed = login.trim();
        User user = userRepository.findByUsername(trimmed);
        if (user == null && trimmed.contains("@")) {
            user = userRepository.findFirstByEmail(normalizeEmail(trimmed));
        }
        return user;
    }

    public User getByUsername(String username) {
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Please sign in again");
        }
        return user;
    }

    public boolean usernameExists(String username) {
        return userRepository.existsByUsername(username);
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
