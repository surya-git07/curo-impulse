package com.curo.curo_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SignupRequest {

    @NotBlank
    private String role;

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotBlank
    @Pattern(regexp = "^[0-9]{10,15}$", message = "must be 10 to 15 digits")
    private String phone;

    @NotBlank
    private String password;

    private ConsentRequest consent;
}
