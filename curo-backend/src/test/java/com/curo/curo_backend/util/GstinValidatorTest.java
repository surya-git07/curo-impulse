package com.curo.curo_backend.util;

import com.curo.curo_backend.util.GstinValidator.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class GstinValidatorTest {

    // ---- valid

    @ParameterizedTest
    @CsvSource({
            "33AAGFS4821K1Z5, TN",
            "29AAGCN7731L1ZW, KA",
            "27AAHFS3052D1ZF, MH",
            "32AAIFP1876M1ZV, KL",
            "07AAJCY5102G1ZE, DL",
            // widely published sample GSTIN, used to check the algorithm against an outside reference
            "27AAPFU0939F1ZV, MH",
            // two GST codes map to Andhra Pradesh and to Dadra/Daman
            "28AAGFS4821K1ZW, AP",
            "37AAGFS4821K1ZX, AP",
            "25AAGFS4821K1Z2, DH",
            "26AAGFS4821K1Z0, DH",
            "05AAGFS4821K1Z4, UT",
            "38AAGFS4821K1ZV, LA"
    })
    void acceptsValidGstinsForTheirState(String gstin, String state) {
        assertThat(GstinValidator.validate(gstin, state)).isEqualTo(Result.VALID);
    }

    @Test
    void ignoresCaseAndSurroundingSpaces() {
        assertThat(GstinValidator.validate("  33aagfs4821k1z5 ", " tn ")).isEqualTo(Result.VALID);
    }

    // ---- format

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "33AAGFS4821K1Z",        // 14 characters
            "33AAGFS4821K1Z55",      // 16 characters
            "3AAGFS4821K1Z5",        // state code is not two digits
            "33AAGFS48211K1Z5",      // PAN digits misplaced
            "33AAGF54821K1Z5",       // PAN has only four letters
            "33AAGFS4821K0Z5",       // entity number 0 is not allowed
            "33AAGFS4821K1X5",       // 14th character must be Z
            "33AAGFS4821K1Z!",       // bad character
            "33AAGFS4821K 1Z5"       // inner space
    })
    void rejectsBadFormat(String gstin) {
        assertThat(GstinValidator.validate(gstin, "TN")).isEqualTo(Result.INVALID_FORMAT);
    }

    @Test
    void nullIsBadFormat() {
        assertThat(GstinValidator.validate(null, "TN")).isEqualTo(Result.INVALID_FORMAT);
    }

    // ---- state code

    @ParameterizedTest
    @ValueSource(strings = {
            "00AAGFS4821K1ZE",
            "39AAGFS4821K1ZT",
            "97AAGFS4821K1ZR",       // other territory: not a state
            "99AAGFS4821K1ZN"        // centre jurisdiction: not a state
    })
    void rejectsUnknownStateCodesEvenWithACorrectCheckCharacter(String gstin) {
        assertThat(GstinValidator.validate(gstin, "TN")).isEqualTo(Result.UNKNOWN_STATE_CODE);
    }

    @Test
    void rejectsAGstinFromAnotherState() {
        assertThat(GstinValidator.validate("33AAGFS4821K1Z5", "KA")).isEqualTo(Result.STATE_MISMATCH);
        assertThat(GstinValidator.validate("29AAGCN7731L1ZW", "TN")).isEqualTo(Result.STATE_MISMATCH);
    }

    @Test
    void missingStateIsAMismatch() {
        assertThat(GstinValidator.validate("33AAGFS4821K1Z5", null)).isEqualTo(Result.STATE_MISMATCH);
        assertThat(GstinValidator.validate("33AAGFS4821K1Z5", "")).isEqualTo(Result.STATE_MISMATCH);
    }

    // ---- check character

    @Test
    void rejectsAWrongCheckCharacter() {
        assertThat(GstinValidator.validate("33AAGFS4821K1Z6", "TN")).isEqualTo(Result.INVALID_CHECK_CHARACTER);
        assertThat(GstinValidator.validate("33AAGFS4821K1ZA", "TN")).isEqualTo(Result.INVALID_CHECK_CHARACTER);
        assertThat(GstinValidator.validate("27AAPFU0939F1ZX", "MH")).isEqualTo(Result.INVALID_CHECK_CHARACTER);
    }

    @Test
    void aSingleChangedCharacterIsCaught() {
        String valid = "33AAGFS4821K1Z5";
        for (int i = 2; i < 14; i++) {
            char original = valid.charAt(i);
            char changed = original == 'A' ? 'B' : (Character.isDigit(original) ? (original == '9' ? '8' : (char) (original + 1)) : 'A');
            String tampered = valid.substring(0, i) + changed + valid.substring(i + 1);
            // whatever else it breaks, it must never pass
            assertThat(GstinValidator.validate(tampered, "TN")).as(tampered).isNotEqualTo(Result.VALID);
        }
    }

    @Test
    void checkCharacterMatchesTheKnownReferenceAndTheSeedExamples() {
        assertThat(GstinValidator.checkCharacter("27AAPFU0939F1Z")).isEqualTo('V');
        assertThat(GstinValidator.checkCharacter("33AAGFS4821K1Z")).isEqualTo('5');
        assertThat(GstinValidator.checkCharacter("29AAGCN7731L1Z")).isEqualTo('W');
    }

    @Test
    void theOldContractSampleWasInvalid() {
        // 33AAAAA0000A1Z5 appeared in contract v0.2; the correct check character is 9
        assertThat(GstinValidator.validate("33AAAAA0000A1Z5", "TN")).isEqualTo(Result.INVALID_CHECK_CHARACTER);
        assertThat(GstinValidator.validate("33AAAAA0000A1Z9", "TN")).isEqualTo(Result.VALID);
    }
}
