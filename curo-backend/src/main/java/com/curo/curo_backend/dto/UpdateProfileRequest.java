package com.curo.curo_backend.dto;

import lombok.Data;

import java.util.List;

/** Only allergies can be changed so far; the other profile fields in the contract are not stored yet. */
@Data
public class UpdateProfileRequest {
    private List<String> allergies;
}
