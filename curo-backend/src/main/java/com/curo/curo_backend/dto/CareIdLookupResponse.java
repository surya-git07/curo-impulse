package com.curo.curo_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CareIdLookupResponse {

    @Data
    @AllArgsConstructor
    public static class ConsentFlag {
        private boolean accepted;
    }

    private String careId;
    private String name;
    // not stored yet (the patient profile is not ported); left out of the JSON until it is
    private Integer ageYears;
    private String gender;
    private ConsentFlag consent;
}
