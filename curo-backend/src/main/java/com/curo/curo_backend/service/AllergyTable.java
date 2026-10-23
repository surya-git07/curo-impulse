package com.curo.curo_backend.service;

import com.curo.curo_backend.model.Severity;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * How recorded allergies are matched to a new drug. A direct match (the allergen is the drug, its alias or its class)
 * is always HIGH. Beyond that: other drugs of the same class are MODERATE (HIGH for penicillins), and the cross-reactions
 * below apply. Clinical content: have a clinician review it.
 */
public final class AllergyTable {

    public record CrossReaction(String allergen, String drugToken, Severity severity, String warning, String explanation) {
    }

    private AllergyTable() {
    }

    // common ways of writing an allergen, mapped to the token the rules use
    private static final Map<String, String> SYNONYMS = Map.ofEntries(
            Map.entry("penicillins", "penicillin"),
            Map.entry("penicilin", "penicillin"),
            Map.entry("sulfa", "sulfonamide"),
            Map.entry("sulpha", "sulfonamide"),
            Map.entry("sulfa drugs", "sulfonamide"),
            Map.entry("sulpha drugs", "sulfonamide"),
            Map.entry("sulphonamide", "sulfonamide"),
            Map.entry("sulphonamides", "sulfonamide"),
            Map.entry("sulfonamides", "sulfonamide"),
            Map.entry("nsaids", "nsaid"),
            Map.entry("anti-inflammatories", "nsaid"),
            Map.entry("cephalosporins", "cephalosporin"),
            Map.entry("macrolides", "macrolide"),
            Map.entry("fluoroquinolones", "fluoroquinolone"),
            Map.entry("quinolones", "fluoroquinolone"),
            Map.entry("statins", "statin"),
            Map.entry("ssris", "ssri"),
            Map.entry("ace inhibitors", "ace_inhibitor"),
            Map.entry("beta blockers", "beta_blocker"),
            Map.entry("opioids", "opioid_analgesic"));

    private static final List<CrossReaction> CROSS_REACTIONS = List.of(
            new CrossReaction("penicillin", "cephalosporin", Severity.MODERATE, "Possible cross-reaction with a penicillin allergy.",
                    "A small share of people allergic to penicillin also react to cephalosporins. Confirm the type of reaction before use."),
            new CrossReaction("aspirin", "nsaid", Severity.MODERATE, "Possible cross-reaction with an aspirin allergy.",
                    "People who react to aspirin can react to other NSAIDs."),
            new CrossReaction("nsaid", "aspirin", Severity.HIGH, "Aspirin is an NSAID.",
                    "Aspirin belongs to the same family of drugs as other NSAIDs and commonly cross-reacts."),
            new CrossReaction("salicylate", "nsaid", Severity.MODERATE, "Possible cross-reaction with a salicylate allergy.",
                    "People who react to salicylates can react to NSAIDs."));

    /** Lower case, trimmed, "allergy" wording removed, and common synonyms mapped to the rule token. */
    public static String canonical(String allergen) {
        if (allergen == null) {
            return "";
        }
        String cleaned = allergen.toLowerCase(Locale.ROOT).replaceAll("\\ballerg(y|ic|ies)\\b( to)?", " ")
                .replaceAll("\\s+", " ").trim();
        return SYNONYMS.getOrDefault(cleaned, cleaned);
    }

    public static List<CrossReaction> crossReactions() {
        return CROSS_REACTIONS;
    }
}
