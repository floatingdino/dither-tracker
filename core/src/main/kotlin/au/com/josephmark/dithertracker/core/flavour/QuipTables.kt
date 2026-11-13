package au.com.josephmark.dithertracker.core.flavour

/**
 * The copy itself. This file is the source of truth for every quip; `design/copy/quips.md` mirrors
 * it for reading and is checked against it by `QuipsDocTest`.
 *
 * House style: sentence case, no trailing full stop (questions keep their `?`), no exclamation
 * marks, ASCII only. Budgets (enforced by `FlavourTest`): lines and captions <= 32 chars,
 * confirm prompts <= 2 wrapped lines of 32, button labels and names <= 10.
 */
internal object QuipTables {

    // --- Knocked out ---------------------------------------------------------------------------

    val koLife = listOf(
        "Beefed it",
        "Kept a one-lander",
        "Had an answer. In the deck",
        "Oof",
        "Board wipe was one turn late",
        "Folded like a lawn chair",
        "Should have mulliganed",
        "Big talk, small life total",
        "Durdled too long",
        "Got got",
        "Trusted a handshake deal",
        "Poked the wrong bear",
        "Deck was 'about a 7'",
        "Spectator mode unlocked",
        "Asked what it does. Too late",
        "Kind of had that coming",
        "Was one turn from winning",
        "Thoroughly cooked",
        "Monarch for one whole turn",
        "Down you go",
        "Turned out to be the threat",
        "That plan aged badly",
        "Tapped out at the worst time",
        "Lost the table vote",
        "Paid life for everything",
        "Oops. Anyway",
    )

    val koPoison = listOf(
        "Thought 9 was a safe number",
        "Poisoned. Gross",
        "It was only a 1/1",
        "Nibbled to death",
        "Laughed at the infect deck",
        "Full health, still dead",
        "One proliferate too many",
        "Wasn't counting, clearly",
        "Ten is a very low number",
        "Death by tally marks",
        "Doesn't feel so good",
        "Died of ten papercuts",
    )

    val koCommanderDamage = listOf(
        "One creature. Lots of hats",
        "Bonk. Twenty-one",
        "Counted to 21 the hard way",
        "Should have chump blocked",
        "Ugh, that commander again",
        "Was saving the removal",
        "Bullied by one commander",
        "Killed it. It came back",
        "Ignored it for too long",
        "Commander tax was too low",
        "Couldn't stop it. Didn't try",
        "Unblockable, as it turns out",
    )

    val koManual = listOf(
        "Scooped with dignity",
        "Rage quit, politely",
        "Declined to watch the combo",
        "Chose peace",
        "Has a train to catch",
        "Read the room. Left it",
        "Went to get snacks",
        "Bailed. Fair enough",
        "Sure, 'had to go'",
    )

    // --- Table tools ---------------------------------------------------------------------------

    /** Caption under the chosen player's name (the name is drawn separately). */
    val startingPlayer = listOf(
        "Goes first. No pressure",
        "Don't blow it",
        "Opens. Make it a good land",
        "First in, first targeted",
        "Off you go then",
        "Goes first. Everyone noticed",
        "Clock's ticking",
        "Chosen at random. Honestly",
    )

    val d20Natural1 = listOf(
        "Yikes",
        "Blame the die",
        "Nailed it",
        "Somebody breathed on it",
        "Ooh, unlucky",
        "Retire this die",
    )

    val d20Natural20 = listOf(
        "Show-off",
        "Keep this die",
        "Okay, calm down",
        "Don't touch it again",
        "Peaked early",
        "Nobody saw that coming",
    )

    /** Any roll that isn't a d20 natural 1 or 20; must make sense for one die or several. */
    val diceRoll = listOf(
        "Fair and square",
        "Thrilling stuff",
        "No take-backs",
        "Could have been worse",
        "Sure, that'll do",
        "Make of that what you will",
    )

