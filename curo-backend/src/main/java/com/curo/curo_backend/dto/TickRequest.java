package com.curo.curo_backend.dto;

import lombok.Data;

/** task is REMINDERS, MISSED or ALL (the default). */
@Data
public class TickRequest {
    private String task;
}
