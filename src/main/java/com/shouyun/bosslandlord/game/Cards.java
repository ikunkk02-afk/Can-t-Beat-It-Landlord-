package com.shouyun.bosslandlord.game;

import java.util.*;

/** Stable card IDs: ranks 3..2 in four suits, then the two jokers. */
public final class Cards {
    private Cards() {}
    public static int rank(int card) {
        if (card < 0 || card >= 54) throw new IllegalArgumentException("Card outside deck");
        return card < 52 ? card / 4 + 3 : card - 36;
    }
    public static String label(int card) {
        return switch (rank(card)) {
            case 11 -> "J"; case 12 -> "Q"; case 13 -> "K"; case 14 -> "A";
            case 15 -> "2"; case 16 -> "小王"; case 17 -> "大王";
            default -> Integer.toString(rank(card));
        };
    }
    public static String suit(int card) {
        return card >= 52 ? "★" : switch (card % 4) { case 0 -> "♠"; case 1 -> "♥"; case 2 -> "♣"; default -> "♦"; };
    }
    public static boolean red(int card) { return card == 53 || card < 52 && card % 2 == 1; }
    public static int[] sorted(Collection<Integer> cards) {
        return cards.stream().sorted(Comparator.reverseOrder()).mapToInt(Integer::intValue).toArray();
    }
    public static boolean validDistinct(int[] cards) {
        long seen = 0;
        for (int card : cards) {
            if (card < 0 || card >= 54 || (seen & 1L << card) != 0) return false;
            seen |= 1L << card;
        }
        return true;
    }
    static long mask(int[] cards) {
        long mask = 0;
        for (int card : cards) mask |= 1L << card;
        return mask;
    }
}
