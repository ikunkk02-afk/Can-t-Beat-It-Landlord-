package com.shouyun.bosslandlord.game;

import java.util.*;

/** Seat 0 is the player, seat 1 the boss, and seat 2 the guest. */
public final class LandlordRound {
    private static final int DEAL_ATTEMPTS = 5;
    private static final int CONTROL_SPREAD = 6;
    private static final int TURN_SPREAD = 2;
    private final List<List<Integer>> hands = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    private final int[][] shown = {new int[0], new int[0], new int[0]};
    private final boolean[] passed = new boolean[3];
    private final int[] bottom = new int[3];
    private int landlord = -1, turn = -1, leader = -1, passes, winner = -1, revision;
    private long playedCards;
    private Combination target;
    public LandlordRound(Random random) {
        List<Integer> deck = balancedDeck(random);
        for (int i = 0; i < 51; i++) hands.get(i % 3).add(deck.get(i));
        for (int i = 0; i < 3; i++) bottom[i] = deck.get(51 + i);
    }

    private static List<Integer> balancedDeck(Random random) {
        List<Integer> deck = new ArrayList<>(54);
        for (int card = 0; card < 54; card++) deck.add(card);
        List<Integer> best = List.of();
        int bestPenalty = Integer.MAX_VALUE;
        boolean firstDealHasBomb = false;
        for (int attempt = 0; attempt < DEAL_ATTEMPTS; attempt++) {
            Collections.shuffle(deck, random);
            OpeningQuality quality = openingQuality(deck);
            if (attempt == 0) firstDealHasBomb = quality.hasBomb();
            // Preserve how often the first random shuffle gives someone a bomb.
            if (quality.hasBomb() != firstDealHasBomb) continue;
            int penalty = quality.penalty();
            if (penalty == 0) return deck;
            if (penalty < bestPenalty) {
                best = List.copyOf(deck);
                bestPenalty = penalty;
            }
        }
        return best;
    }

    // Score the three 17-card hands before bidding. The seat labels and bottom cards
    // do not affect the score, so the dealer cannot reserve good cards for a player.
    private record OpeningQuality(int penalty, boolean hasBomb) {}

    private static OpeningQuality openingQuality(List<Integer> deck) {
        int minControl = Integer.MAX_VALUE, maxControl = 0;
        int minTurns = Integer.MAX_VALUE, maxTurns = 0;
        int pairHeavy = 0;
        boolean hasBomb = false;
        for (int seat = 0; seat < 3; seat++) {
            int[] hand = new int[17];
            for (int i = 0; i < hand.length; i++) hand[i] = deck.get(seat + i * 3);
            long ranks = HandPlan.ranks(hand);
            List<HandPlan.Option> options = HandPlan.options(hand);
            int turns = new HandPlan(options).turns(ranks);
            int smallJoker = HandPlan.count(ranks, 16), bigJoker = HandPlan.count(ranks, 17);
            // The two jokers can be used together as one control play.
            int control = HandPlan.count(ranks, 14) + 3 * HandPlan.count(ranks, 15)
                    + 3 * smallJoker + 4 * bigJoker - 3 * smallJoker * bigJoker;
            int pairs = 0;
            for (int rank = 3; rank <= 15; rank++) {
                int copies = HandPlan.count(ranks, rank);
                if (copies == 2) pairs++;
                if (copies == 4) hasBomb = true;
            }
            if (pairs >= 4 && options.stream().noneMatch(option -> option.combo().kind() == Combination.Kind.STRAIGHT)) {
                pairHeavy++;
            }
            minControl = Math.min(minControl, control);
            maxControl = Math.max(maxControl, control);
            minTurns = Math.min(minTurns, turns);
            maxTurns = Math.max(maxTurns, turns);
        }
        int penalty = 2 * Math.max(0, maxControl - minControl - CONTROL_SPREAD)
                + 3 * Math.max(0, maxTurns - minTurns - TURN_SPREAD)
                + 2 * pairHeavy;
        return new OpeningQuality(penalty, hasBomb);
    }
    public void assignLandlord(int seat) {
        if (landlord != -1 || (seat != 0 && seat != 1))
            throw new IllegalStateException("Invalid landlord assignment");
        landlord = seat;
        turn = seat;
        for (int card : bottom) hands.get(seat).add(card);
    }
    public String play(int seat, int[] cards) {
        if (landlord < 0 || winner >= 0 || seat != turn) return "还没轮到你出牌";
        if (!Cards.validDistinct(cards) || cards.length > 20) return "无效的牌";
        if (cards.length == 0) return pass(seat);
        for (int card : cards) if (!hands.get(seat).contains(card)) return "这些牌不在你的手中";
        Combination c = Combination.parse(cards);
        if (c == null) return "这些牌不能组成合法牌型";
        if (!c.beats(target)) return "必须出相同牌型、更大的牌，或使用炸弹";
        for (int card : cards) hands.get(seat).remove(Integer.valueOf(card));
        playedCards |= Cards.mask(cards);
        shown[seat] = cards.clone(); passed[seat] = false;
        target = c; leader = seat; passes = 0; revision++;
        if (hands.get(seat).isEmpty()) winner = seat;
        else turn = (turn + 1) % 3;
        return "";
    }
    private String pass(int seat) {
        if (target == null) return "你先出牌，不能不出";
        shown[seat] = new int[0]; passed[seat] = true; passes++; revision++;
        turn = (turn + 1) % 3;
        if (passes == 2) {
            turn = leader; target = null; passes = 0; leader = -1;
            Arrays.fill(passed, false); for (int i = 0; i < 3; i++) shown[i] = new int[0];
        }
        return "";
    }
    public int[] suggest(int seat) {
        if (seat != turn || landlord < 0 || winner >= 0) return new int[0];
        int[] counts = {count(0), count(1), count(2)};
        return CardAi.choose(new CardAi.View(seat, landlord, hand(seat), counts, playedCards, bottom(), leader, target, passes));
    }
    public int[] hand(int seat) { return Cards.sorted(hands.get(seat)); }
    public int[] shown(int seat) { return shown[seat].clone(); }
    public int[] bottom() { return landlord < 0 ? new int[0] : bottom.clone(); }
    public int landlord() { return landlord; }
    public boolean sameSide(int a, int b) { return (a == landlord) == (b == landlord); }
    public int count(int seat) { return hands.get(seat).size(); }
    public boolean passed(int seat) { return passed[seat]; }
    public int turn() { return turn; }
    public int leader() { return leader; }
    public int winner() { return winner; }
    public int revision() { return revision; }
    public Combination target() { return target; }
    public boolean playerWins() { return winner >= 0 && sameSide(0, winner); }
}
