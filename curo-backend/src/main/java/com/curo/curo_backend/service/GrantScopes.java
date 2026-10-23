package com.curo.curo_backend.service;

import com.curo.curo_backend.model.GrantScope;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class GrantScopes {

    private GrantScopes() {
    }

    /** Parses scope names (PROFILE, PRESCRIPTIONS, MEDICATIONS, DOSES); at least one is required. Duplicates are dropped. */
    static List<GrantScope> parse(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SCOPE",
                    "scope must list at least one of PROFILE, PRESCRIPTIONS, MEDICATIONS, DOSES.");
        }
        List<GrantScope> scopes = new ArrayList<>();
        for (String name : raw) {
            try {
                GrantScope scope = GrantScope.valueOf(name == null ? "" : name.trim().toUpperCase(Locale.ROOT));
                if (!scopes.contains(scope)) {
                    scopes.add(scope);
                }
            } catch (IllegalArgumentException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SCOPE",
                        "Unknown scope. Use PROFILE, PRESCRIPTIONS, MEDICATIONS or DOSES.");
            }
        }
        return scopes;
    }
}
