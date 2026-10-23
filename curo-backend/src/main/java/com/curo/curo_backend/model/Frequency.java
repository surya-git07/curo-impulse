package com.curo.curo_backend.model;

import java.util.List;

public enum Frequency {
    ONCE_DAILY("08:00"),
    TWICE_DAILY("08:00", "20:00"),
    THREE_TIMES_DAILY("08:00", "14:00", "20:00"),
    FOUR_TIMES_DAILY("06:00", "12:00", "18:00", "22:00");

    private final List<String> defaultTimes;

    Frequency(String... defaultTimes) {
        this.defaultTimes = List.of(defaultTimes);
    }

    public List<String> defaultTimes() {
        return defaultTimes;
    }
}
