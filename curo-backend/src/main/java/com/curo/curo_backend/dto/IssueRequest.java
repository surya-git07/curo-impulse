package com.curo.curo_backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/** doctorId and hospitalId may be sent but are ignored: the doctor is whoever is signed in. */
@Data
public class IssueRequest {

    @NotBlank
    private String careId;

    private List<DrugItem> drugs;
}
