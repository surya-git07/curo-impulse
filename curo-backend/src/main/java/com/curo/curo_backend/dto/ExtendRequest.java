package com.curo.curo_backend.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** Send exactly one of extraDays and newEndDate. */
@Data
public class ExtendRequest {
    // days added to the current end date (1 to 365)
    private Integer extraDays;
    // the new last day of the course, yyyy-MM-dd; the only option when the prescription has no end date yet
    private String newEndDate;
    // optional note for the history
    @Size(max = 500)
    private String reason;
}
