package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.InteractionResult;
import com.curo.curo_backend.model.Severity;
import com.curo.curo_backend.repository.DrugMasterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TableInteractionCheckerTest {

    private TableInteractionChecker checker;

    @BeforeEach
    void setUp() {
        DrugMasterRepository repo = mock(DrugMasterRepository.class);
        when(repo.findByNormalizedName(anyString())).thenReturn(Optional.empty());
        when(repo.findFirstByAliasesContaining(anyString())).thenReturn(Optional.empty());
        DrugCatalog catalog = new DrugCatalog();
        ReflectionTestUtils.setField(catalog, "drugMasterRepository", repo);
        checker = new TableInteractionChecker();
        ReflectionTestUtils.setField(checker, "drugCatalog", catalog);
    }

    private InteractionResult check(String drug, List<String> current, String... allergies) {
        return checker.check(drug, current, List.of(allergies));
    }

    // ---- drug with drug

    @Test
    void warfarinWithAspirinIsHigh() {
        InteractionResult r = check("Aspirin", List.of("Warfarin"));

        assertThat(r.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(r.getWarning()).contains("Aspirin with Warfarin").containsIgnoringCase("bleeding");
        assertThat(r.getExplanation()).containsIgnoringCase("bleeding");
    }

    @Test
    void theOrderDoesNotMatter() {
        InteractionResult newWarfarin = check("Warfarin", List.of("Aspirin"));

        assertThat(newWarfarin.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(newWarfarin.getWarning()).contains("Warfarin with Aspirin");
    }

    @Test
    void classRulesCatchEveryDrugInTheClass() {
        for (String nsaid : List.of("Ibuprofen", "Diclofenac", "Naproxen")) {
            assertThat(check(nsaid, List.of("Warfarin")).getSeverity()).as(nsaid).isEqualTo(Severity.HIGH);
        }
        assertThat(check("Sertraline", List.of("Tramadol")).getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(check("Fluoxetine", List.of("Tramadol")).getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(check("Alprazolam", List.of("Tramadol")).getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(check("Lisinopril", List.of("Spironolactone")).getSeverity()).isEqualTo(Severity.MODERATE);
        assertThat(check("Sildenafil", List.of("Isosorbide Mononitrate")).getSeverity()).isEqualTo(Severity.HIGH);
    }

    @Test
    void namesAreMatchedIgnoringCaseSpacingAndAliases() {
        assertThat(check("  ASPIRIN ", List.of("warfarin")).getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(check("Acetylsalicylic Acid", List.of("Warfarin")).getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(check("ASA", List.of("Warfarin")).getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(check("Paracetamol", List.of("warfarin")).getSeverity()).isEqualTo(Severity.LOW);
        assertThat(check("Acetaminophen", List.of("warfarin")).getSeverity()).isEqualTo(Severity.LOW);
    }

    @Test
    void severityMatchesTheTable() {
        assertThat(check("Clopidogrel", List.of("Aspirin")).getSeverity()).isEqualTo(Severity.MODERATE);
        assertThat(check("Atorvastatin", List.of("Clarithromycin")).getSeverity()).isEqualTo(Severity.MODERATE);
        assertThat(check("Simvastatin", List.of("Clarithromycin")).getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(check("Insulin", List.of("Atenolol")).getSeverity()).isEqualTo(Severity.MODERATE);
    }

    @Test
    void anUnrelatedPairHasNoWarning() {
        InteractionResult r = check("Paracetamol", List.of("Amlodipine", "Metformin"));

        assertThat(r.getSeverity()).isEqualTo(Severity.NONE);
        assertThat(r.getWarning()).isNull();
        assertThat(r.getExplanation()).contains("No interaction or allergy conflict");
    }

    @Test
    void noCurrentDrugsAndNoAllergiesIsNone() {
        assertThat(check("Warfarin", List.of()).getSeverity()).isEqualTo(Severity.NONE);
        assertThat(checker.check("Warfarin", null, null).getSeverity()).isEqualTo(Severity.NONE);
    }

    @Test
    void theWorstFindingWinsAndEveryFindingIsExplained() {
        // new warfarin against aspirin (HIGH), paracetamol (LOW) and metronidazole (HIGH)
        InteractionResult r = check("Warfarin", List.of("Paracetamol", "Aspirin", "Metformin"));

        assertThat(r.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(r.getWarning()).contains("Warfarin with Aspirin").contains("1 more");
        assertThat(r.getExplanation()).contains("Aspirin").contains("Paracetamol");
        assertThat(r.getExplanation().lines().count()).isEqualTo(2);
    }

    @Test
    void repeatingADrugThePatientAlreadyTakesIsFlagged() {
        InteractionResult r = check("Ibuprofen", List.of("ibuprofen"));

        assertThat(r.getSeverity()).isEqualTo(Severity.MODERATE);
        assertThat(r.getWarning()).contains("already prescribed");
    }

    @Test
    void aDrugAndItsAliasCountAsTheSameDrug() {
        assertThat(check("Aspirin", List.of("Acetylsalicylic acid")).getWarning()).contains("already prescribed");
    }

    // ---- allergies

    @Test
    void penicillinAllergyWithAmoxicillinIsHigh() {
        InteractionResult r = check("Amoxicillin", List.of(), "Penicillin");

        assertThat(r.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(r.getWarning()).contains("Recorded allergy").contains("Penicillin");
        assertThat(r.getExplanation()).contains("recorded allergy to Penicillin");
    }

    @Test
    void anAllergyToTheDrugItselfIsHigh() {
        assertThat(check("Ibuprofen", List.of(), "ibuprofen").getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(check("Amoxicillin", List.of(), "Amoxicillin allergy").getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(check("Ibuprofen", List.of(), "Allergic to NSAIDs").getSeverity()).isEqualTo(Severity.HIGH);
    }

    @Test
    void commonWaysOfWritingAnAllergyAreUnderstood() {
        assertThat(check("Cotrimoxazole", List.of(), "Sulfa").getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(check("Cotrimoxazole", List.of(), "sulpha drugs").getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(check("Amoxicillin", List.of(), "Penicillins").getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(check("Naproxen", List.of(), "NSAIDs").getSeverity()).isEqualTo(Severity.HIGH);
    }

    @Test
    void anAllergyToOnePenicillinCoversTheOthers() {
        InteractionResult r = check("Amoxicillin-Clavulanate", List.of(), "Amoxicillin");

        assertThat(r.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(r.getWarning()).contains("same class");
    }

    @Test
    void sameClassOutsidePenicillinsIsModerate() {
        assertThat(check("Naproxen", List.of(), "Ibuprofen").getSeverity()).isEqualTo(Severity.MODERATE);
    }

    @Test
    void crossReactionsBetweenFamiliesAreFlagged() {
        assertThat(check("Cefixime", List.of(), "Penicillin").getSeverity()).isEqualTo(Severity.MODERATE);
        assertThat(check("Aspirin", List.of(), "NSAIDs").getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(check("Ibuprofen", List.of(), "Aspirin").getSeverity()).isEqualTo(Severity.MODERATE);
    }

    @Test
    void anUnrelatedAllergyDoesNotWarn() {
        assertThat(check("Paracetamol", List.of(), "Penicillin", "Peanuts", "Latex").getSeverity()).isEqualTo(Severity.NONE);
    }

    @Test
    void blankAllergyEntriesAreIgnored() {
        assertThat(checker.check("Paracetamol", List.of(), java.util.Arrays.asList("", "  ", "allergy")).getSeverity())
                .isEqualTo(Severity.NONE);
    }

    @Test
    void allergyAndInteractionTogetherAreBothReported() {
        InteractionResult r = check("Ibuprofen", List.of("Warfarin"), "Ibuprofen");

        assertThat(r.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(r.getExplanation()).contains("recorded allergy").contains("Warfarin");
        assertThat(r.getExplanation().lines().count()).isEqualTo(2);
    }

    // ---- not recognised

    @Test
    void aDrugOutsideTheListIsUnknownNotNone() {
        InteractionResult r = check("Zorbix", List.of("Warfarin"));

        assertThat(r.getSeverity()).isEqualTo(Severity.UNKNOWN);
        assertThat(r.getWarning()).contains("Zorbix").contains("could not be checked");
    }

    @Test
    void anAllergyMatchOnAnUnrecognisedDrugStillWarns() {
        assertThat(check("Zorbix", List.of(), "zorbix").getSeverity()).isEqualTo(Severity.HIGH);
    }

    @Test
    void aCurrentDrugOutsideTheListIsSimplyNotCompared() {
        assertThat(check("Aspirin", List.of("Zorbix")).getSeverity()).isEqualTo(Severity.NONE);
    }

    @Test
    void theDatabaseBeingDownDoesNotBreakTheBuiltInList() {
        DrugMasterRepository down = mock(DrugMasterRepository.class);
        when(down.findByNormalizedName(anyString())).thenThrow(new DataAccessResourceFailureException("down"));
        DrugCatalog catalog = new DrugCatalog();
        ReflectionTestUtils.setField(catalog, "drugMasterRepository", down);
        ReflectionTestUtils.setField(checker, "drugCatalog", catalog);

        assertThat(check("Aspirin", List.of("Warfarin")).getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(check("Zorbix", List.of()).getSeverity()).isEqualTo(Severity.UNKNOWN);
    }

    @Test
    void theResultHasNoBlockingField() {
        assertThat(InteractionResult.class.getDeclaredFields()).extracting(java.lang.reflect.Field::getName)
                .containsExactlyInAnyOrder("severity", "warning", "explanation");
    }
}
