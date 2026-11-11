package com.shouyun.bosslandlord.game;

import java.util.*;
import static com.shouyun.bosslandlord.game.Combination.Kind.*;

/** Dependency-free rule checks; deliberately never bootstraps a game client or server. */
public final class LogicTests {
    private static int checks;
    private static void check(boolean condition, String name) {
        checks++; if (!condition) throw new AssertionError(name);
    }
    private static int[] cards(int... ranks) {
        return cardsWithSuits(new int[18], ranks);
    }
    private static int[] cardsWithSuits(int[] used, int[] ranks) {
        int[] cards = new int[ranks.length];
        for (int i = 0; i < ranks.length; i++) {
            int r = ranks[i]; cards[i] = r >= 16 ? r + 36 : (r - 3) * 4 + used[r]++;
        }
        return cards;
    }
    private static void kind(Combination.Kind expected, int... ranks) {
        Combination c = Combination.parse(cards(ranks));
        check(c != null && c.kind() == expected, "Expected " + expected + " for " + Arrays.toString(ranks) + ", got " + c);
    }
    private static String signature(int[] ids) {
        int[] counts = new int[18]; for (int card : ids) counts[Cards.rank(card)]++;
        return Arrays.toString(counts);
    }
    private static void ruleExamples() {
        kind(SINGLE, 3); kind(PAIR, 15,15); kind(TRIPLE, 7,7,7);
        kind(TRIPLE_SINGLE, 3,3,3,17); kind(TRIPLE_PAIR, 9,9,9,15,15);
        kind(STRAIGHT, 10,11,12,13,14); kind(PAIR_STRAIGHT, 12,12,13,13,14,14);
        kind(AIRPLANE, 3,3,3,4,4,4); kind(AIRPLANE, 3,3,3,4,4,4,5,5,5);
        kind(AIRPLANE_SINGLE, 7,7,7,8,8,8,3,15);
        kind(AIRPLANE_SINGLE, 7,7,7,8,8,8,3,3);
        kind(AIRPLANE_SINGLE, 3,3,3,4,4,4,16,17);
        kind(AIRPLANE_PAIR, 3,3,3,4,4,4,7,7,15,15);
        kind(FOUR_SINGLE, 5,5,5,5,3,3); kind(FOUR_SINGLE, 5,5,5,5,16,17);
        kind(FOUR_PAIR, 8,8,8,8,3,3,15,15);
        kind(BOMB, 15,15,15,15); kind(ROCKET, 16,17);
        check(Combination.parse(new int[0]) == null, "Empty is not a combination");
        check(Combination.parse(new int[]{0,0}) == null, "Duplicate physical cards rejected");
        check(Combination.parse(new int[]{-1}) == null && Combination.parse(new int[]{54}) == null, "Invalid deck IDs rejected");
        check(Combination.parse(cards(11,12,13,14,15)) == null, "2 cannot be in a straight");
        check(Combination.parse(cards(14,14,14,15,15,15)) == null, "2 cannot be in an airplane");
        check(Combination.parse(cards(3,3,4,4,5)) == null, "Incomplete pair straight rejected");
        check(Combination.parse(cards(3,3,3,3,4,4,4,5)) == null, "Core rank cannot double as a wing");
        var rocket = Combination.parse(cards(16,17)); var bomb = Combination.parse(cards(3,3,3,3));
        check(rocket.beats(bomb) && !bomb.beats(rocket) && !rocket.beats(rocket), "Rocket hierarchy");
        check(bomb.beats(Combination.parse(cards(15,15,15,17))), "Bomb beats an ordinary play");
        check(!Combination.parse(cards(4,4)).beats(Combination.parse(cards(3))), "Different families do not compare");
        check(!Combination.parse(cards(4,5,6,7,8,9)).beats(Combination.parse(cards(3,4,5,6,7))), "Straight lengths must match");
        check(Combination.parse(cards(4,4,4,3)).beats(Combination.parse(cards(3,3,3,17))), "Triple main rank determines strength");
    }
    private static void exhaustiveMoveCoverage() {
        Random rng = new Random(42821);
        List<Integer> deck = new ArrayList<>(); for (int i = 0; i < 54; i++) deck.add(i);
        List<int[]> samples = new ArrayList<>();
        samples.add(cards(3,3,3,4,4,4,5,5,6,6));
        samples.add(cards(3,3,3,3,4,4,4,4,16,17));
        samples.add(cards(10,11,12,13,14,15,16,17));
        for (int i = 0; i < 80; i++) { Collections.shuffle(deck, rng); samples.add(deck.subList(0, 10).stream().mapToInt(Integer::intValue).toArray()); }
        for (int[] hand : samples) {
            Set<String> expected = new HashSet<>(); List<int[]> subsets = new ArrayList<>();
            for (int mask = 1; mask < 1 << hand.length; mask++) {
                int[] play = new int[Integer.bitCount(mask)]; int k = 0;
                for (int i = 0; i < hand.length; i++) if ((mask & 1 << i) != 0) play[k++] = hand[i];
                if (Combination.parse(play) != null) { expected.add(signature(play)); subsets.add(play); }
            }
            List<Moves.Move> generated = Moves.legal(hand, null); Set<String> actual = new HashSet<>();
            for (Moves.Move move : generated) {
                actual.add(signature(move.cards())); check(Combination.parse(move.cards()) != null, "Generator emits valid combinations");
            }
            check(actual.equals(expected), "Move generator covers every rank-equivalent legal subset");
            for (int[] targetCards : subsets.stream().limit(15).toList()) {
                Combination target = Combination.parse(targetCards); Set<String> expectedResponses = new HashSet<>();
                for (int[] play : subsets) if (Combination.parse(play).beats(target)) expectedResponses.add(signature(play));
                Set<String> responses = new HashSet<>();
                for (Moves.Move move : Moves.legal(hand, target)) responses.add(signature(move.cards()));
                check(responses.equals(expectedResponses), "Response generator preserves every legal family");
            }
        }
    }
    private static void roundChecks() {
        LandlordRound r = new LandlordRound(new Random(1));
        check(r.count(0) == 17 && r.count(1) == 17 && r.count(2) == 17, "Each player can inspect 17 cards during bidding");
        check(r.bottom().length == 0 && r.turn() == -1, "Bottom and lead remain hidden before assignment");
        check(!r.play(0, new int[]{r.hand(0)[0]}).isEmpty(), "Cannot play while bidding");
        r.assignLandlord(1);
        check(r.count(0) == 17 && r.count(1) == 20 && r.count(2) == 17 && r.turn() == 1, "Deal is 17/20/17; boss leads");
        Set<Integer> deck = new HashSet<>(); for (int s = 0; s < 3; s++) for (int card : r.hand(s)) check(deck.add(card), "No duplicated deal card");
        check(deck.size() == 54, "Complete deck conserved");
        check(!r.play(0, new int[]{r.hand(0)[0]}).isEmpty() && r.revision() == 0, "Out of turn request cannot mutate state");
        check(!r.play(1, new int[0]).isEmpty() && r.revision() == 0, "Leader cannot pass");
        check(!r.play(1, new int[]{r.hand(0)[0]}).isEmpty() && r.count(1) == 20, "Cannot play another player's card");
        int c = r.hand(1)[0]; check(!r.play(1, new int[]{c,c}).isEmpty(), "Duplicate packet cards rejected");
        check(r.play(1, new int[]{c}).isEmpty(), "Landlord plays single");
        check(r.play(2, new int[0]).isEmpty() && r.target() != null, "First pass retains target");
        check(r.play(0, new int[0]).isEmpty() && r.turn() == 1 && r.target() == null, "Two passes return free lead to last player");
        for (int i = 0; i < 3; i++) check(r.shown(i).length == 0 && !r.passed(i), "New trick clears old table cards");
        // Defensive copies keep callers from modifying authoritative hands.
        int[] copy = r.hand(1); int previous = copy[0]; copy[0] = -1;
        check(r.hand(1)[0] == previous, "Hand snapshot is immutable from the caller");
    }