    val coinHeads = listOf(
        "Heads you win, probably",
        "Heads, obviously",
        "The coin smiles on you",
        "Heads. Don't gloat",
    )

    val coinTails = listOf(
        "Tails. Sorry, not sorry",
        "Tails, as foretold",
        "The coin is unmoved",
        "Tails, sadly",
    )

    // --- Reset / new game ----------------------------------------------------------------------

    /** Every prompt must plainly ask "start the game over?"; any button pair can follow any prompt. */
    val resetPrompt = listOf(
        "Start over? Everyone back to full life",
        "Reset the table? No hard feelings",
        "Clear the board and start fresh?",
        "Reset everything? Grudges not included",
        "Start a new game? This one gets wiped",
        "New game? Shuffle up, we go again",
    )

    /** (confirm, cancel). The confirm label alone must read as "reset". */
    val resetButtons = listOf(
        ConfirmButtons("Start over", "Keep going"),
        ConfirmButtons("New game", "Not yet"),
        ConfirmButtons("Reset", "Hold on"),
        ConfirmButtons("Reset it", "Leave it"),
    )

    // --- History / end of game -----------------------------------------------------------------

    val emptyHistory = listOf(
        "Nothing yet. Suspiciously quiet",
        "Nothing has happened. Yet",
        "Nobody has done anything yet",
        "All quiet on the battlefield",
    )

    /** Caption under the winner's name (the name is drawn separately). */
    val winner = listOf(
        "Last one standing",
        "Outlasted the table",
        "Survived the politics",
        "Was the threat all along",
        "Gracious in victory, hopefully",
        "Wins. Rematch?",
        "Wins. Salt levels rising",
        "Picks the snacks next time",
    )

    // --- Commander damage: the targeted player ------------------------------------------------

    /**
     * Drawn large on the targeted player's tile while the others count their commander damage
     * (T30). Voice: the friend across the table heckling a little — blunt, playful, never mean. Budget: <= 16 chars ([TARGET_MAX]) so it can be drawn at a big `ui` scale.
     */
    val commanderTarget = listOf(
        "Uh oh",
        "Whoops",
        "It's you",
        "Big mistake",
        "Here it comes",
        "This'll sting",
        "Not your day",
        "Should've ducked",
        "Hold still",
        "Called it",
        "Brace yourself",
    )

    const val TARGET_MAX = 16

    // --- Default player names ------------------------------------------------------------------

    /** The first set is the default (and the only set when quips are off). */
    val nameSets = listOf(
        NameSet("Players", List(6) { "Player ${it + 1}" }),
        NameSet("Colours", listOf("White", "Blue", "Black", "Red", "Green", "Colourless")),
        NameSet("Archetypes", listOf("Aggro", "Control", "Combo", "Midrange", "Stax", "Group Hug")),
        NameSet("Turn", listOf("Untap", "Upkeep", "Draw", "Main", "Combat", "End Step")),
        NameSet("Usual cast", listOf("The Threat", "Durdler", "Kingmaker", "Topdecker", "Politician", "Rule Zero")),
    )

    // --- Quips off -----------------------------------------------------------------------------

    object Plain {
        val koLife = listOf("Knocked out")
        val koPoison = listOf("Knocked out: poison")
        val koCommanderDamage = listOf("Knocked out: commander damage")
        val koManual = listOf("Marked out")
        val startingPlayer = listOf("Goes first")
        val d20Natural1 = listOf("Natural 1")
        val d20Natural20 = listOf("Natural 20")
        val diceRoll = listOf("Rolled")
        val coinHeads = listOf("Heads")
        val coinTails = listOf("Tails")
        val resetPrompt = listOf("Reset game?")
        val resetButtons = listOf(ConfirmButtons("Yes", "No"))
        val emptyHistory = listOf("No history yet")
        val winner = listOf("Wins")
        val commanderTarget = listOf("Target")
        val nameSets = listOf(QuipTables.nameSets.first())
    }
}
