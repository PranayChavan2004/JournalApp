package com.projectbyPranayChavan.JournalApp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Every field is optional; only the ones that are sent get changed. */
@Data
public class UpdateUserRequest {

    @Size(min = 3, max = 30, message = "Username must be 3 to 30 characters")
    @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "Username may only contain letters, numbers, dot, dash and underscore")
    private String username;

    @Email(message = "Enter a valid e-mail address")
    @Size(max = 254, message = "E-mail is too long")
    private String email;

    private Boolean sentimentAnalysis;

    /** Required whenever newPassword is set. */
    @Size(max = 72)
    private String currentPassword;

    @Size(min = 8, max = 72, message = "New password must be 8 to 72 characters")
    private String newPassword;
}
