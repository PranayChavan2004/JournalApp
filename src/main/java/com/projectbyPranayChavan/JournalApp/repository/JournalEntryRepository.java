package com.projectbyPranayChavan.JournalApp.repository;

import com.projectbyPranayChavan.JournalApp.entities.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    /* SECURITY: both queries are scoped to the owner (u.id = :userId), so one user can never
       read or modify another user's entries just by guessing an id. */

    @Query("select j from User u join u.journalEntries j where u.id = :userId order by j.date desc")
    List<JournalEntry> findAllByUserId(@Param("userId") UUID userId);

    @Query("select j from User u join u.journalEntries j where u.id = :userId and j.id = :entryId")
    Optional<JournalEntry> findByUserIdAndId(@Param("userId") UUID userId, @Param("entryId") UUID entryId);
}
