package com.shouyun.bosslandlord.game;

import java.util.*;

/** Rank-based move generation avoids enumerating the million subsets of a 20-card hand. */
public final class Moves {
    private Moves() {}
    public record Move(int[] cards, Combination combination) {}
    public static List<Move> legal(int[] hand, Combination target) {
        List<List<Integer>> ranks = new ArrayList<>();
        for (int i = 0; i < 18; i++) ranks.add(new ArrayList<>());
        for (int card : hand) ranks.get(Cards.rank(card)).add(card);
        List<Move> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int r = 3; r <= 17; r++) for (int n = 1; n <= ranks.get(r).size(); n++) {
            int[] core = take(ranks, r, r, n);
            add(out, seen, core, target);
            if (n == 3) {
                wings(out, seen, ranks, core, r, r, 1, false, target);
                wings(out, seen, ranks, core, r, r, 1, true, target);
            } else if (n == 4) {
                wings(out, seen, ranks, core, r, r, 2, false, target);
                wings(out, seen, ranks, core, r, r, 2, true, target);
            }
        }
        if (!ranks.get(16).isEmpty() && !ranks.get(17).isEmpty()) add(out, seen, new int[]{52, 53}, target);
        for (int copies = 1; copies <= 3; copies++) {
            int min = copies == 1 ? 5 : copies == 2 ? 3 : 2;
            for (int start = 3; start <= 14; start++) for (int end = start; end <= 14; end++) {
                if (ranks.get(end).size() < copies) break;
                int len = end - start + 1;
                if (len < min || len * copies > hand.length) continue;
                int[] core = take(ranks, start, end, copies);
                add(out, seen, core, target);
                if (copies == 3) {
                    wings(out, seen, ranks, core, start, end, len, false, target);
                    wings(out, seen, ranks, core, start, end, len, true, target);
                }
            }
        }
        return out;
    }
    private static int[] take(List<List<Integer>> ranks, int from, int to, int n) {
        int[] result = new int[(to - from + 1) * n]; int i = 0;
        for (int r = from; r <= to; r++) for (int k = 0; k < n; k++) result[i++] = ranks.get(r).get(k);
        return result;
    }
    private static void wings(List<Move> out, Set<Long> seen, List<List<Integer>> ranks, int[] core,
                              int from, int to, int count, boolean pairs, Combination target) {
        int total = core.length + count * (pairs ? 2 : 1);
        if (total > 20) return;
        if (target != null && (target.size() != total || target.explosive())) return;
        int[] working = Arrays.copyOf(core, total);
        attach(out, seen, ranks, working, core.length, 3, count, pairs, from, to, target);
    }
    private static void attach(List<Move> out, Set<Long> seen, List<List<Integer>> ranks, int[] working,
                               int pos, int r, int left, boolean pairs, int from, int to, Combination target) {
        if (left == 0) { add(out, seen, working.clone(), target); return; }
        if (r > 17) return;
        if (r >= from && r <= to) { attach(out, seen, ranks, working, pos, r + 1, left, pairs, from, to, target); return; }
        int max = pairs ? (ranks.get(r).size() >= 2 ? 1 : 0) : Math.min(2, ranks.get(r).size());
        for (int n = 0; n <= Math.min(max, left); n++) {
            int number = pairs ? n * 2 : n;
            for (int k = 0; k < number; k++) working[pos + k] = ranks.get(r).get(k);
            attach(out, seen, ranks, working, pos + number, r + 1, left - n, pairs, from, to, target);
        }
    }
    private static void add(List<Move> out, Set<Long> seen, int[] cards, Combination target) {
        Combination c = Combination.parse(cards);
        if (c == null || !c.beats(target)) return;
        long key = 0; for (int card : cards) key |= 1L << card;
        if (seen.add(key)) out.add(new Move(cards, c));
    }
}
