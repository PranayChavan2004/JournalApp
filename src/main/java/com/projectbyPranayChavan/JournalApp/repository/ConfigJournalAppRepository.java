package com.projectbyPranayChavan.JournalApp.repository;

import com.projectbyPranayChavan.JournalApp.entities.ConfigJournalAppEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ConfigJournalAppRepository extends JpaRepository<ConfigJournalAppEntity, String> {
}
