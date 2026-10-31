package com.projectbyPranayChavan.JournalApp.controller;

import com.projectbyPranayChavan.JournalApp.dto.JournalEntryRequest;
import com.projectbyPranayChavan.JournalApp.dto.JournalEntryResponse;
import com.projectbyPranayChavan.JournalApp.entities.User;
import com.projectbyPranayChavan.JournalApp.service.JournalEntryService;
import com.projectbyPranayChavan.JournalApp.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;


@Tag(name = "Journal APIs")
@RestController
@RequestMapping("/journal")
public class JournalController {

    private final JournalEntryService journalEntryService;
    private final UserService userService;

    public JournalController(JournalEntryService journalEntryService, UserService userService) {
        this.journalEntryService = journalEntryService;
        this.userService = userService;
    }

    private User currentUser(Authentication authentication) {
        return userService.getByUsername(authentication.getName());
    }

    @GetMapping
    public List<JournalEntryResponse> getAllJournalEntriesOfUser(Authentication authentication) {
        return journalEntryService.list(currentUser(authentication).getId());
    }

    @PostMapping
    public ResponseEntity<JournalEntryResponse> addJournalEntry(@Valid @RequestBody JournalEntryRequest entry,
                                                                Authentication authentication) {
        JournalEntryResponse created = journalEntryService.create(currentUser(authentication).getId(), entry);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/id/{id}")
    public JournalEntryResponse getById(@PathVariable UUID id, Authentication authentication) {
        return journalEntryService.get(currentUser(authentication).getId(), id);
    }

    @PutMapping("/{id}")
    public JournalEntryResponse updateEntry(@PathVariable UUID id,
                                            @Valid @RequestBody JournalEntryRequest entry,
                                            Authentication authentication) {
        return journalEntryService.update(currentUser(authentication).getId(), id, entry);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEntry(@PathVariable UUID id, Authentication authentication) {
        journalEntryService.delete(currentUser(authentication).getId(), id);
        return ResponseEntity.noContent().build();
    }
}
