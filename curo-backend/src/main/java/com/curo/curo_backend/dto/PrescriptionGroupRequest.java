package com.curo.curo_backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class PrescriptionGroupRequest {
    private String patientAbha;
    private Integer doctorId;
    private Integer hospitalId;
    private List<DrugItem> drugs;

    @Data
    public static class DrugItem {
        private String drug;
        private String dose;
        private String duration;
    }
}