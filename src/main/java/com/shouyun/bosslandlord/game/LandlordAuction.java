package com.shouyun.bosslandlord.game;

/** The two rivals bid against each other. The guest always joins the farmers. */
public final class LandlordAuction {
    public enum Step { PLAYER_CALL, BOSS_RESPONSE, PLAYER_ROB, COMPLETE }

    private Step step = Step.PLAYER_CALL;
    private int candidate = -1;

    public Step step() { return step; }
    public int candidate() { return candidate; }

    public boolean playerDecision(boolean claim) {
        if (step == Step.PLAYER_CALL) {
            candidate = claim ? 0 : -1;
            step = Step.BOSS_RESPONSE;
            return true;
        }
        if (step == Step.PLAYER_ROB) {
            if (claim) candidate = 0;
            step = Step.COMPLETE;
            return true;
        }
        return false;
    }

    public boolean bossDecision(boolean rob) {
        if (step != Step.BOSS_RESPONSE) return false;
        if (candidate == -1) {
            candidate = 1;
            step = Step.COMPLETE;
        } else if (rob) {
            candidate = 1;
            step = Step.PLAYER_ROB;
        } else {
            step = Step.COMPLETE;
        }
        return true;
    }

    public static boolean strongHand(int[] hand) {
        return CardAi.wantsLandlord(hand);
    }
}