    private static void auctionChecks() {
        LandlordAuction declined = new LandlordAuction();
        check(!declined.bossDecision(true), "Boss cannot act before the player");
        check(declined.playerDecision(false) && declined.bossDecision(false)
                && declined.step() == LandlordAuction.Step.COMPLETE && declined.candidate() == 1, "Declining assigns the boss");
        LandlordAuction called = new LandlordAuction();
        check(called.playerDecision(true) && !called.playerDecision(true) && called.bossDecision(false)
                && called.step() == LandlordAuction.Step.COMPLETE && called.candidate() == 0, "Uncontested call assigns the player");
        for (boolean rob : new boolean[]{false, true}) {
            LandlordAuction contested = new LandlordAuction();
            check(contested.playerDecision(true) && contested.bossDecision(true)
                    && contested.step() == LandlordAuction.Step.PLAYER_ROB, "Boss counterbid opens the final decision");
            check(contested.playerDecision(rob) && contested.step() == LandlordAuction.Step.COMPLETE
                    && contested.candidate() == (rob ? 0 : 1), "Player's final decision determines landlord");
            check(!contested.playerDecision(true) && !contested.bossDecision(true), "Completed auction rejects repeated decisions");
        }
        LandlordRound playerRound = new LandlordRound(new Random(84));
        playerRound.assignLandlord(0);
        check(playerRound.turn() == 0 && playerRound.count(0) == 20
                && playerRound.count(1) == 17 && playerRound.count(2) == 17, "Player landlord receives bottom and leads");
        for (int card : playerRound.bottom()) {
            check(Arrays.stream(playerRound.hand(0)).anyMatch(c -> c == card), "Bottom belongs to the winning bidder");
        }
        check(playerRound.sameSide(1, 2) && !playerRound.sameSide(0, 2), "Guest joins the boss when player is landlord");
    }

