package com.curo.curo_backend.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CareIdGeneratorTest {

    private final CareIdGenerator generator = new CareIdGenerator();

    @Test
    void generatesPrefixPlusSixCharsFromTheAllowedAlphabet() {
        for (int i = 0; i < 5000; i++) {
            String id = generator.generate();
            assertThat(id).matches("^CID-[A-HJ-NP-Z2-9]{6}$");
            assertThat(CareIdGenerator.isCareId(id)).isTrue();
        }
    }

    @Test
    void neverUsesAmbiguousCharacters() {
        String all = String.join("", java.util.stream.Stream.generate(generator::generate).limit(5000).toList())
                .replace("CID-", "");
        assertThat(all).doesNotContain("0", "O", "1", "I");
    }

    @Test
    void alphabetIsExactlyTheContractAlphabet() {
        assertThat(CareIdGenerator.ALPHABET).isEqualTo("ABCDEFGHJKLMNPQRSTUVWXYZ23456789");
    }

    @Test
    void valuesAreVariedNotConstant() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            seen.add(generator.generate());
        }
        assertThat(seen.size()).isGreaterThan(1990);
    }

    @Test
    void isCareIdRejectsBadShapes() {
        assertThat(CareIdGenerator.isCareId(null)).isFalse();
        assertThat(CareIdGenerator.isCareId("CID-ABC12")).isFalse();
        assertThat(CareIdGenerator.isCareId("CID-ABC1234")).isFalse();
        assertThat(CareIdGenerator.isCareId("CID-ABCO12")).isFalse();
        assertThat(CareIdGenerator.isCareId("cid-ABC234")).isFalse();
        assertThat(CareIdGenerator.isCareId("9000000001")).isFalse();
    }
}
