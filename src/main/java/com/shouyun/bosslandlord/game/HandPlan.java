package com.shouyun.bosslandlord.game;

import java.util.*;

/** Estimates how many plays will empty a hand if it can keep the lead. */
final class HandPlan {
    private static final long GUARD_BITS = 0x888888888888888L;
    private static final int SEARCH_BUDGET = 32_000;

    record Option(int[] cards, Combination combo, long ranks) {}

    private final Map<Long, Integer> memo = new HashMap<>();
    private final Set<Long> singlePlays = new HashSet<>();
    private final List<List<Option>> byRank = new ArrayList<>();
    private int budget = SEARCH_BUDGET;

    HandPlan(List<Option> options) {
        for (int rank = 3; rank <= 17; rank++) byRank.add(new ArrayList<>());
        for (Option option : options) {
            singlePlays.add(option.ranks());
            for (int rank = 3; rank <= 17; rank++) {
                if (count(option.ranks(), rank) != 0) byRank.get(rank - 3).add(option);
            }
        }
    }

    int turns(long remaining) {
        if (remaining == 0) return 0;
        if (singlePlays.contains(remaining)) return 1;
        Integer known = memo.get(remaining);
        if (known != null) return known;

        int best = 0;
        List<Option> pivot = null;
        for (int rank = 3; rank <= 17; rank++) {
            if (count(remaining, rank) == 0) continue;
            best++;
            List<Option> choices = byRank.get(rank - 3);
            if (pivot == null || choices.size() < pivot.size()) pivot = choices;
        }
        if (budget <= 0 || pivot == null) return best;
        // In any partition some play must contain the pivot rank. Trying those first avoids
        // solving the same partition again in every possible order.
        for (Option option : pivot) {
            if (best == 2 || budget-- <= 0) break;
            if (contains(remaining, option.ranks())) {
                best = Math.min(best, 1 + turns(remaining - option.ranks()));
            }
        }
        if (budget > 0 || best == 2) memo.put(remaining, best);
        return best;
    }

    static List<Option> options(int[] cards) {
        List<Option> result = new ArrayList<>();
        for (Moves.Move move : Moves.legal(cards, null)) {
            result.add(new Option(move.cards(), move.combination(), ranks(move.cards())));
        }
        result.sort(Comparator.comparingInt((Option option) -> option.combo().size()).reversed()
                .thenComparingInt(option -> option.combo().high()));
        return result;
    }

    static long ranks(int[] cards) {
        long result = 0;
        for (int card : cards) result += 1L << ((Cards.rank(card) - 3) * 4);
        return result;
    }

    static int count(long ranks, int rank) {
        return (int)((ranks >>> ((rank - 3) * 4)) & 7L);
    }

    static boolean contains(long hand, long play) {
        // Each nibble holds a rank count. An insufficient count clears that nibble's guard bit.
        return (((hand | GUARD_BITS) - play) & GUARD_BITS) == GUARD_BITS;
    }
}