    private static void balancedDealChecks() {
        int plainSpread = 0, balancedSpread = 0;
        int[] highCardsBySeat = new int[3];
        Random seeds = new Random(462507L);
        for (int i = 0; i < 500; i++) {
            long seed = seeds.nextLong();
            LandlordRound round = new LandlordRound(new Random(seed));
            List<Integer> plain = new ArrayList<>();
            for (int card = 0; card < 54; card++) plain.add(card);
            Collections.shuffle(plain, new Random(seed));
            int[] plainHigh = new int[3], balancedHigh = new int[3];
            long[] plainRanks = new long[3];
            for (int j = 0; j < 51; j++) {
                int rank = Cards.rank(plain.get(j));
                if (rank >= 15) plainHigh[j % 3]++;
                plainRanks[j % 3] += 1L << ((rank - 3) * 4);
            }
            boolean openingBomb = false;
            for (int seat = 0; seat < 3; seat++) {
                int[] hand = round.hand(seat);
                check(hand.length == 17, "Each seat gets 17 cards before bidding");
                for (int card : hand) if (Cards.rank(card) >= 15) balancedHigh[seat]++;
                highCardsBySeat[seat] += balancedHigh[seat];
                openingBomb |= hasBomb(HandPlan.ranks(hand));
            }
            check(openingBomb == Arrays.stream(plainRanks).anyMatch(LogicTests::hasBomb),
                    "Balance selection preserves whether the first shuffle gave any seat a bomb");
            plainSpread += spread(plainHigh);
            balancedSpread += spread(balancedHigh);

            round.assignLandlord(i % 2);
            int[] all = new int[54];
            int offset = 0;
            for (int seat = 0; seat < 3; seat++) {
                int[] hand = round.hand(seat);
                System.arraycopy(hand, 0, all, offset, hand.length);
                offset += hand.length;
            }
            check(offset == 54 && Cards.validDistinct(all), "Balanced selection conserves all 54 physical cards");
        }
        check(balancedSpread < plainSpread, "Balanced selection reduces concentration of twos and jokers");
        check(spread(highCardsBySeat) < 100, "No seat accumulates a large advantage over the seeded sample");
    }

