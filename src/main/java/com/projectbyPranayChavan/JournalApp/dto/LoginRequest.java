package com.projectbyPranayChavan.JournalApp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {

    /** Username or e-mail address. */
    @NotBlank(message = "Username is required")
    @Size(max = 254)
    private String username;

    @NotBlank(message = "Password is required")
    @Size(max = 72)
    private String password;
}
