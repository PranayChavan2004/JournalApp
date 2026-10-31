package com.projectbyPranayChavan.JournalApp.repository;

import com.projectbyPranayChavan.JournalApp.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    User findByUsername(String username);

    User findFirstByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
