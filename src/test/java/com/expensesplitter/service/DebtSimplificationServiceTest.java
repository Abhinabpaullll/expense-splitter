package com.expensesplitter.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DebtSimplificationServiceTest {

    private DebtSimplificationService service;

    @BeforeEach
    void setUp() {
        service = new DebtSimplificationService();
    }

    @Test
    @DisplayName("Zero net balances produces zero transfers")
    void testZeroNetBalances() {
        Map<String, Long> net = Map.of(
            "m1", 0L,
            "m2", 0L,
            "m3", 0L
        );
        List<DebtSimplificationService.Transfer> transfers = service.simplifyDebts(net);
        assertThat(transfers).isEmpty();
    }

    @Test
    @DisplayName("Simple 2-party settlement")
    void testTwoPartySettlement() {
        Map<String, Long> net = Map.of(
            "Asha", 5000L,
            "Ravi", -5000L
        );
        List<DebtSimplificationService.Transfer> transfers = service.simplifyDebts(net);
        assertThat(transfers).hasSize(1);
        assertThat(transfers.get(0).getFromMemberId()).isEqualTo("Ravi");
        assertThat(transfers.get(0).getToMemberId()).isEqualTo("Asha");
        assertThat(transfers.get(0).getAmountCents()).isEqualTo(5000L);
    }

    @Test
    @DisplayName("4-party multilateral settlement compresses to minimal transfers and ends at zero")
    void testMultilateralSimplificationEndsAtZero() {
        // Balances: Asha +1400, Meera +600, Ravi -800, Karan -1200
        // Sum = +2000 -2000 = 0
        Map<String, Long> net = Map.of(
            "Asha", 140000L,
            "Meera", 60000L,
            "Ravi", -80000L,
            "Karan", -120000L
        );

        List<DebtSimplificationService.Transfer> transfers = service.simplifyDebts(net);

        // N=4 members -> at most N-1 = 3 transfers
        assertThat(transfers.size()).isLessThanOrEqualTo(3);

        // Verify all balances resolve exactly to zero after applying transfers
        Map<String, Long> simulation = new HashMap<>(net);
        for (DebtSimplificationService.Transfer t : transfers) {
            // Debtor pays -> balance increases toward zero
            simulation.put(t.getFromMemberId(), simulation.get(t.getFromMemberId()) + t.getAmountCents());
            // Creditor receives -> balance decreases toward zero
            simulation.put(t.getToMemberId(), simulation.get(t.getToMemberId()) - t.getAmountCents());
        }

        for (Map.Entry<String, Long> entry : simulation.entrySet()) {
            assertThat(entry.getValue())
                .as("Member %s balance must end at zero", entry.getKey())
                .isZero();
        }
    }

    @Test
    @DisplayName("Discrepant balances fail validation")
    void testNonZeroSumDiscrepancyFails() {
        Map<String, Long> invalid = Map.of(
            "Asha", 1000L,
            "Ravi", -800L
        );
        assertThatThrownBy(() -> service.simplifyDebts(invalid))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Net balances sum must be zero");
    }
}

