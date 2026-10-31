package com.projectbyPranayChavan.JournalApp.entities;

import com.projectbyPranayChavan.JournalApp.enums.Sentiment;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "Journal_Entries")
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    /* FIX: was a default VARCHAR(255) */
    @Column(columnDefinition = "TEXT")
    private String content;

    private LocalDateTime date;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private Sentiment sentiment;
}
