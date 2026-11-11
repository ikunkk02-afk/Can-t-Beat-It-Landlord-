package com.shouyun.bosslandlord.game;

import java.util.*;
import static com.shouyun.bosslandlord.game.HandPlan.Option;

final class CardAi {
    private static final int[] PASS = new int[0];
    private static final int ENDGAME_CARDS = 16;
    private static final int COMBO_SIZES = 21;

    /** The AI is given its own hand and public information only. */
    record View(int seat, int landlord, int[] hand, int[] counts, long played, int[] bottom,
                int leader, Combination target, int passes) {
        boolean ally(int other) {
            return (other == landlord) == (seat == landlord);
        }
    }

    record Choice(Option option, double score) {}
    record Deal(Hand[] hands) {}
    private record Outlook(double holds, double teamFinishes, double rivalFinishes, double rivalLeads) {}

    static final class Hand {
        final long ranks;
        final List<Option> options;
        final Combination wholeHand;
        private final int[] high = new int[Combination.Kind.values().length * COMBO_SIZES];
        private int bomb;
        private boolean rocket;

        Hand(int[] cards, List<Option> options) {
            ranks = HandPlan.ranks(cards);
            this.options = options;
            wholeHand = Combination.parse(cards);
            for (Option option : options) {
                Combination combo = option.combo();
                int index = index(combo);
                high[index] = Math.max(high[index], combo.high());
                if (combo.kind() == Combination.Kind.BOMB) bomb = Math.max(bomb, combo.high());
                if (combo.kind() == Combination.Kind.ROCKET) rocket = true;
            }
        }

        boolean canBeat(Combination target) {
            if (target.kind() == Combination.Kind.ROCKET) return false;
            if (rocket) return true;
            if (target.kind() == Combination.Kind.BOMB) return bomb > target.high();
            return bomb > 0 || high[index(target)] > target.high();
        }

        boolean canFinish(Combination target) {
            return wholeHand != null && wholeHand.beats(target);
        }

        private static int index(Combination combo) {
            return combo.kind().ordinal() * COMBO_SIZES + combo.size();
        }
    }

    private CardAi() {}

    static int[] choose(View view) {
        List<Option> options = HandPlan.options(view.hand());
        List<Option> legal = new ArrayList<>();
        for (Option option : options) {
            if (!option.combo().beats(view.target())) continue;
            if (option.cards().length == view.hand().length) return option.cards();
            legal.add(option);
        }
        if (legal.isEmpty()) return PASS;

        long ownRanks = HandPlan.ranks(view.hand());
        HandPlan plan = new HandPlan(options);
        int startingTurns = plan.turns(ownRanks);
        boolean endgame = Arrays.stream(view.counts()).sum() <= ENDGAME_CARDS;
        boolean imminent = false;
        for (int seat = 0; seat < 3; seat++) {
            if (!view.ally(seat) && view.counts()[seat] <= 3) imminent = true;
        }
        List<Deal> deals = sampleDeals(view, options, endgame ? 12 : imminent ? 10 : 5);
        Map<Combination, Outlook> outlooks = new HashMap<>();
        List<Choice> choices = new ArrayList<>();
        for (Option option : legal) {
            int turnsLeft = plan.turns(ownRanks - option.ranks());
            Outlook outlook = outlooks.computeIfAbsent(option.combo(), combo -> playOutlook(view, deals, combo));
            double score = (startingTurns - turnsLeft) * 5.8 + option.cards().length * .42;
            score -= controlCost(ownRanks, option);
            if (view.target() != null && !option.combo().explosive()) {
                score -= .16 * (option.combo().high() - 3);
            }
            if (option.combo().explosive() && turnsLeft > 2) score -= 4.2;
            score += outlook.holds() * (turnsLeft == 1 ? 17 : view.target() == null ? 2.8 : 4.5);
            score += outlook.teamFinishes() * 21 - outlook.rivalFinishes() * 38;
            if (view.target() != null && view.ally(view.leader())) {
                score -= view.counts()[view.leader()] <= 3 ? 9.5 : 6.5;
            }
            choices.add(new Choice(option, score));
        }
        if (view.target() != null) {
            Outlook outlook = passOutlook(view, deals);
            double score = view.ally(view.leader()) ? 2.2 : -.9;
            score += outlook.teamFinishes() * 21 - outlook.rivalFinishes() * 38 - outlook.rivalLeads() * 5.5;
            choices.add(new Choice(null, score));
        }
        choices.sort(Comparator.comparingDouble(Choice::score).reversed());
        Choice best = endgame ? EndgameSearch.best(view, deals, choices) : choices.getFirst();
        return best.option() == null ? PASS : best.option().cards();
    }

