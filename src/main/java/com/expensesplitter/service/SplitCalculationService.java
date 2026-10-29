package com.expensesplitter.service;

import com.expensesplitter.model.SplitType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Calculates expense distributions across members.
 * Guarantees that when a split does not divide evenly, the remainder is distributed
 * one minor unit at a time so shares always sum exactly to the total amount.
 */
public class SplitCalculationService {

    public static class SplitResult {
        private final Map<String, Long> sharesCents;
        private final boolean valid;
        private final String errorMessage;

        public SplitResult(Map<String, Long> sharesCents, boolean valid, String errorMessage) {
            this.sharesCents = Collections.unmodifiableMap(sharesCents);
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public Map<String, Long> getSharesCents() {
            return sharesCents;
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public long getShare(String memberId) {
            return sharesCents.getOrDefault(memberId, 0L);
        }
    }

    public SplitResult calculateSplits(long totalAmountCents, SplitType splitType, List<String> memberIds, Map<String, BigDecimal> params) {
        if (memberIds == null || memberIds.isEmpty()) {
            return new SplitResult(Collections.emptyMap(), false, "At least one member must be selected for the split.");
        }
        if (totalAmountCents <= 0) {
            return new SplitResult(Collections.emptyMap(), false, "Expense amount must be greater than zero.");
        }

        return switch (splitType) {
            case EQUAL -> calculateEqual(totalAmountCents, memberIds);
            case EXACT -> calculateExact(totalAmountCents, memberIds, params);
            case PERCENT -> calculatePercent(totalAmountCents, memberIds, params);
            case SHARES -> calculateShares(totalAmountCents, memberIds, params);
        };
    }

    private SplitResult calculateEqual(long totalCents, List<String> memberIds) {
        int n = memberIds.size();
        long baseShare = totalCents / n;
        long remainder = totalCents % n;

        Map<String, Long> shares = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            long share = baseShare + (i < remainder ? 1L : 0L);
            shares.put(memberIds.get(i), share);
        }
        return new SplitResult(shares, true, null);
    }

    private SplitResult calculateExact(long totalCents, List<String> memberIds, Map<String, BigDecimal> params) {
        Map<String, Long> shares = new LinkedHashMap<>();
        long sumCents = 0;

        for (String memberId : memberIds) {
            BigDecimal val = (params != null) ? params.getOrDefault(memberId, BigDecimal.ZERO) : BigDecimal.ZERO;
            long cents = val.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
            if (cents < 0) {
                return new SplitResult(Collections.emptyMap(), false, "Exact share cannot be negative.");
            }
            shares.put(memberId, cents);
            sumCents += cents;
        }

        if (sumCents != totalCents) {
            long diff = totalCents - sumCents;
            String msg = String.format("Exact shares sum to %s, differing from total by %s.",
                formatCents(sumCents), formatCents(Math.abs(diff)));
            return new SplitResult(shares, false, msg);
        }

        return new SplitResult(shares, true, null);
    }

    private SplitResult calculatePercent(long totalCents, List<String> memberIds, Map<String, BigDecimal> params) {
        Map<String, Long> shares = new LinkedHashMap<>();
        BigDecimal hundred = new BigDecimal("100.00");
        BigDecimal totalPercent = BigDecimal.ZERO;

        class FractionalRemainder {
            final String memberId;
            final long baseCents;
            final BigDecimal fraction;

            FractionalRemainder(String memberId, long baseCents, BigDecimal fraction) {
                this.memberId = memberId;
                this.baseCents = baseCents;
                this.fraction = fraction;
            }
        }

        List<FractionalRemainder> remainders = new ArrayList<>();
        long allocatedSum = 0;

        for (String memberId : memberIds) {
            BigDecimal p = (params != null) ? params.getOrDefault(memberId, BigDecimal.ZERO) : BigDecimal.ZERO;
            if (p.compareTo(BigDecimal.ZERO) < 0) {
                return new SplitResult(Collections.emptyMap(), false, "Percentage cannot be negative.");
            }
            totalPercent = totalPercent.add(p);

            // Exact calculation: totalCents * (p / 100)
            BigDecimal exactCents = BigDecimal.valueOf(totalCents).multiply(p).divide(hundred, 6, RoundingMode.HALF_UP);
            long baseCents = exactCents.longValue();
            BigDecimal fraction = exactCents.remainder(BigDecimal.ONE);

            remainders.add(new FractionalRemainder(memberId, baseCents, fraction));
            allocatedSum += baseCents;
        }

        if (totalPercent.setScale(2, RoundingMode.HALF_UP).compareTo(hundred) != 0) {
            return new SplitResult(Collections.emptyMap(), false,
                "Percentages must sum to 100.00%. Current sum: " + totalPercent.setScale(2, RoundingMode.HALF_UP) + "%.");
        }

        // Sort by largest fractional remainder first to distribute remaining cents
        remainders.sort((a, b) -> b.fraction.compareTo(a.fraction));

        long remainingCents = totalCents - allocatedSum;
        for (int i = 0; i < remainders.size(); i++) {
            FractionalRemainder fr = remainders.get(i);
            long extra = (i < remainingCents) ? 1L : 0L;
            shares.put(fr.memberId, fr.baseCents + extra);
        }

        return new SplitResult(shares, true, null);
    }

    private SplitResult calculateShares(long totalCents, List<String> memberIds, Map<String, BigDecimal> params) {
        BigDecimal totalShares = BigDecimal.ZERO;
        for (String memberId : memberIds) {
            BigDecimal s = (params != null) ? params.getOrDefault(memberId, BigDecimal.ONE) : BigDecimal.ONE;
            if (s.compareTo(BigDecimal.ZERO) <= 0) {
                return new SplitResult(Collections.emptyMap(), false, "Share counts must be strictly positive.");
            }
            totalShares = totalShares.add(s);
        }

        if (totalShares.compareTo(BigDecimal.ZERO) == 0) {
            return new SplitResult(Collections.emptyMap(), false, "Total shares must be greater than zero.");
        }

        class ShareFraction {
            final String memberId;
            final long baseCents;
            final BigDecimal fraction;

            ShareFraction(String memberId, long baseCents, BigDecimal fraction) {
                this.memberId = memberId;
                this.baseCents = baseCents;
                this.fraction = fraction;
            }
        }

        List<ShareFraction> fractions = new ArrayList<>();
        long allocatedSum = 0;

        for (String memberId : memberIds) {
            BigDecimal s = (params != null) ? params.getOrDefault(memberId, BigDecimal.ONE) : BigDecimal.ONE;
            BigDecimal exactCents = BigDecimal.valueOf(totalCents).multiply(s).divide(totalShares, 6, RoundingMode.HALF_UP);
            long baseCents = exactCents.longValue();
            BigDecimal fraction = exactCents.remainder(BigDecimal.ONE);

            fractions.add(new ShareFraction(memberId, baseCents, fraction));
            allocatedSum += baseCents;
        }

        fractions.sort((a, b) -> b.fraction.compareTo(a.fraction));
        long remainingCents = totalCents - allocatedSum;

        Map<String, Long> shares = new LinkedHashMap<>();
        for (int i = 0; i < fractions.size(); i++) {
            ShareFraction sf = fractions.get(i);
            long extra = (i < remainingCents) ? 1L : 0L;
            shares.put(sf.memberId, sf.baseCents + extra);
        }

        return new SplitResult(shares, true, null);
    }

    private String formatCents(long cents) {
        return BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP).toPlainString();
    }
}

