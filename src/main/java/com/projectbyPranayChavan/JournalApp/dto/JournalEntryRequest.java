package com.projectbyPranayChavan.JournalApp.dto;

import com.projectbyPranayChavan.JournalApp.enums.Sentiment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class JournalEntryRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 150, message = "Title must be at most 150 characters")
    private String title;

    @Size(max = 10000, message = "Entry must be at most 10,000 characters")
    private String content;

    private Sentiment sentiment;
}
