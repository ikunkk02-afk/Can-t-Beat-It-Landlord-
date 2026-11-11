package com.shouyun.bosslandlord.game;

import java.util.*;

public record Combination(Kind kind, int high, int length, int size) {
    public enum Kind {
        SINGLE("单张"), PAIR("对子"), TRIPLE("三张"), TRIPLE_SINGLE("三带一"), TRIPLE_PAIR("三带二"),
        STRAIGHT("顺子"), PAIR_STRAIGHT("连对"), AIRPLANE("飞机"), AIRPLANE_SINGLE("飞机带单"),
        AIRPLANE_PAIR("飞机带对"), FOUR_SINGLE("四带二"), FOUR_PAIR("四带两对"), BOMB("炸弹"), ROCKET("王炸");
        public final String title;
        Kind(String title) { this.title = title; }
    }
    public boolean beats(Combination other) {
        if (other == null) return true;
        if (other.kind == Kind.ROCKET) return false;
        if (kind == Kind.ROCKET) return true;
        if (kind == Kind.BOMB && other.kind != Kind.BOMB) return true;
        return kind == other.kind && size == other.size && length == other.length && high > other.high;
    }
    public boolean explosive() { return kind == Kind.BOMB || kind == Kind.ROCKET; }
    public static Combination parse(int[] cards) {
        if (cards.length == 0 || cards.length > 20 || !Cards.validDistinct(cards)) return null;
        int[] c = new int[18];
        for (int card : cards) c[Cards.rank(card)]++;
        int n = cards.length, high = 0, distinct = 0, max = 0;
        for (int r = 3; r <= 17; r++) if (c[r] > 0) { high = r; distinct++; max = Math.max(max, c[r]); }
        if (n == 2 && c[16] == 1 && c[17] == 1) return of(Kind.ROCKET, 17, 1, n);
        if (distinct == 1) return of(switch (n) { case 1 -> Kind.SINGLE; case 2 -> Kind.PAIR; case 3 -> Kind.TRIPLE; default -> Kind.BOMB; }, high, 1, n);
        int triple = find(c, 3), four = find(c, 4);
        if (n == 4 && triple > 0) return of(Kind.TRIPLE_SINGLE, triple, 1, n);
        if (n == 5 && triple > 0 && find(c, 2) > 0) return of(Kind.TRIPLE_PAIR, triple, 1, n);
        if (n == 6 && four > 0) return of(Kind.FOUR_SINGLE, four, 1, n);
        if (n == 8 && four > 0 && count(c, 2) == 2) return of(Kind.FOUR_PAIR, four, 1, n);
        if (n >= 5 && max == 1 && sequence(c, 1, n)) return of(Kind.STRAIGHT, high, n, n);
        if (n >= 6 && n % 2 == 0 && max == 2 && sequence(c, 2, n / 2)) return of(Kind.PAIR_STRAIGHT, high, n / 2, n);
        // Airplane cores never contain a 2 or joker, and wing cards cannot reuse a core rank.
        for (int wings = 0; wings <= 2; wings++) {
            int unit = 3 + wings;
            if (n % unit != 0 || n / unit < 2) continue;
            int len = n / unit;
            for (int start = 3; start + len - 1 <= 14; start++) {
                boolean ok = true;
                for (int r = start; r < start + len; r++) if (c[r] != 3) ok = false;
                if (!ok) continue;
                int singles = 0, pairs = 0;
                for (int r = 3; r <= 17; r++) if (r < start || r >= start + len) {
                    singles += c[r];
                    if (c[r] == 2) pairs++; else if (wings == 2 && c[r] != 0) ok = false;
                    if (wings == 1 && c[r] > 2) ok = false;
                }
                if (wings == 0 && singles != 0 || wings == 1 && singles != len || wings == 2 && pairs != len) ok = false;
                if (ok) return of(wings == 0 ? Kind.AIRPLANE : wings == 1 ? Kind.AIRPLANE_SINGLE : Kind.AIRPLANE_PAIR, start + len - 1, len, n);
            }
        }
        return null;
    }
    private static Combination of(Kind k, int h, int l, int n) { return new Combination(k, h, l, n); }
    private static int find(int[] c, int n) { for (int r = 3; r <= 17; r++) if (c[r] == n) return r; return 0; }
    private static int count(int[] c, int n) { int t = 0; for (int r = 3; r <= 17; r++) if (c[r] == n) t++; return t; }
    private static boolean sequence(int[] c, int copies, int ranks) {
        int first = 0, last = 0;
        for (int r = 3; r <= 17; r++) if (c[r] > 0) {
            if (r > 14 || c[r] != copies) return false;
            if (first == 0) first = r;
            last = r;
        }
        return last - first + 1 == ranks;
    }
}
