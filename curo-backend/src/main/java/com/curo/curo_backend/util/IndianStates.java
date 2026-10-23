package com.curo.curo_backend.util;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** GST state codes mapped to ISO 3166-2:IN codes. 97 (other territory) and 99 (centre) are not states. */
public final class IndianStates {

    private static final Map<String, String> GST_TO_ISO = Map.ofEntries(
            Map.entry("01", "JK"), Map.entry("02", "HP"), Map.entry("03", "PB"), Map.entry("04", "CH"),
            Map.entry("05", "UT"), Map.entry("06", "HR"), Map.entry("07", "DL"), Map.entry("08", "RJ"),
            Map.entry("09", "UP"), Map.entry("10", "BR"), Map.entry("11", "SK"), Map.entry("12", "AR"),
            Map.entry("13", "NL"), Map.entry("14", "MN"), Map.entry("15", "MZ"), Map.entry("16", "TR"),
            Map.entry("17", "ML"), Map.entry("18", "AS"), Map.entry("19", "WB"), Map.entry("20", "JH"),
            Map.entry("21", "OD"), Map.entry("22", "CG"), Map.entry("23", "MP"), Map.entry("24", "GJ"),
            Map.entry("25", "DH"), Map.entry("26", "DH"), Map.entry("27", "MH"), Map.entry("28", "AP"),
            Map.entry("29", "KA"), Map.entry("30", "GA"), Map.entry("31", "LD"), Map.entry("32", "KL"),
            Map.entry("33", "TN"), Map.entry("34", "PY"), Map.entry("35", "AN"), Map.entry("36", "TG"),
            Map.entry("37", "AP"), Map.entry("38", "LA"));

    private static final Set<String> ISO_CODES = GST_TO_ISO.values().stream().collect(Collectors.toUnmodifiableSet());

    private IndianStates() {
    }

    public static Optional<String> isoForGstCode(String gstCode) {
        return Optional.ofNullable(GST_TO_ISO.get(gstCode));
    }

    public static boolean isKnownIso(String iso) {
        return iso != null && ISO_CODES.contains(iso);
    }
}
