package com.expensesplitter.service;

import com.expensesplitter.model.SplitType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SplitCalculationServiceTest {

    private SplitCalculationService service;

    @BeforeEach
    void setUp() {
        service = new SplitCalculationService();
    }

    @Test
    @DisplayName("Equal split: 100 minor units among 3 members (34, 33, 33)")
    void testEqualSplitOneHundredAmongThree() {
        List<String> members = List.of("m1", "m2", "m3");
        SplitCalculationService.SplitResult result = service.calculateSplits(100, SplitType.EQUAL, members, null);

        assertThat(result.isValid()).isTrue();
        Map<String, Long> shares = result.getSharesCents();
        assertThat(shares).containsEntry("m1", 34L);
        assertThat(shares).containsEntry("m2", 33L);
        assertThat(shares).containsEntry("m3", 33L);

        long sum = shares.values().stream().mapToLong(Long::longValue).sum();
        assertThat(sum).isEqualTo(100L);
    }

    @Test
    @DisplayName("Equal split: 1 minor unit (0.01) between 2 members (1, 0)")
    void testEqualSplitOneUnitBetweenTwo() {
        List<String> members = List.of("m1", "m2");
        SplitCalculationService.SplitResult result = service.calculateSplits(1, SplitType.EQUAL, members, null);

        assertThat(result.isValid()).isTrue();
        Map<String, Long> shares = result.getSharesCents();
        assertThat(shares).containsEntry("m1", 1L);
        assertThat(shares).containsEntry("m2", 0L);

        long sum = shares.values().stream().mapToLong(Long::longValue).sum();
        assertThat(sum).isEqualTo(1L);
    }

    @Test
    @DisplayName("Exact split: valid sum matches total")
    void testExactSplitValid() {
        List<String> members = List.of("m1", "m2");
        Map<String, BigDecimal> params = Map.of(
            "m1", new BigDecimal("45.50"),
            "m2", new BigDecimal("54.50")
        );
        SplitCalculationService.SplitResult result = service.calculateSplits(10000, SplitType.EXACT, members, params);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getShare("m1")).isEqualTo(4550L);
        assertThat(result.getShare("m2")).isEqualTo(5450L);
    }

    @Test
    @DisplayName("Exact split: invalid sum is blocked with descriptive error")
    void testExactSplitMismatch() {
        List<String> members = List.of("m1", "m2");
        Map<String, BigDecimal> params = Map.of(
            "m1", new BigDecimal("45.00"),
            "m2", new BigDecimal("50.00")
        );
        SplitCalculationService.SplitResult result = service.calculateSplits(10000, SplitType.EXACT, members, params);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("differing from total");
    }

    @Test
    @DisplayName("Percent split: 33.33%, 33.33%, 33.34% of 100 cents divides remainder evenly")
    void testPercentSplitRemainderDistribution() {
        List<String> members = List.of("m1", "m2", "m3");
        Map<String, BigDecimal> params = Map.of(
            "m1", new BigDecimal("33.33"),
            "m2", new BigDecimal("33.33"),
            "m3", new BigDecimal("33.34")
        );
        SplitCalculationService.SplitResult result = service.calculateSplits(100, SplitType.PERCENT, members, params);

        assertThat(result.isValid()).isTrue();
        long sum = result.getSharesCents().values().stream().mapToLong(Long::longValue).sum();
        assertThat(sum).isEqualTo(100L);
    }

    @Test
    @DisplayName("Percent split: total not 100% is blocked")
    void testPercentSplitNotHundred() {
        List<String> members = List.of("m1", "m2");
        Map<String, BigDecimal> params = Map.of(
            "m1", new BigDecimal("40.00"),
            "m2", new BigDecimal("50.00")
        );
        SplitCalculationService.SplitResult result = service.calculateSplits(10000, SplitType.PERCENT, members, params);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("Percentages must sum to 100.00%");
    }

    @Test
    @DisplayName("Shares split: 2 shares, 1 share, 1 share of 100 cents (50, 25, 25)")
    void testSharesSplit() {
        List<String> members = List.of("m1", "m2", "m3");
        Map<String, BigDecimal> params = Map.of(
            "m1", new BigDecimal("2"),
            "m2", new BigDecimal("1"),
            "m3", new BigDecimal("1")
        );
        SplitCalculationService.SplitResult result = service.calculateSplits(100, SplitType.SHARES, members, params);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getShare("m1")).isEqualTo(50L);
        assertThat(result.getShare("m2")).isEqualTo(25L);
        assertThat(result.getShare("m3")).isEqualTo(25L);
        long sum = result.getSharesCents().values().stream().mapToLong(Long::longValue).sum();
        assertThat(sum).isEqualTo(100L);
    }
}

