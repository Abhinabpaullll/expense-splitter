package com.expensesplitter.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Debt Simplification Engine using greedy min-cash-flow algorithm.
 * Compresses multilateral debt webs into the minimal possible number of transactions.
 * Guarantees that all net balances resolve exactly to zero.
 */
public class DebtSimplificationService {

    public static class Transfer {
        private final String fromMemberId;
        private final String toMemberId;
        private final long amountCents;

        public Transfer(String fromMemberId, String toMemberId, long amountCents) {
            this.fromMemberId = fromMemberId;
            this.toMemberId = toMemberId;
            this.amountCents = amountCents;
        }

        public String getFromMemberId() {
            return fromMemberId;
        }

        public String getToMemberId() {
            return toMemberId;
        }

        public long getAmountCents() {
            return amountCents;
        }

        public BigDecimal getAmountDecimal() {
            return BigDecimal.valueOf(amountCents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }

        @Override
        public String toString() {
            return fromMemberId + " pays " + toMemberId + ": " + getAmountDecimal();
        }
    }

    private static class BalanceNode {
        final String memberId;
        long amountCents;

        BalanceNode(String memberId, long amountCents) {
            this.memberId = memberId;
            this.amountCents = amountCents;
        }
    }

    public List<Transfer> simplifyDebts(Map<String, Long> netBalances) {
        if (netBalances == null || netBalances.isEmpty()) {
            return Collections.emptyList();
        }

        // Verify balance invariant: sum of net balances must be 0
        long balanceCheck = 0;
        for (long b : netBalances.values()) {
            balanceCheck += b;
        }
        if (balanceCheck != 0) {
            throw new IllegalArgumentException("Net balances sum must be zero. Current discrepancy: " + balanceCheck);
        }

        // Creditors: positive net balance (owed money)
        PriorityQueue<BalanceNode> creditors = new PriorityQueue<>((a, b) -> Long.compare(b.amountCents, a.amountCents));

        // Debtors: negative net balance (owe money, stored as positive magnitudes)
        PriorityQueue<BalanceNode> debtors = new PriorityQueue<>((a, b) -> Long.compare(b.amountCents, a.amountCents));

        for (Map.Entry<String, Long> entry : netBalances.entrySet()) {
            long val = entry.getValue();
            if (val > 0) {
                creditors.offer(new BalanceNode(entry.getKey(), val));
            } else if (val < 0) {
                debtors.offer(new BalanceNode(entry.getKey(), -val));
            }
        }

        List<Transfer> transfers = new ArrayList<>();

        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            BalanceNode creditor = creditors.poll();
            BalanceNode debtor = debtors.poll();

            long settle = Math.min(creditor.amountCents, debtor.amountCents);
            if (settle > 0) {
                transfers.add(new Transfer(debtor.memberId, creditor.memberId, settle));
            }

            creditor.amountCents -= settle;
            debtor.amountCents -= settle;

            if (creditor.amountCents > 0) {
                creditors.offer(creditor);
            }
            if (debtor.amountCents > 0) {
                debtors.offer(debtor);
            }
        }

        return transfers;
    }
}

