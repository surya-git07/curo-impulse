package com.curo.curo_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TickResponse {
    // reminders and alerts are not built yet, so these stay 0
    private long remindersSent;
    private long markedMissed;
    private long alertsCreated;
}
