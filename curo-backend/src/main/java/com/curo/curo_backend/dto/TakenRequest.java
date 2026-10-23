package com.curo.curo_backend.dto;

import lombok.Data;

import java.time.Instant;

/** Only takenAt can be sent. There is no way to send a status: only the server decides MISSED. */
@Data
public class TakenRequest {
    private Instant takenAt;
}
