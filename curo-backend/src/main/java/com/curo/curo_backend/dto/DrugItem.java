package com.curo.curo_backend.dto;

import lombok.Data;

import java.util.List;

/** One drug in an issue request, or the fields of a change. Which fields are required depends on the call. */
@Data
public class DrugItem {
    private String drug;
    private String dose;
    // free text such as "7 days" or "2 weeks"
    private String duration;
    private Integer durationDays;
    // ONCE_DAILY, TWICE_DAILY, THREE_TIMES_DAILY, FOUR_TIMES_DAILY
    private String frequency;
    // HH:mm, one per dose; overrides the default times for the frequency
    private List<String> times;
}
