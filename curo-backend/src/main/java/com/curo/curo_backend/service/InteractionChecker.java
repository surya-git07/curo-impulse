package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.InteractionResult;

import java.util.List;

/**
 * Checks a new drug against what the patient already takes and against their recorded allergies.
 * Callers only ever see this interface. TableInteractionChecker answers from the built-in tables; an
 * implementation backed by Bedrock can replace it by setting interaction.check.mode to its own value.
 * Implementations warn only: they must never be used to block a prescription.
 */
public interface InteractionChecker {

    /**
     * @param newDrug      the drug being prescribed, as typed
     * @param currentDrugs names of the patient's other current drugs
     * @param allergies    the patient's recorded allergies, free text; may be null or empty
     * @return the worst severity found, a short warning, and an explanation of every finding
     */
    InteractionResult check(String newDrug, List<String> currentDrugs, List<String> allergies);
}
