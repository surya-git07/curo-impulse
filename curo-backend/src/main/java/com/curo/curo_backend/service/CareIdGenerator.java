package com.curo.curo_backend.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.regex.Pattern;

@Component
public class CareIdGenerator {

    // No 0/O/1/I, so a CareID read out loud or copied by hand is unambiguous
    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    static final String PREFIX = "CID-";
    static final int LENGTH = 6;

    private static final Pattern FORMAT = Pattern.compile("^CID-[A-HJ-NP-Z2-9]{6}$");

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder sb = new StringBuilder(PREFIX);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    public static boolean isCareId(String value) {
        return value != null && FORMAT.matcher(value).matches();
    }
}
