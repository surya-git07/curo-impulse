package com.curo.curo_backend.util;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Structural GSTIN validation: 15-character format, a known state code that matches the state entered,
 * and the standard check character. It does not prove the GSTIN is actually registered.
 */
public final class GstinValidator {

    public enum Result {
        VALID,
        INVALID_FORMAT,
        UNKNOWN_STATE_CODE,
        STATE_MISMATCH,
        INVALID_CHECK_CHARACTER
    }

    private static final Pattern FORMAT = Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");
    private static final String CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private GstinValidator() {
    }

    /** Trimmed and upper-cased; null stays null. */
    public static String normalize(String gstin) {
        return gstin == null ? null : gstin.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * @param gstin    the GSTIN as entered
     * @param isoState ISO 3166-2:IN code the applicant entered, e.g. "TN"
     */
    public static Result validate(String gstin, String isoState) {
        String g = normalize(gstin);
        if (g == null || !FORMAT.matcher(g).matches()) {
            return Result.INVALID_FORMAT;
        }
        Optional<String> stateOfGstin = IndianStates.isoForGstCode(g.substring(0, 2));
        if (stateOfGstin.isEmpty()) {
            return Result.UNKNOWN_STATE_CODE;
        }
        if (isoState == null || !stateOfGstin.get().equals(isoState.trim().toUpperCase(Locale.ROOT))) {
            return Result.STATE_MISMATCH;
        }
        if (checkCharacter(g.substring(0, 14)) != g.charAt(14)) {
            return Result.INVALID_CHECK_CHARACTER;
        }
        return Result.VALID;
    }

    /** The 15th character for the first 14: base-36 weighted checksum (weights 1, 2, 1, 2, ...). */
    public static char checkCharacter(String first14) {
        int sum = 0;
        for (int i = 0; i < first14.length(); i++) {
            int product = CHARS.indexOf(first14.charAt(i)) * (i % 2 == 0 ? 1 : 2);
            sum += product / 36 + product % 36;
        }
        return CHARS.charAt((36 - sum % 36) % 36);
    }
}
