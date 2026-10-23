package com.curo.curo_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class HistoryResponse {
    private String prescriptionId;
    private String careId;
    private String drug;
    private int currentVersion;
    private List<VersionView> versions;
}
