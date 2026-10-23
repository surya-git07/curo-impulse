package com.curo.curo_backend.service;

import com.curo.curo_backend.model.DrugMaster;
import com.curo.curo_backend.repository.DrugMasterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Finds a drug by name or alias. The built-in list is checked first, so classification still works when the
 * database cannot be reached; drugs added to drug_master later are found through the repository.
 */
@Service
public class DrugCatalog {

    private static final Map<String, DrugMaster> BUILT_IN = index();

    @Autowired
    private DrugMasterRepository drugMasterRepository;

    public static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    public Optional<DrugMaster> resolve(String name) {
        String key = normalize(name);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        DrugMaster builtIn = BUILT_IN.get(key);
        if (builtIn != null) {
            return Optional.of(builtIn);
        }
        try {
            return drugMasterRepository.findByNormalizedName(key).or(() -> drugMasterRepository.findFirstByAliasesContaining(key));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Everything the rules may call this drug: its name, aliases and class, lower case. An unknown drug is just its own name. */
    public Set<String> tokens(String name, Optional<DrugMaster> drug) {
        Set<String> tokens = new HashSet<>();
        tokens.add(normalize(name));
        drug.ifPresent(d -> {
            tokens.add(d.getNormalizedName());
            if (d.getAliases() != null) {
                tokens.addAll(d.getAliases());
            }
            if (d.getDrugClass() != null) {
                tokens.add(d.getDrugClass().toLowerCase(Locale.ROOT));
            }
        });
        return tokens;
    }

    /** True if every name rules refer to is a drug, alias or class the built-in list knows. Used by tests to catch typos. */
    static Set<String> builtInTokens() {
        Set<String> all = new HashSet<>();
        for (DrugMaster d : DrugSeed.entries()) {
            all.add(d.getNormalizedName());
            all.addAll(d.getAliases());
            all.add(d.getDrugClass().toLowerCase(Locale.ROOT));
        }
        return all;
    }

    private static Map<String, DrugMaster> index() {
        Map<String, DrugMaster> map = new HashMap<>();
        for (DrugMaster d : DrugSeed.entries()) {
            map.put(d.getNormalizedName(), d);
            d.getAliases().forEach(alias -> map.put(alias, d));
        }
        return map;
    }
}
