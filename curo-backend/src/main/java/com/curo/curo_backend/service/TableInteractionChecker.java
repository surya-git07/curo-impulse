package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.InteractionResult;
import com.curo.curo_backend.model.DrugMaster;
import com.curo.curo_backend.model.Severity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** The built-in interaction check: InteractionTable for drug pairs, AllergyTable for allergies. Active when interaction.check.mode=table. */
@Service
@ConditionalOnProperty(name = "interaction.check.mode", havingValue = "table", matchIfMissing = true)
public class TableInteractionChecker implements InteractionChecker {

    private record Finding(Severity severity, String warning, String explanation) {
    }

    @Autowired
    private DrugCatalog drugCatalog;

    @Override
    public InteractionResult check(String newDrug, List<String> currentDrugs, List<String> allergies) {
        Optional<DrugMaster> resolved = drugCatalog.resolve(newDrug);
        Set<String> newTokens = drugCatalog.tokens(newDrug, resolved);
        String newName = resolved.map(DrugMaster::getName).orElse(newDrug.trim());

        List<Finding> findings = new ArrayList<>();
        if (currentDrugs != null) {
            for (String current : currentDrugs) {
                interactionFindings(newName, newTokens, resolved, current, findings);
            }
        }
        if (allergies != null) {
            for (String allergy : allergies) {
                allergyFindings(newName, newTokens, resolved, allergy, findings);
            }
        }

        if (findings.isEmpty()) {
            if (resolved.isEmpty()) {
                return new InteractionResult(Severity.UNKNOWN,
                        newName + " is not in the reference drug list, so it could not be checked.",
                        "No rule applied to " + newName + ", but it is not a drug the built-in list recognises. Check interactions and allergies manually.");
            }
            return new InteractionResult(Severity.NONE, null,
                    "No interaction or allergy conflict found in the built-in reference table.");
        }

        findings.sort(Comparator.comparing(Finding::severity, (a, b) -> a.isWorseThan(b) ? -1 : b.isWorseThan(a) ? 1 : 0));
        Finding worst = findings.get(0);
        String warning = findings.size() == 1 ? worst.warning()
                : worst.warning() + " (" + (findings.size() - 1) + " more, see explanation)";
        StringBuilder explanation = new StringBuilder();
        for (Finding f : findings) {
            if (explanation.length() > 0) {
                explanation.append('\n');
            }
            explanation.append("- ").append(f.explanation());
        }
        return new InteractionResult(worst.severity(), warning, explanation.toString());
    }

    private void interactionFindings(String newName, Set<String> newTokens, Optional<DrugMaster> newDrug, String current,
                                     List<Finding> findings) {
        Optional<DrugMaster> currentDrug = drugCatalog.resolve(current);
        Set<String> currentTokens = drugCatalog.tokens(current, currentDrug);
        String currentName = currentDrug.map(DrugMaster::getName).orElse(current.trim());

        if (sameDrug(newName, newDrug, currentName, currentDrug)) {
            findings.add(new Finding(Severity.MODERATE, newName + " is already prescribed.",
                    "The patient already has a current prescription for " + currentName + ". Check the repeat is intended."));
            return;
        }
        for (InteractionTable.Rule rule : InteractionTable.rules()) {
            boolean applies = newTokens.contains(rule.a()) && currentTokens.contains(rule.b())
                    || newTokens.contains(rule.b()) && currentTokens.contains(rule.a());
            if (applies) {
                findings.add(new Finding(rule.severity(), newName + " with " + currentName + ": " + rule.warning(),
                        newName + " with " + currentName + ": " + rule.explanation()));
            }
        }
    }

    private void allergyFindings(String newName, Set<String> newTokens, Optional<DrugMaster> newDrug, String recorded,
                                 List<Finding> findings) {
        String allergen = AllergyTable.canonical(recorded);
        if (allergen.isEmpty()) {
            return;
        }
        String shown = recorded.trim();

        // 1. the allergen is this drug, one of its names, or its class
        if (newTokens.contains(allergen)) {
            findings.add(new Finding(Severity.HIGH, "Recorded allergy: " + shown + ".",
                    "The patient has a recorded allergy to " + shown + ", which matches " + newName + " (same drug or drug class)."));
            return;
        }

        Optional<DrugMaster> allergenDrug = drugCatalog.resolve(allergen);

        // 2. the allergen is a specific drug and this is another drug of the same class
        if (allergenDrug.isPresent() && newDrug.isPresent()
                && allergenDrug.get().getDrugClass().equalsIgnoreCase(newDrug.get().getDrugClass())) {
            boolean penicillins = "PENICILLIN".equalsIgnoreCase(newDrug.get().getDrugClass());
            findings.add(new Finding(penicillins ? Severity.HIGH : Severity.MODERATE,
                    "Recorded allergy to " + shown + " (same class).",
                    "The patient is allergic to " + shown + ", and " + newName + " is in the same drug class ("
                            + newDrug.get().getDrugClass().toLowerCase(Locale.ROOT).replace('_', ' ') + ")."
                            + (penicillins ? " Penicillins cross-react strongly." : " Cross-reaction is possible.")));
            return;
        }

        // 3. known cross-reactions between families
        String allergenClass = allergenDrug.map(d -> d.getDrugClass().toLowerCase(Locale.ROOT)).orElse(null);
        for (AllergyTable.CrossReaction cr : AllergyTable.crossReactions()) {
            boolean allergenMatches = cr.allergen().equals(allergen) || cr.allergen().equals(allergenClass);
            if (allergenMatches && newTokens.contains(cr.drugToken())) {
                findings.add(new Finding(cr.severity(), cr.warning() + " (recorded allergy: " + shown + ")",
                        newName + ": " + cr.explanation() + " The patient has a recorded allergy to " + shown + "."));
                return;
            }
        }
    }

    private boolean sameDrug(String nameA, Optional<DrugMaster> a, String nameB, Optional<DrugMaster> b) {
        if (a.isPresent() && b.isPresent()) {
            return a.get().getNormalizedName().equals(b.get().getNormalizedName());
        }
        return DrugCatalog.normalize(nameA).equals(DrugCatalog.normalize(nameB));
    }
}
