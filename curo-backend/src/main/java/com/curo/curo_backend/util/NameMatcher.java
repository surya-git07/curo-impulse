package com.curo.curo_backend.util;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Compares names ignoring case, accents, punctuation, spacing and leading titles such as "Dr". */
public final class NameMatcher {

    private static final Set<String> TITLES = Set.of("dr", "mr", "mrs", "ms", "miss", "prof");

    private NameMatcher() {
    }

    public static String normalize(String name) {
        if (name == null) {
            return "";
        }
        String plain = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String spaced = plain.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        if (spaced.isEmpty()) {
            return "";
        }
        List<String> words = Arrays.asList(spaced.split(" "));
        int start = 0;
        while (start < words.size() - 1 && TITLES.contains(words.get(start))) {
            start++;
        }
        return String.join(" ", words.subList(start, words.size()));
    }

    public static boolean matches(String a, String b) {
        String na = normalize(a);
        return !na.isEmpty() && na.equals(normalize(b));
    }
}
