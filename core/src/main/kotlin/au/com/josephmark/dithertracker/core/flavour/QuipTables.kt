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
        "Kept a one-lander",
        "Should have mulliganed",
        "Stuck on three lands",
        "Drew nothing but lands",
        "Turned out to be the threat",
        "Left mana up for nothing",
        "Held the wrong answer",
        "Had an answer. In the deck",
        "Board wipe was one turn late",
        "Tapped out at the worst time",
        "Was still setting up",
        "Durdled too long",
        "Attacked the wrong player",
        "Trusted a handshake deal",
        "Gave everyone a reason",
        "Public enemy by turn 3",
        "Deck was 'about a 7'",
        "Asked what it does. Too late",
        "Should have read the card",
        "Was one turn from winning",
        "Ramped into nothing",
        "Paid life for everything",
        "Life total was a resource",
        "Monarch for one whole turn",
        "Played around nothing",
        "The deck had other plans",
    )

    val koPoison = listOf(
        "Thought 9 was a safe number",
        "Ten is a very low number",
        "It was only a 1/1",
        "Didn't count the counters",
        "One proliferate too many",
        "Turns out poison is real",
        "Laughed at the infect deck",
        "Lethal in tiny instalments",
        "Died of ten papercuts",
        "Doesn't feel so good",
        "Life total: fine. Poison: no",
        "Healthy, apart from that",
    )

    val koCommanderDamage = listOf(
        "One creature. Lots of hats",
        "Counted to 21 the hard way",
        "Same commander, every turn",
        "Should have chump blocked",
        "Was saving the removal",
        "Killed it. It came back",
        "Ignored the commander",
        "Commander tax was too low",
        "Unblockable, as it turns out",
        "21 from the command zone",
        "Took it personally",
        "Thankfully someone was counting",
    )

    val koManual = listOf(
        "Scooped with dignity",
        "Declined to watch the combo",
        "Saw how this ends",
        "Strategic retreat",
        "Chose peace",
        "Has a train to catch",
        "Went to get snacks",
        "Shuffling up already",
        "Will be back next game",
    )

    // --- Table tools ---------------------------------------------------------------------------

    /** Caption under the chosen player's name (the name is drawn separately). */
    val startingPlayer = listOf(
        "Goes first. No pressure",
        "Goes first. Everyone noticed",
        "Gets the first land drop",
        "Opens. Make it a good land",
        "Kicks things off",
        "Sets the pace",
        "Has the floor",
        "Chosen at random. Honestly",
    )

    val d20Natural1 = listOf(
        "Not ideal",
        "Blame the die",
        "Retire this die",
        "Somebody breathed on it",
        "At least it's memorable",
    )

    val d20Natural20 = listOf(
        "Keep this die",
        "That's the good die",
        "Don't touch it again",
        "Nobody saw that coming",
        "Remember this moment",
    )

    /** Any roll that isn't a d20 natural 1 or 20; must make sense for one die or several. */
    val diceRoll = listOf(
        "Fair and square",
        "No take-backs",
        "Rolled in plain sight",
        "Could have been worse",
        "Make of that what you will",
    )

    val coinHeads = listOf(
        "Heads you win, probably",
        "Landed face up",
        "The coin smiles on you",
    )

    val coinTails = listOf(
        "Tails, as foretold",
        "Tails never fails, they say",
        "The coin is unmoved",
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
        val nameSets = listOf(QuipTables.nameSets.first())
    }
}