    private static int spread(int[] counts) {
        return Math.max(counts[0], Math.max(counts[1], counts[2])) - Math.min(counts[0], Math.min(counts[1], counts[2]));
    }

    private static boolean hasBomb(long ranks) {
        for (int rank = 3; rank <= 15; rank++) if (HandPlan.count(ranks, rank) == 4) return true;
        return false;
    }

    private static void simulatedMatches() {
        int playerWins = 0, guestFinishes = 0;
        long start = System.nanoTime();
        for (int seed = 0; seed < 500; seed++) {
            int landlord = seed % 2;
            LandlordRound r = new LandlordRound(new Random(seed));
            r.assignLandlord(landlord);
            int turns = 0;
            while (r.winner() < 0 && turns++ < 170) {
                int seat = r.turn(); int[] play = r.suggest(seat); int before = r.count(seat);
                check(r.play(seat, play).isEmpty(), "AI always chooses a legal play, seed=" + seed);
                check(r.count(seat) == before - play.length, "Move conserves remaining cards");
                check(r.revision() == turns, "Exactly one revision per accepted action");
            }
            check(r.winner() >= 0 && turns <= 170, "Every simulated match terminates");
            check(r.count(r.winner()) == 0, "Winner emptied their hand");
            boolean playerSideWon = landlord == 0 ? r.winner() == 0 : r.winner() != 1;
            check(r.playerWins() == playerSideWon, "Settlement uses the assigned roles");
            if (r.playerWins()) playerWins++;
            if (r.winner() == 2) guestFinishes++;
            check(!r.play(r.turn(), new int[0]).isEmpty(), "Finished match rejects extra actions");
        }
        check(playerWins > 0 && playerWins < 500 && guestFinishes > 0, "Both rivals and the guest can finish");
        System.out.printf(Locale.ROOT, "500 full matches, both landlord assignments: player=%d, boss=%d, guest finishes=%d, %.2fs%n",
                playerWins, 500 - playerWins, guestFinishes, (System.nanoTime()-start)/1e9);
    }
    private static void healthChecks() {
        for (float max : new float[]{20, 200, 300, 500, 1003}) {
            float hp = max;
            for (int i = 0; i < 3; i++) hp = WagerMath.remaining(hp, max, 1);
            check(hp == 0, "Three wagers remove exactly a full max-health bar");
            check(Math.abs(WagerMath.remaining(max, max, 1) - max*2/3) < .001, "Standard wager removes one-third maximum health");
            check(Math.abs(WagerMath.remaining(max, max, 2) - max/3) < .001, "Double wager removes two-thirds maximum health");
            check(WagerMath.remaining(max, max, 3) == 0, "Super wager can clear full health");
            check(WagerMath.remaining(1, max, 1) == 0, "Low-health losses can be lethal");
            check(WagerMath.remaining(max/2, max, 1) < max/4, "Stake uses maximum, not current health");
        }
    }

    private static void animationChecks() {
        var plane = Combination.parse(cards(3,3,3,4,4,4,7,7,15,15));
        var bomb = Combination.parse(cards(3,3,3,3));
        var rocket = Combination.parse(cards(16,17));
        var straight = Combination.parse(cards(10,11,12,13,14));
        check(PlayEffect.of(plane) == PlayEffect.AIRPLANE, "Airplane variants trigger a flyover");
        check(PlayEffect.of(bomb) == PlayEffect.BOMB && PlayEffect.BOMB.blasts(plane), "Bomb can shoot down an airplane");
        check(PlayEffect.of(rocket) == PlayEffect.ROCKET && PlayEffect.ROCKET.blasts(plane), "Rocket keeps its own effect against airplanes");
        check(PlayEffect.of(straight) == PlayEffect.NONE && !PlayEffect.BOMB.blasts(straight), "Ordinary combinations avoid the special effect");
    }

