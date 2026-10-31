package com.projectbyPranayChavan.JournalApp.service;

import com.projectbyPranayChavan.JournalApp.dto.JournalEntryRequest;
import com.projectbyPranayChavan.JournalApp.dto.JournalEntryResponse;
import com.projectbyPranayChavan.JournalApp.entities.JournalEntry;
import com.projectbyPranayChavan.JournalApp.entities.User;
import com.projectbyPranayChavan.JournalApp.exception.ApiException;
import com.projectbyPranayChavan.JournalApp.repository.JournalEntryRepository;
import com.projectbyPranayChavan.JournalApp.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;


@Service
public class JournalEntryService {

    private final JournalEntryRepository journalEntryRepository;
    private final UserRepository userRepository;

    public JournalEntryService(JournalEntryRepository journalEntryRepository, UserRepository userRepository) {
        this.journalEntryRepository = journalEntryRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<JournalEntryResponse> list(UUID userId) {
        return journalEntryRepository.findAllByUserId(userId).stream()
                .map(JournalEntryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public JournalEntryResponse get(UUID userId, UUID entryId) {
        return JournalEntryResponse.from(find(userId, entryId));
    }

    @Transactional
    public JournalEntryResponse create(UUID userId, JournalEntryRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Please sign in again"));

        JournalEntry entry = new JournalEntry();
        entry.setTitle(request.getTitle().trim());
        entry.setContent(request.getContent());
        entry.setSentiment(request.getSentiment());
        entry.setDate(LocalDateTime.now(ZoneOffset.UTC));

        JournalEntry saved = journalEntryRepository.save(entry);
        user.getJournalEntries().add(saved);
        return JournalEntryResponse.from(saved);
    }

    @Transactional
    public JournalEntryResponse update(UUID userId, UUID entryId, JournalEntryRequest request) {
        JournalEntry entry = find(userId, entryId);
        entry.setTitle(request.getTitle().trim());
        entry.setContent(request.getContent());
        entry.setSentiment(request.getSentiment());
        // The creation date stays as it was (the old code moved the entry to "now" on every edit).
        return JournalEntryResponse.from(entry);
    }

    @Transactional
    public void delete(UUID userId, UUID entryId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Please sign in again"));
        boolean removed = user.getJournalEntries().removeIf(e -> e.getId().equals(entryId));
        if (!removed) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Journal entry not found");
        }
        // orphanRemoval on User.journalEntries deletes the row for us
    }

    private JournalEntry find(UUID userId, UUID entryId) {
        // Looking up through the owner means "not yours" and "does not exist" look identical.
        return journalEntryRepository.findByUserIdAndId(userId, entryId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Journal entry not found"));
    }
}
