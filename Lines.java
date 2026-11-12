import java.util.Random;

/**
 * What the masked characters say. Each line is {narration, speech}: what you see (ash grey),
 * then what he says (cream, in quotes). Either part may be empty.
 * Opponents are indexed 0..4 (Gasman, Doctor, Goalie, Sack, Welder); dealers 0..2 (card, piece, reseller).
 */
public final class Lines {
    private Lines() {}

    private static final Random RNG = new Random();

    private static String[] pick(String[][] options) {
        return options[RNG.nextInt(options.length)];
    }

    // ─── Opponents ──────────────────────────────────────────────
    private static final String[][][] GREET = {
        {{"He drums his fingers on the table.", "Sit. Your king looks lonely."},
         {"The filter rattles with every breath.", "Back again. The air down here suits you."}},
        {{"The beak tilts toward your pieces.", "I have cured worse openings than yours."},
         {"He pulls his gloves tight, one finger at a time.", "Shall we begin the examination?"}},
        {{"He leans in. You can hear him breathing behind the holes.", "Your move. Don't keep me waiting."},
         {"The mask is scratched where something hit it.", "I never let anything past me."}},
        {{"The sack rustles. Something under it is smiling.", "Play. I'll be gentle. At first."},
         {"The rope around his neck creaks.", "I've been waiting in the dark so long."}},
        {{"Green light flickers behind the glass.", "Let's burn through this."},
         {"The helmet clanks as he nods.", "Sparks fly. Pieces melt. Sit."}},
    };

    private static final String[][][] TALK = {
        {{"", "Breathe slow. Panic wastes air."}, {"", "Everything down here rusts. Even kings."},
         {"He taps the filter.", "You get used to the smell."}},
        {{"", "Every piece you lose, I will study."}, {"", "Bleeding is only the body thinking."},
         {"The beak clicks.", "Hold still."}},
        {{"", "I've stopped harder shots than yours."}, {"", "Keep your head up. Or don't."},
         {"He knocks on the mask.", "Still in one piece."}},
        {{"", "I can't see the board. I don't need to."}, {"Something shifts under the burlap.", ""},
         {"", "Do you hear that? No? Good."}},
        {{"", "Heat makes everything honest."}, {"", "Don't look straight at the light."},
         {"The glass glows a little brighter.", "Closer."}},
    };

    private static final String[][] HURT = {
        {"The filter wheezes.", "Lucky."}, {"He hisses behind the mask.", "That will cost you."},
        {"His fingers stop drumming.", ""}, {"", "Hm. Noted."},
    };
    private static final String[][] CAPTURE = {
        {"He takes your piece slowly.", "Mine now."}, {"", "One less."},
        {"He sets your piece beside him, facing you.", ""}, {"", "Did that hurt?"},
    };
    private static final String[][] CHECK = {
        {"He leans over the table.", "Check."}, {"", "Your king is sweating."}, {"", "Run, little king."},
    };
    private static final String[][] WIN = {
        {"He rises out of his chair.", "Mine."}, {"The lamp swings. He doesn't blink.", "Checkmate."},
    };
    private static final String[][] LOSE = {
        {"He slumps. The lamp flickers.", "...Well played."}, {"His head drops.", "Go, then. Before it goes dark."},
    };
    private static final String[][] DRAW = {
        {"He sits back.", "Nobody wins tonight."}, {"He tilts his head.", "Again, then."},
    };

    public static String[] greet(int opp) { return pick(GREET[clamp(opp)]); }
    public static String[] talk(int opp) { return pick(TALK[clamp(opp)]); }
    public static String[] hurt() { return pick(HURT); }
    public static String[] capture() { return pick(CAPTURE); }
    public static String[] check() { return pick(CHECK); }
    public static String[] win() { return pick(WIN); }
    public static String[] lose() { return pick(LOSE); }
    public static String[] draw() { return pick(DRAW); }

    private static int clamp(int i) { return Math.max(0, Math.min(4, i)); }

    // ─── Dealers ────────────────────────────────────────────────
    private static final String[][][] DEALER_GREET = {
        {{"He fans a deck across the counter.", "Every card has a price. Even you."},
         {"The white mask doesn't move when he talks.", "Pick one. Any one."}},
        {{"Pieces stand in rows on his counter, like soldiers.", "Fresh from the lathe. Mostly."},
         {"He raps the counter with a knuckle.", "Need an army? I have bodies."}},
        {{"Only his eyes show.", "I'll take anything off your hands. Half price."},
         {"He counts coins without looking at them.", "Selling? I'm always buying."}},
    };
    private static final String[][] YES = {
        {"He nods.", "A fine choice."}, {"The coins vanish under his hand.", "Done."}, {"", "Pleasure."},
    };
    private static final String[][] NO = {
        {"He shakes his head.", "Come back with more gold."}, {"", "No."}, {"He taps the price.", "Can't afford it."},
    };
    private static final String[][][] DEALER_TALK = {
        {{"", "Cards burn once. Use them well."}, {"", "Luck is a card you can buy."}},
        {{"", "A queen won't save you. More pawns might."}, {"", "Everything I sell, the dark takes back."}},
        {{"", "Everything sells for half. Fair is a word for upstairs."}, {"", "Empty hands are light hands."}},
    };

    public static String[] dealerGreet(int d) { return pick(DEALER_GREET[d]); }
    public static String[] dealerTalk(int d) { return pick(DEALER_TALK[d]); }
    public static String[] yes() { return pick(YES); }
    public static String[] no() { return pick(NO); }
}
