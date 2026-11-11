package com.shouyun.bosslandlord.game;

import java.util.*;
import com.shouyun.bosslandlord.game.CardAi.Choice;
import com.shouyun.bosslandlord.game.CardAi.Deal;
import com.shouyun.bosslandlord.game.CardAi.View;
import com.shouyun.bosslandlord.game.HandPlan.Option;

/** Checks short lines in plausible deals. Exhausting the budget leaves a line undecided. */
final class EndgameSearch {
    private static final int CANDIDATES = 7;
    private static final int NODES_PER_CANDIDATE = 200;
    private static final double OUTCOME_WEIGHT = 36;

    private EndgameSearch() {}

    static Choice best(View view, List<Deal> deals, List<Choice> ranked) {
        if (ranked.size() == 1) return ranked.getFirst();
        List<Choice> finalists = new ArrayList<>(ranked.subList(0, Math.min(CANDIDATES, ranked.size())));
        if (finalists.stream().noneMatch(choice -> choice.option() == null)) {
            for (Choice choice : ranked) {
                if (choice.option() == null) {
                    finalists.add(choice);
                    break;
                }
            }
        }
        int[] outcomes = new int[finalists.size()];
        for (Deal deal : deals) {
            Search search = new Search(view, deal);
            for (int i = 0; i < finalists.size(); i++) {
                outcomes[i] += search.evaluate(finalists.get(i).option());
            }
        }
        int best = 0;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < finalists.size(); i++) {
            double score = finalists.get(i).score() + OUTCOME_WEIGHT * outcomes[i] / deals.size();
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return finalists.get(best);
    }

    private record Position(long first, long second, long third, int turn, int leader, int passes, Combination target) {}

    private static final class Search {
        private final View view;
        private final Deal deal;
        private final long[] hands = new long[3];
        private final Map<Position, Integer> solved = new HashMap<>();
        private int budget;

        Search(View view, Deal deal) {
            this.view = view;
            this.deal = deal;
            for (int seat = 0; seat < 3; seat++) hands[seat] = deal.hands()[seat].ranks;
        }

        int evaluate(Option option) {
            budget = NODES_PER_CANDIDATE;
            return apply(view.seat(), view.leader(), view.passes(), view.target(), option);
        }

        private int apply(int turn, int leader, int passes, Combination target, Option option) {
            int next = (turn + 1) % 3;
            if (option == null) {
                if (passes == 1) return solve(leader, -1, 0, null);
                return solve(next, leader, 1, target);
            }
            long before = hands[turn];
            hands[turn] -= option.ranks();
            int result = hands[turn] == 0 ? (view.ally(turn) ? 1 : -1) : solve(next, turn, 0, option.combo());
            hands[turn] = before;
            return result;
        }

        private int solve(int turn, int leader, int passes, Combination target) {
            Position position = new Position(hands[0], hands[1], hands[2], turn, leader, passes, target);
            Integer cached = solved.get(position);
            if (cached != null) return cached;
            if (budget-- <= 0) return 0;

            int goal = view.ally(turn) ? 1 : -1;
            boolean unresolved = false;
            for (Option option : deal.hands()[turn].options) {
                if (!HandPlan.contains(hands[turn], option.ranks()) || !option.combo().beats(target)) continue;
                int result = apply(turn, leader, passes, target, option);
                if (result == goal) {
                    solved.put(position, goal);
                    return goal;
                }
                if (result == 0) unresolved = true;
            }
            if (target != null) {
                int result = apply(turn, leader, passes, target, null);
                if (result == goal) {
                    solved.put(position, goal);
                    return goal;
                }
                if (result == 0) unresolved = true;
            }
            if (unresolved) return 0;
            solved.put(position, -goal);
            return -goal;
        }
    }
}