    private static CardAi.View position(int seat, int landlord, int leader, int passes, int[] target, int[]... ranksBySeat) {
        int[] usedSuits = new int[18];
        int[] counts = new int[3];
        int[][] hands = new int[3][];
        long remaining = 0;
        for (int i = 0; i < 3; i++) {
            hands[i] = cardsWithSuits(usedSuits, ranksBySeat[i]);
            counts[i] = hands[i].length;
            long mask = Cards.mask(hands[i]);
            check((remaining & mask) == 0 && Cards.validDistinct(hands[i]), "Fixture cards are unique");
            remaining |= mask;
        }
        // In the short fixtures the landlord's last cards were revealed as part of the bottom.
        int[] bottom = counts[landlord] <= 3 ? hands[landlord] : new int[0];
        return new CardAi.View(seat, landlord, hands[seat], counts, ((1L << 54) - 1) & ~remaining,
                bottom, leader, Combination.parse(cards(target)), passes);
    }

    private static void tacticalChecks() {
        int[] twoPlays = cards(3,4,5,6,7,8,8,8,9,9);
        check(new HandPlan(HandPlan.options(twoPlays)).turns(HandPlan.ranks(twoPlays)) == 2,
                "Hand planning keeps a five-card straight and a full triple-pair");

        CardAi.View blockSingle = position(0, 1, -1, 0, new int[0],
                new int[]{3,4,4}, new int[]{14}, new int[]{5,5});
        var play = Combination.parse(CardAi.choose(blockSingle));
        check(play != null && play.kind() == PAIR, "Lead a pair to block a one-card landlord and feed the farmer");

        CardAi.View protectAlly = position(0, 1, 2, 0, new int[]{5},
                new int[]{11,3,3}, new int[]{9}, new int[]{7,7});
        play = Combination.parse(CardAi.choose(protectAlly));
        check(play != null && play.kind() == SINGLE && play.high() == 11,
                "Overtake an ally's low single when the landlord can finish over it");

        CardAi.View giveWay = position(2, 0, 1, 0, new int[]{15},
                new int[]{3,8}, new int[]{7}, new int[]{4,4,4,4,5});
        check(CardAi.choose(giveWay).length == 0, "Keep the bomb and let an ally finish from the lead");

        CardAi.View emergency = position(0, 1, 1, 1, new int[]{7,7},
                new int[]{4,4,4,4,3}, new int[]{8,8}, new int[]{9,9,10});
        play = Combination.parse(CardAi.choose(emergency));
        check(play != null && play.kind() == BOMB, "Spend a bomb if passing gives the landlord a winning lead");

        CardAi.View opening = position(1, 1, -1, 0, new int[0],
                new int[]{4,7,8,9,11,12,14,15}, new int[]{3,3,4,4,5,5,10,13,15,16,17},
                new int[]{3,6,7,8,9,10,11,13});
        check(Arrays.stream(CardAi.choose(opening)).noneMatch(card -> Cards.rank(card) >= 16),
                "Keep both jokers in an ordinary opening");
    }

