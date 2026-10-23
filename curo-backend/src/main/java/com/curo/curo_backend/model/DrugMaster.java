package com.curo.curo_backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/** Reference list of drugs the interaction and allergy checks understand. */
@Data
@Document("drug_master")
public class DrugMaster {

    @Id
    private String id;

    // display name, e.g. "Amoxicillin-Clavulanate"
    private String name;

    // lower case, trimmed; how lookups match
    @Indexed(unique = true)
    private String normalizedName;

    // class the rules refer to, e.g. NSAID, SSRI, ACE_INHIBITOR
    private String drugClass;

    // RX (prescription only) or OTC (usually sold over the counter). Indicative: confirm against local regulation.
    private String scheduleType;

    // other names it is known by, lower case
    @Indexed
    private List<String> aliases;
}
