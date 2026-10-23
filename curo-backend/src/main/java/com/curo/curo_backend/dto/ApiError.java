package com.curo.curo_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {
    private String error;
    private String message;
    // only set for GRANT_EXISTS
    private String grantId;

    public ApiError(String error, String message) {
        this(error, message, null);
    }
}