    static boolean wantsLandlord(int[] hand) {
        long ranks = HandPlan.ranks(hand);
        int turns = new HandPlan(HandPlan.options(hand)).turns(ranks);
        int controls = HandPlan.count(ranks, 15) + 2 * (HandPlan.count(ranks, 16) + HandPlan.count(ranks, 17));
        for (int rank = 3; rank <= 15; rank++) {
            if (HandPlan.count(ranks, rank) == 4) controls += 4;
        }
        return controls >= 4 && (turns <= 5 || controls >= 7) || turns <= 3;
    }

    private static double controlCost(long own, Option option) {
        double cost = 0;
        for (int rank = 3; rank <= 17; rank++) {
            int used = HandPlan.count(option.ranks(), rank);
            if (used == 0) continue;
            int available = HandPlan.count(own, rank);
            if (rank >= 14) cost += used * (rank == 14 ? .38 : rank == 15 ? 1.15 : 1.65);
            if (available == 4) cost += used < 4 ? 5.2 : 3.9;
            else if (used < available) cost += available == 3 ? 1.3 : .68;
        }
        if (HandPlan.count(own, 16) == 1 && HandPlan.count(own, 17) == 1) {
            int jokers = HandPlan.count(option.ranks(), 16) + HandPlan.count(option.ranks(), 17);
            if (jokers > 0) cost += jokers == 1 ? 4.5 : 3.9;
        }
        return cost;
    }

    private static Outlook playOutlook(View view, List<Deal> deals, Combination play) {
        double holds = 0, teamFinishes = 0, rivalFinishes = 0;
        int first = (view.seat() + 1) % 3;
        int second = (view.seat() + 2) % 3;
        for (Deal deal : deals) {
            Hand a = deal.hands()[first];
            Hand b = deal.hands()[second];
            boolean aAlly = view.ally(first), bAlly = view.ally(second);
            boolean aBeats = a.canBeat(play), bBeats = b.canBeat(play);
            if ((aAlly || !aBeats) && (bAlly || !bBeats)) holds++;
            if (a.canFinish(play)) {
                if (aAlly) teamFinishes++;
                else rivalFinishes++;
            } else if (b.canFinish(play)) {
                if (bAlly) teamFinishes += aBeats && !aAlly ? .30 : 1;
                else rivalFinishes += aBeats && aAlly ? .55 : 1;
            }
        }
        return new Outlook(holds / deals.size(), teamFinishes / deals.size(), rivalFinishes / deals.size(), 0);
    }

