package com.curo.curo_backend.util;

import java.util.Locale;
import java.util.regex.Pattern;

public final class LicenceNumbers {

    private static final Pattern DOCTOR = Pattern.compile("^[A-Z0-9/\\-]{4,25}$");
    private static final Pattern DRUG = Pattern.compile("^[A-Z0-9/\\-]{5,30}$");

    private LicenceNumbers() {
    }

    /** Trimmed and upper-cased; null stays null. */
    public static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean isValidDoctorNumber(String normalized) {
        return normalized != null && DOCTOR.matcher(normalized).matches();
    }

    /** A hospital licence number follows the same plain format rule as a drug licence number. */
    public static boolean isValidHospitalNumber(String normalized) {
        return isValidDrugLicenceNumber(normalized);
    }

    public static boolean isValidDrugLicenceNumber(String normalized) {
        return normalized != null && DRUG.matcher(normalized).matches();
    }
}
