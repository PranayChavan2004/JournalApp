package com.projectbyPranayChavan.JournalApp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Sign-up payload (also used to create admins). */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserDTO {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 30, message = "Username must be 3 to 30 characters")
    @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "Username may only contain letters, numbers, dot, dash and underscore")
    @Schema(description = "The user's username")
    private String userName;

    @Email(message = "Enter a valid e-mail address")
    @Size(max = 254, message = "E-mail is too long")
    private String email;

    private boolean sentimentAnalysis;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
    private String password;
}
