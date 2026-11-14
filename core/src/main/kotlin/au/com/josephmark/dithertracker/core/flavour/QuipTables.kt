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

    // Since T42 a KO or winner line replaces the life total, drawn large: short lines draw biggest
    // (<= 9 chars per line at k = 4 on the Nomad, <= 12 at k = 3). Each list ends with its verbatim
    // 8/16-bit game lines ([verbatim]) — at least half of every KO and winner list; sources in
    // `design/copy/quips.md`, the wider bench in `design/copy/retro-candidates.md`.

    val koLife = listOf(
        "Beefed it",
        "Kept a one-lander",
        "Oof",
        "Folded like a lawn chair",
        "Should have mulliganed",
        "Durdled too long",
        "Got got",
        "Trusted a handshake deal",
        "Poked the wrong bear",
        "Deck was 'about a 7'",
        "Spectator mode unlocked",
        "Thoroughly cooked",
        "Lost the table vote",
        "Oops. Anyway",
        // verbatim
        "Thou art dead.",
        "You die...",
        "Somebody set up us the bomb.",
        "Oh no!",
        "Unit lost.",
        "Too late for that.",
        "A valiant attempt.",
        "Your prayers are not heard.",
        "Shoot! It was so close too!",
        "Stay a while... stay forever!",
        "But nothing happened.",
        "Critical hit!",
        "I feel asleep!!",
        "You fight like a dairy farmer.",
        "Rise from your grave!",
        "Barf!",
    )

    val koPoison = listOf(
        "Poisoned. Gross",
        "It was only a 1/1",
        "Nibbled to death",
        "Ten is a very low number",
        "Died of ten papercuts",
        // verbatim
        "The poison was deadly...",
        "It's not very effective...",
        "Buy medicine before you go.",
        "Your life force is running out",
        "Time passes...",
    )

    val koCommanderDamage = listOf(
        "One creature. Lots of hats",
        "Bonk. Twenty-one",
        "Should have chump blocked",
        "Ugh, that commander again",
        "Was saving the removal",
        "Bullied by one commander",
        "Killed it. It came back",
        "Ignored it for too long",
        // verbatim
        "All your base are belong to us.",
        "It's super effective!",
        "Welcome to your doom!",
        "One-hit KO!",
        "Excellent move!",
        "Toasty!",
        "You spoony bard!",
        "Son of a submariner!",
        "I hunger!",
        "It's you !!",
        "Can't escape!",
        "What you say !!",
        "The truck have started to move!",
    )

    val koManual = listOf(
        "Scooped with dignity",
        "Rage quit, politely",
        "Chose peace",
        "Has a train to catch",
        "Went to get snacks",
        // verbatim
        "Chickening out... already?",
        "You run THAT fast?",
        "Got away safely!",
        "Do you wish to leave the game?",
        "go ahead and leave.",
        "The enemy is running away.",
        "Leave at once!",
        "It's a secret to everybody.",
    )

    // --- Table tools ---------------------------------------------------------------------------

    /** The FIRST flag on the roll-off's winner (T59: informative — who starts — in the table's voice). */
    val startingPlayer = listOf(
        "On the play",
        "First out the gates",
        "You lead off",
        "Kick us off",
        "Top of the order",
        "Floor's yours",
        "Opening act",
        "Turn one is yours",
        "Set the pace",
        "Lead the charge",
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
        "Reset the game? Sore losers welcome",
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
        "Go on, do something",
        "Nothing has happened. Yet",
        "Waiting on you lot",
    )

    /** Caption under the winner's name (the name is drawn separately). */
    val winner = listOf(
        "Last one standing",
        "Never in doubt",
        "Survived the politics",
        "Told you so",
        "Wins. Rematch?",
        "Too easy",
        // verbatim
        "A winner is you",
        "Conglaturation !!!",
        "You have completed a great game.",
        "You won!",
        "The legends have proven true.",
        "For great justice.",
        "Course clear!",
        "Yippee!",
        "Hurrah!",
        "Now go and rest our heroes !",
        "I've not seen such bravery",
        "Thou art truly brave.",
        "Do you expect me to applaud?",
        "Boy, you're rich!",
        "Go home and be a family man!",
        "How are you gentlemen !!",
    )

    /**
     * Lines quoted verbatim from 8- and 16-bit era games (T42; Sam: "game quotes should be
     * verbatim"). Their own punctuation is part of the quote, so the house style's "no exclamation
     * marks, no closing full stop" rules don't apply to them (`FlavourTest`). Sources: the "Sources"
     * table in `design/copy/quips.md`.
     */
    val verbatim: Set<String> = setOf(
        "Thou art dead.",
        "You die...",
        "Somebody set up us the bomb.",
        "Oh no!",
        "Unit lost.",
        "Too late for that.",
        "A valiant attempt.",
        "Your prayers are not heard.",
        "Shoot! It was so close too!",
        "Stay a while... stay forever!",
        "But nothing happened.",
        "Critical hit!",
        "I feel asleep!!",
        "You fight like a dairy farmer.",
        "Rise from your grave!",
        "Barf!",
        "The poison was deadly...",
        "It's not very effective...",
        "Buy medicine before you go.",
        "Your life force is running out",
        "Time passes...",
        "All your base are belong to us.",
        "It's super effective!",
        "Welcome to your doom!",
        "One-hit KO!",
        "Excellent move!",
        "Toasty!",
        "You spoony bard!",
        "Son of a submariner!",
        "I hunger!",
        "It's you !!",
        "Can't escape!",
        "What you say !!",
        "The truck have started to move!",
        "Chickening out... already?",
        "You run THAT fast?",
        "Got away safely!",
        "Do you wish to leave the game?",
        "go ahead and leave.",
        "The enemy is running away.",
        "Leave at once!",
        "It's a secret to everybody.",
        "A winner is you",
        "Conglaturation !!!",
        "You have completed a great game.",
        "You won!",
        "The legends have proven true.",
        "For great justice.",
        "Course clear!",
        "Yippee!",
        "Hurrah!",
        "Now go and rest our heroes !",
        "I've not seen such bravery",
        "Thou art truly brave.",
        "Do you expect me to applaud?",
        "Boy, you're rich!",
        "Go home and be a family man!",
        "How are you gentlemen !!",
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