    private static int[] legacyDecision(LandlordRound round, int seat) {
        int[] hand = round.hand(seat);
        Combination target = round.target();
        List<Moves.Move> legal = Moves.legal(hand, target);
        for (Moves.Move move : legal) if (move.cards().length == hand.length) return move.cards();
        if (target != null && round.sameSide(seat, round.leader()) || legal.isEmpty()) return new int[0];
        int landlord = round.landlord();
        int enemyRemaining = seat == landlord ? Math.min(round.count(1 - landlord), round.count(2)) : round.count(landlord);
        int[] counts = new int[18];
        for (int card : hand) counts[Cards.rank(card)]++;
        Moves.Move best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Moves.Move move : legal) {
            Combination combo = move.combination();
            double score = move.cards().length * (target == null ? 5.0 : 2.0) - combo.high() * .42;
            if (combo.explosive()) score -= hand.length > 6 ? 45 : 12;
            int[] used = new int[18];
            for (int card : move.cards()) used[Cards.rank(card)]++;
            for (int rank = 3; rank <= 17; rank++) {
                if (used[rank] > 0 && used[rank] < counts[rank]) {
                    score -= counts[rank] == 4 ? 15 : counts[rank] == 3 ? 4 : 2;
                }
            }
            if (enemyRemaining <= 2 && combo.size() > enemyRemaining) score += 20;
            if (enemyRemaining == 1 && combo.kind() == SINGLE) score += combo.high() * 2;
            if (score > bestScore) {
                best = move;
                bestScore = score;
            }
        }
        return best.cards();
    }

    private static boolean landlordWon(int seed, boolean upgradedLandlord, boolean upgradedFarmers) {
        int landlord = seed % 2;
        var round = new LandlordRound(new Random(seed));
        round.assignLandlord(landlord);
        int turns = 0;
        while (round.winner() < 0 && turns++ < 170) {
            int seat = round.turn();
            boolean upgraded = seat == landlord ? upgradedLandlord : upgradedFarmers;
            int[] play = upgraded ? round.suggest(seat) : legacyDecision(round, seat);
            String error = round.play(seat, play);
            if (!error.isEmpty()) throw new AssertionError(error);
        }
        if (round.winner() < 0) throw new AssertionError("Match did not finish");
        return round.winner() == landlord;
    }

    private static void compareStrategies() {
        int deals = 600, oldLandlord = 0, newLandlord = 0, newFarmers = 0;
        for (int seed = 8000; seed < 8000 + deals; seed++) {
            if (landlordWon(seed, false, false)) oldLandlord++;
            if (landlordWon(seed, true, false)) newLandlord++;
            if (!landlordWon(seed, false, true)) newFarmers++;
        }
        System.out.printf(Locale.ROOT, "%d paired matches: upgraded side wins %d (%.1f%%).%n",
                deals * 2, newLandlord + newFarmers, (newLandlord + newFarmers) * 50.0 / deals);
        System.out.printf(Locale.ROOT, "Same deals: landlord %d -> %d wins, farmers %d -> %d wins.%n",
                oldLandlord, newLandlord, deals - oldLandlord, newFarmers);
        check(newLandlord > oldLandlord && newFarmers > deals - oldLandlord,
                "The upgrade improves both factions on the same deals");
    }

    private static void propBurstChecks() {
        PropBurst player = new PropBurst();
        for (int i = 0; i < PropBurst.LIMIT; i++) {
            long now = 100 + i * 15;
            check(player.tryUse(now) && player.used(now) == i + 1, "All five consecutive props are allowed");
        }
        check(!player.tryUse(175), "Sixth prop is blocked");
        check(!player.tryUse(359), "Retry does not extend the ten-second cooldown");
        check(player.tryUse(360) && player.used(360) == 1, "Exactly 200 ticks after the fifth prop starts a fresh streak");
        check(player.tryUse(560) && player.used(560) == 1, "Ten quiet seconds reset a partial streak");
        check(new PropBurst().tryUse(175), "Another player has a separate streak");
    }

    public static void main(String[] args) {
        ruleExamples(); exhaustiveMoveCoverage(); roundChecks(); auctionChecks(); balancedDealChecks();
        simulatedMatches(); healthChecks(); animationChecks();
        tacticalChecks(); compareStrategies(); propBurstChecks();
        System.out.println("PASS: " + checks + " assertions; no Minecraft process or save was opened.");
    }
}
