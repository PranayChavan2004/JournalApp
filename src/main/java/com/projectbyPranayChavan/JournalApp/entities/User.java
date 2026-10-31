package com.projectbyPranayChavan.JournalApp.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.projectbyPranayChavan.JournalApp.enums.AuthProvider;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true)
    private String email;

    private boolean sentimentAnalysis;

    @JsonIgnore
    @Column(nullable = false)
    private String password;


    @JsonIgnore
    @Builder.Default
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinTable(
            name = "user_journal",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "journal_id")
    )
    private List<JournalEntry> journalEntries = new ArrayList<>();

    private List<String> roles;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private AuthProvider provider;
    private int tokenVersion;

    public AuthProvider effectiveProvider() {
        return provider == null ? AuthProvider.LOCAL : provider;
    }
}
