package com.projectbyPranayChavan.JournalApp.dto;

import com.projectbyPranayChavan.JournalApp.entities.JournalEntry;
import com.projectbyPranayChavan.JournalApp.enums.Sentiment;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

public record JournalEntryResponse(UUID id, String title, String content, Instant date, Sentiment sentiment) {

    public static JournalEntryResponse from(JournalEntry e) {

        Instant when = e.getDate() == null ? null : e.getDate().toInstant(ZoneOffset.UTC);
        return new JournalEntryResponse(e.getId(), e.getTitle(), e.getContent(), when, e.getSentiment());
    }
}
