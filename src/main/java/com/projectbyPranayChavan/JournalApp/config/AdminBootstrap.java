package com.projectbyPranayChavan.JournalApp.config;

import com.projectbyPranayChavan.JournalApp.dto.UserDTO;
import com.projectbyPranayChavan.JournalApp.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * need to create the very first admin account on startup, if ADMIN_USERNAME and ADMIN_PASSWORD are set
 * and no such user exists yet. Without this, a fresh deployment would have no way to get an admin
 * except editing the database by hand.
 */
@Slf4j
@Component
public class AdminBootstrap implements ApplicationRunner {

    private final UserService userService;
    private final String username;
    private final String password;
    private final String email;

    public AdminBootstrap(UserService userService,
                          @Value("${app.admin.username:}") String username,
                          @Value("${app.admin.password:}") String password,
                          @Value("${app.admin.email:}") String email) {
        this.userService = userService;
        this.username = username;
        this.password = password;
        this.email = email;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (username.isBlank() || password.isBlank()) {
            return;
        }
        if (userService.usernameExists(username.trim())) {
            return;
        }
        try {
            userService.createAdmin(new UserDTO(username.trim(), email.isBlank() ? null : email.trim(), false, password));
            log.info("Initial admin account '{}' created. Remove ADMIN_PASSWORD from the environment now.", username.trim());
        } catch (Exception e) {
            log.error("Could not create the initial admin account: {}", e.getMessage());
        }
    }
}
