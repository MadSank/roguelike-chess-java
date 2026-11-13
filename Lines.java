import java.util.Random;

/**
 * What the masked characters say. Each line is {narration, speech}: what you see (ash grey),
 * then what he says (cream, in quotes). Either part may be empty.
 * Opponents are indexed 0..4 (Rag, Tape, Box, Bag, Pillow: prisoners forced to play); dealers 0..2 (card, piece, reseller).
 */
public final class Lines {
    private Lines() {}

    private static final Random RNG = new Random();

    private static String[] pick(String[][] options) {
        return options[RNG.nextInt(options.length)];
    }

    // ─── Opponents ──────────────────────────────────────────────
    private static final String[][][] GREET = {
        {{"The goggles fog up. He wipes them with a shaking thumb.", "They said whoever loses doesn't leave. Sorry."},
         {"The knot of his rag has come loose. He doesn't fix it.", "Just play. Don't look at them."}},
        {{"Only his eyes move between the strips of tape.", "I've won four. I'm not stopping at you."},
         {"The tape crackles when he breathes.", "Sit. They're watching."}},
        {{"Someone drew that smile on the box. It wasn't him.", "Do you know the rules? I don't. Not all of them."},
         {"The cardboard is soft at the corners from sweat.", "Let's make it quick. For both of us."}},
        {{"The paper bag rustles every time he swallows.", "I used to play in the park. Before."},
         {"He folds his hands to stop them shaking.", "Don't feel sorry for me. Just play."}},
        {{"The face on the pillowcase grins. He doesn't.", "I sleep in this. When they let me."},
         {"The string around his neck is pulled tight.", "Someone has to win. It's going to be me."}},
    };

    private static final String[][][] TALK = {
        {{"", "Count them. Five behind you, four behind me."}, {"", "How long have you been here? I lost track."},
         {"He glances at the guard with the beak.", "That one never blinks."}},
        {{"", "Every game I win, they bring me a better one."}, {"", "Don't talk to the guards. They don't answer."},
         {"He picks at the tape by his ear.", "It pulls my hair when I take it off."}},
        {{"", "There's a window upstairs. You can see the lights."}, {"", "I had a name. It's written on the box somewhere."},
         {"He tilts the box at the guards.", "They bet on us, you know."}},
        {{"", "My brother sat in that chair. Last week."}, {"The bag goes still.", "Did you hear something?"},
         {"", "When this is over, I'm walking out the front."}},
        {{"", "The smiling one picked me. He picks all of us."}, {"", "Win or lose, keep your mask on."},
         {"He touches the drawn grin.", "My daughter drew it. For a joke."}},
    };

    private static final String[][] HURT = {
        {"He flinches.", "No. No, no."}, {"He looks at the guards, not at you.", ""},
        {"His breath catches.", "That was mine."}, {"", "Okay. Okay. It's fine."},
    };
    private static final String[][] CAPTURE = {
        {"He takes your piece and won't look up.", "Sorry."}, {"", "That's one."},
        {"He sets your piece down carefully, like it might break.", ""}, {"", "I have to. You know I have to."},
    };
    private static final String[][] CHECK = {
        {"He leans over the table.", "Check. Please move."}, {"", "Your king. Look at your king."}, {"", "Check."},
    };
    private static final String[][] WIN = {
        {"He stands up too fast. The guards don't move.", "I'm sorry. I'm sorry."}, {"The room goes very quiet.", "Checkmate."},
    };
    private static final String[][] LOSE = {
        {"His hands fall off the table.", "...Good game."}, {"A guard's hand settles on his shoulder.", "Wait. Wait, one more."},
    };
    private static final String[][] DRAW = {
        {"He laughs, once, without meaning to.", "Nobody loses."}, {"The guards look at each other.", "Again, then."},
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
