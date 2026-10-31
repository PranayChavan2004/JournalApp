package com.projectbyPranayChavan.JournalApp.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "config_journal_app")
@Data
@NoArgsConstructor
public class ConfigJournalAppEntity {

    @Id
    @Column(name = "config_key")
    private String key;

    @Column(name = "config_value")
    private String value;
}