    private static Outlook passOutlook(View view, List<Deal> deals) {
        double teamFinishes = 0, rivalFinishes = 0, rivalLeads = 0;
        int next = (view.seat() + 1) % 3;
        boolean leaderAlly = view.ally(view.leader());
        for (Deal deal : deals) {
            Hand nextHand = deal.hands()[next];
            Hand leader = deal.hands()[view.leader()];
            if (view.passes() == 0 && nextHand.canFinish(view.target())) {
                if (view.ally(next)) teamFinishes++;
                else rivalFinishes++;
                continue;
            }
            boolean rivalResponds = view.passes() == 0 && !view.ally(next) && nextHand.canBeat(view.target());
            boolean allyResponds = view.passes() == 0 && view.ally(next) && nextHand.canBeat(view.target());
            if (leaderAlly) {
                if (leader.wholeHand != null) teamFinishes += rivalResponds ? .15 : 1;
                if (rivalResponds) rivalLeads += .55;
            } else {
                double chanceOfLead = allyResponds ? .23 : 1;
                if (leader.wholeHand != null) rivalFinishes += chanceOfLead;
                if (view.counts()[view.leader()] <= 4) rivalLeads += chanceOfLead;
            }
        }
        return new Outlook(0, teamFinishes / deals.size(), rivalFinishes / deals.size(), rivalLeads / deals.size());
    }

    private static List<Deal> sampleDeals(View view, List<Option> ownOptions, int samples) {
        int first = (view.seat() + 1) % 3;
        int second = (view.seat() + 2) % 3;
        long ownMask = Cards.mask(view.hand());
        long unseen = ((1L << 54) - 1) & ~(view.played() | ownMask);
        long reserved = view.landlord() == view.seat() ? 0 : Cards.mask(view.bottom()) & unseen;
        int[] known = unpack(reserved);
        int[] pool = unpack(unseen & ~reserved);
        int neededFirst = view.counts()[first] - (first == view.landlord() ? known.length : 0);
        int neededSecond = view.counts()[second] - (second == view.landlord() ? known.length : 0);
        if (neededFirst == 0 || neededSecond == 0) samples = 1;

        Hand own = new Hand(view.hand(), ownOptions);
        List<Deal> deals = new ArrayList<>();
        if (pool.length <= 8 && allocationCount(pool.length, neededFirst) <= samples) {
            for (int mask = 0; mask < 1 << pool.length; mask++) {
                if (Integer.bitCount(mask) != neededFirst) continue;
                int[] a = new int[neededFirst];
                int[] b = new int[neededSecond];
                int atA = 0, atB = 0;
                for (int i = 0; i < pool.length; i++) {
                    if ((mask & 1 << i) != 0) a[atA++] = pool[i];
                    else b[atB++] = pool[i];
                }
                deals.add(makeDeal(view, own, first, second, a, b, known));
            }
            return deals;
        }

        SplittableRandom random = new SplittableRandom(ownMask * 31 + view.played() * 101 + view.seat());
        for (int sample = 0; sample < samples; sample++) {
            for (int i = pool.length - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                int card = pool[i];
                pool[i] = pool[j];
                pool[j] = card;
            }
            int[] a = Arrays.copyOfRange(pool, 0, neededFirst);
            int[] b = Arrays.copyOfRange(pool, neededFirst, neededFirst + neededSecond);
            deals.add(makeDeal(view, own, first, second, a, b, known));
        }
        return deals;
    }

    private static Deal makeDeal(View view, Hand own, int first, int second, int[] a, int[] b, int[] known) {
        if (first == view.landlord()) a = withKnown(a, known);
        if (second == view.landlord()) b = withKnown(b, known);
        Hand[] hands = new Hand[3];
        hands[view.seat()] = own;
        hands[first] = new Hand(a, HandPlan.options(a));
        hands[second] = new Hand(b, HandPlan.options(b));
        return new Deal(hands);
    }

    private static int allocationCount(int cards, int first) {
        int result = 1;
        for (int i = 0; i < first; i++) result = result * (cards - i) / (i + 1);
        return result;
    }

    private static int[] unpack(long mask) {
        int[] cards = new int[Long.bitCount(mask)];
        for (int i = 0; i < cards.length; i++) {
            cards[i] = Long.numberOfTrailingZeros(mask);
            mask &= mask - 1;
        }
        return cards;
    }

    private static int[] withKnown(int[] hand, int[] known) {
        int[] cards = Arrays.copyOf(hand, hand.length + known.length);
        System.arraycopy(known, 0, cards, hand.length, known.length);
        return cards;
    }
}
