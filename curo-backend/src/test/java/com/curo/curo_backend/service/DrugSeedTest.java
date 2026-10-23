package com.curo.curo_backend.service;

import com.curo.curo_backend.model.DrugMaster;
import com.curo.curo_backend.model.Severity;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class DrugSeedTest {

    private final List<DrugMaster> drugs = DrugSeed.entries();

    @Test
    void hasBetweenThirtyAndFiftyDrugs() {
        assertThat(drugs.size()).isBetween(30, 50);
    }

    @Test
    void everyDrugHasANameClassAndSchedule() {
        for (DrugMaster d : drugs) {
            assertThat(d.getName()).as("name").isNotBlank();
            assertThat(d.getNormalizedName()).as(d.getName()).isEqualTo(d.getName().toLowerCase());
            assertThat(d.getDrugClass()).as(d.getName() + " class").matches("^[A-Z][A-Z0-9_]*$");
            assertThat(d.getScheduleType()).as(d.getName() + " schedule").isIn("RX", "OTC");
            assertThat(d.getAliases()).isNotNull();
        }
    }

    @Test
    void namesAndAliasesAreUniqueAcrossTheList() {
        Set<String> seen = new HashSet<>();
        for (DrugMaster d : drugs) {
            assertThat(seen.add(d.getNormalizedName())).as("duplicate name " + d.getName()).isTrue();
            for (String alias : d.getAliases()) {
                assertThat(seen.add(alias)).as("duplicate alias " + alias).isTrue();
                assertThat(alias).isEqualTo(alias.toLowerCase());
            }
        }
    }

    @Test
    void includesTheHeadlineDrugs() {
        Set<String> names = drugs.stream().map(DrugMaster::getNormalizedName).collect(Collectors.toSet());
        assertThat(names).contains("warfarin", "aspirin", "ibuprofen", "amoxicillin", "paracetamol", "metformin", "insulin");
    }

    @Test
    void entriesAreFreshObjectsEachCall() {
        assertThat(DrugSeed.entries()).isNotSameAs(DrugSeed.entries());
    }

    // ---- the tables must only talk about things the drug list knows, or a typo silently disables a rule

    @Test
    void everyInteractionRuleSideIsADrugAliasOrClassInTheList() {
        Set<String> known = DrugCatalog.builtInTokens();
        for (InteractionTable.Rule rule : InteractionTable.rules()) {
            assertThat(known).as("side a of " + rule).contains(rule.a());
            assertThat(known).as("side b of " + rule).contains(rule.b());
        }
    }

    @Test
    void everyCrossReactionTokenIsKnown() {
        Set<String> known = DrugCatalog.builtInTokens();
        for (AllergyTable.CrossReaction c : AllergyTable.crossReactions()) {
            assertThat(known).as(c.toString()).contains(c.allergen(), c.drugToken());
        }
    }

    @Test
    void everyRuleHasASeverityAndWords() {
        for (InteractionTable.Rule rule : InteractionTable.rules()) {
            assertThat(rule.severity()).isIn(Severity.LOW, Severity.MODERATE, Severity.HIGH);
            assertThat(rule.warning()).isNotBlank();
            assertThat(rule.explanation()).isNotBlank();
            assertThat(rule.a()).isNotEqualTo(rule.b());
        }
    }

    @Test
    void noPairIsListedTwice() {
        Set<String> pairs = new HashSet<>();
        for (InteractionTable.Rule rule : InteractionTable.rules()) {
            String key = rule.a().compareTo(rule.b()) < 0 ? rule.a() + "|" + rule.b() : rule.b() + "|" + rule.a();
            assertThat(pairs.add(key)).as("duplicate pair " + key).isTrue();
        }
    }

    @Test
    void warfarinWithAspirinIsInTheTableAsHigh() {
        assertThat(InteractionTable.rules()).anySatisfy(r -> {
            assertThat(Set.of(r.a(), r.b())).containsExactlyInAnyOrder("warfarin", "aspirin");
            assertThat(r.severity()).isEqualTo(Severity.HIGH);
        });
    }

    @Test
    void allergyWordingIsNormalised() {
        assertThat(AllergyTable.canonical("Penicillin allergy")).isEqualTo("penicillin");
        assertThat(AllergyTable.canonical("  Allergic to Sulfa ")).isEqualTo("sulfonamide");
        assertThat(AllergyTable.canonical("NSAIDs")).isEqualTo("nsaid");
        assertThat(AllergyTable.canonical("Penicillins")).isEqualTo("penicillin");
        assertThat(AllergyTable.canonical("Amoxicillin")).isEqualTo("amoxicillin");
        assertThat(AllergyTable.canonical(null)).isEmpty();
    }
}
