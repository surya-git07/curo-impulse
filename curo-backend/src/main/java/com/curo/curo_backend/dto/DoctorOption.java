package com.curo.curo_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** What the front desk may know about a doctor: name and ids only. No phone, licence or verification details. */
@Data
@AllArgsConstructor
public class DoctorOption {
    private String id;
    private String careId;
    private String name;
}
