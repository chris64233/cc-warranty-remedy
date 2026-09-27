package com.chris64233.warrantyremedy.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 同一故障判定规则的单元测试：空白/大小写归一、产品隔离、键定长。
 */
class DedupeKeyTest {

    @Test
    void whitespaceAndCaseDifferencesRepresentSameFault() {
        assertThat(WarrantyClaimService.activeDedupeKey(7L, WarrantyClaimService.normalizeSymptom("  屏幕 不亮 ")))
                .isEqualTo(WarrantyClaimService.activeDedupeKey(7L,
                        WarrantyClaimService.normalizeSymptom("屏幕 不亮")))
                .isEqualTo(WarrantyClaimService.activeDedupeKey(7L,
                        WarrantyClaimService.normalizeSymptom("屏幕  不亮  ")));
    }

    @Test
    void differentProductsOrDifferentSymptomsProduceDifferentKeys() {
        String key = WarrantyClaimService.activeDedupeKey(1L, "故障");
        assertThat(key).isNotEqualTo(WarrantyClaimService.activeDedupeKey(2L, "故障"));
        assertThat(key).isNotEqualTo(WarrantyClaimService.activeDedupeKey(1L, "别的故障"));
    }

    @Test
    void keyIsFixedLengthSha256HexEvenForLongSymptom() {
        String longSymptom = "故".repeat(512);
        String key = WarrantyClaimService.activeDedupeKey(1L, longSymptom);
        assertThat(key).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void normalizeCompressesWhitespaceAndLowercases() {
        assertThat(WarrantyClaimService.normalizeSymptom("  ABC   def ")).isEqualTo("abc def");
        assertThat(WarrantyClaimService.normalizeSymptom("\t屏幕\n  不亮 ")).isEqualTo("屏幕 不亮");
    }
}
