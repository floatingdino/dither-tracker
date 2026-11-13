# Retro candidates — verbatim 8/16-bit game lines (T42)

The bench for the retro register on the KO and winner tiles (SPEC "Tone": *Retro voice for death and
victory* — famous lines quoted **verbatim**). Every row was checked word-for-word against the source
named; disassemblies and released source code first, then transcripts and localisation write-ups,
listicles never on their own. Lines are drawn in caps on the tile, so casing below is as the game
showed it (voice lines have conventional punctuation). Budget: at most 32 characters, no character /
game / place / company names, kid-safe, only glyphs the `ui` face has.

**Shipped** = in `QuipTables.kt` now (mirrored in `quips.md`, which also has a per-line sources
table). Everything else is a candidate for Sam to pick from. Ranked by fit within each group.

Source shorthands: *pokered* = github.com/pret/pokered `data/text/*.asm`; *DW disasm* =
github.com/nmikstas/dragon-warrior-disassembly `Bank02.asm`; *Doom src* = github.com/id-Software/DOOM
`d_englsh.h` / `dstrings.c`; *Wolf src* = github.com/id-Software/wolf3d `WL_MENU.C`; *Zork src* =
github.com/historicalsource/zork1 `gverbs.zil` / `1actions.zil`; *NetHack src* = github.com/NetHack
(current source; long-standing strings); *AGS wiki* = adventuregamestudio.co.uk/wiki/Insult_Swordfighting;
*LoL* = legendsoflocalization.com.

Confidence: **code** = string in a disassembly or source; **verified** = a transcript / reference page
quotes it; **voice** = spoken line, words confirmed; **uncertain** = only secondary sources or they
disagree (never shipped).

## KO: life

| Line | Game | Year, platform | Verified against | Conf. | Fit | Shipped |
|---|---|---|---|---|---|---|
| Thou art dead. | Dragon Warrior | 1989, NES | DW disasm | code | The classic death line | yes |
| You die... | NetHack | 1987, PC | NetHack src `end.c` | code | Dry and final | yes |
| Somebody set up us the bomb. | Zero Wing | 1991, Mega Drive | Wikiquote | verified | Ambushed by a combo | yes |
| Oh no! | Lemmings | 1991, Amiga/PC | Retro Gamer #39; MobyGames | voice | Short, sweet doom | yes |
| Unit lost. | Command & Conquer | 1995, DOS | PCGamesN (EVA lines) | voice | Cold report | yes |
| Too late for that. | Zork I | 1980, PC | Zork src | code | No more saves | yes |
| A valiant attempt. | Zork I | 1980, PC | Zork src | code | Kind eulogy | yes |
| Your prayers are not heard. | Zork I | 1980, PC | Zork src (said while dead) | code | The fog didn't come | yes |
| Shoot! It was so close too! | Pokemon Red/Blue | 1998, Game Boy | pokered `text_6` | code | Died one point short | yes |
| Stay a while... stay forever! | Impossible Mission | 1984, C64 | Wikipedia | voice | Welcome to the graveyard | yes |
| But nothing happened. | Dragon Warrior | 1989, NES | DW disasm | code | The last-ditch play | yes |
| Critical hit! | Pokemon Red/Blue | 1998, Game Boy | pokered `text_2` | code | The swing that did it | yes |
| I FEEL ASLEEP!! | Metal Gear | 1988, NES | Wikipedia (NES text all caps) | verified | A long nap | yes |
| You fight like a dairy farmer. | The Secret of Monkey Island | 1990, PC | AGS wiki; Wikiquote | verified | The table's parting jab | yes |
| Rise from your grave! | Altered Beast | 1988, arcade / MD | VGMRips track list; Wikipedia | voice | Ironic; hints at revive | yes |
| BARF! | River City Ransom | 1990, NES | LoL "River City Ransom's BARF!" | verified | Comic KO cry | yes |
| Aww! It appeared to be caught! | Pokemon Red/Blue | 1998, Game Boy | pokered `text_6` | code | "I was stable!" | |

## KO: poison

| Line | Game | Year, platform | Verified against | Conf. | Fit | Shipped |
|---|---|---|---|---|---|---|
| The poison was deadly... | NetHack | 1987, PC | NetHack src `attrib.c` | code | Exactly the moment | yes |
| It's not very effective... | Pokemon Red/Blue | 1998, Game Boy | pokered `text_2` | code | Small doses add up | yes |
| BUY MEDICINE BEFORE YOU GO. | The Legend of Zelda | 1987, NES | LoL Zelda comparison | verified | Should've packed the antidote | yes |
| Your life force is running out | Gauntlet | 1985, arcade | Wikipedia | voice | The slow drain | yes |
| Time passes... | Zork I | 1980, PC | Zork src | code | Counter by counter | yes |
| You have died of dysentery. | The Oregon Trail | 1985, Apple II | secondary sources only | uncertain | Death by slow sickness | |

## KO: commander damage (one big repeated attacker / the boss)

| Line | Game | Year, platform | Verified against | Conf. | Fit | Shipped |
|---|---|---|---|---|---|---|
| All your base are belong to us. | Zero Wing | 1991, Mega Drive | Wikiquote; Wikipedia | verified | The commander owns you | yes |
| It's super effective! | Pokemon Red/Blue | 1998, Game Boy | pokered `text_2` | code | That general again | yes |
| Welcome to your doom! | Altered Beast | 1988, arcade / MD | VGMRips track list | voice | The boss's greeting | yes |
| One-hit KO! | Pokemon Red/Blue | 1998, Game Boy | pokered `text_2` | code | Big general swing | yes |
| Excellent move! | Dragon Warrior | 1989, NES | DW disasm (critical hit) | code | Credit to the attacker | yes |
| Toasty! | Mortal Kombat II | 1993, arcade | MK Wiki; PC Gamer | voice | Roasted by the general | yes |
| You spoony bard! | Final Fantasy II (US) | 1991, SNES | LoL "You Spoony Bard" | verified | Insult from the boss | yes |
| Son of a submariner! | Final Fantasy III (US) | 1994, SNES | Wikipedia "Ted Woolsey" | verified | Loser's outburst | yes |
| I hunger! | Sinistar | 1982, arcade | Wikipedia | voice | A commander that eats players | yes |
| It's you !! | Zero Wing | 1991, Mega Drive | Wikiquote | verified | Of course it was them | yes |
| Can't escape! | Pokemon Red/Blue | 1998, Game Boy | pokered `text_2` | code | Cornered | yes |
| WHAT YOU SAY !! | Zero Wing | 1991, Mega Drive | Wikiquote | verified | Disbelief at 21 | yes |
| THE TRUCK HAVE STARTED TO MOVE! | Metal Gear | 1988, NES | Wikipedia (own sentence after "Uh-oh!") | verified | Run over by the general | yes |
| Run, coward! | Sinistar | 1982, arcade | Wikipedia | voice | Boss taunt | |
| Beware, I live | Sinistar | 1982, arcade | Wikipedia | voice | The general hits the table | |
| Let's see you get out of here | Gauntlet | 1985, arcade | Wikipedia | voice | Boss taunt | |
| Berserk! | Doom | 1993, DOS | Doom src | code | Rampage | |
| SMAAAASH!! | EarthBound | 1995, SNES | EarthBound wiki (A count unconfirmed) | uncertain | One big hit | |
| Fatality | Mortal Kombat | 1992, arcade | no transcript | uncertain | The 21st point | |

## Conceded / quit

| Line | Game | Year, platform | Verified against | Conf. | Fit | Shipped |
|---|---|---|---|---|---|---|
| Chickening out... already? | Wolfenstein 3D | 1992, DOS | Wolf src | code | It's a quit prompt | yes |
| You run THAT fast? | The Secret of Monkey Island | 1990, PC | AGS wiki | verified | Mocks the retreat | yes |
| Got away safely! | Pokemon Red/Blue | 1998, Game Boy | pokered `text_2` | code | Escaped alive | yes |
| Do you wish to leave the game? | Zork I | 1980, PC | Zork src | code | The real quit prompt | yes |
| go ahead and leave. | Doom | 1993, DOS | Doom src `dstrings.c` | code | Quit message (lower case original) | yes |
| The enemy is running away. | Dragon Warrior | 1989, NES | DW disasm | code | From the winners' side | yes |
| Leave at once! | Dragon Warrior | 1989, NES | DW disasm | code | Out you go | yes |
| IT'S A SECRET TO EVERYBODY. | The Legend of Zelda | 1987, NES | LoL; Wikiquote | verified | Why they left | yes |
| see if i care. | Doom | 1993, DOS | Doom src `dstrings.c` | code | Second half of the same quit line | |
| Wimps, press Y. | Wolfenstein 3D | 1992, DOS | Wolf src | code | Quit prompt (key makes no sense here) | |
| Why, did you want to borrow one? | The Secret of Monkey Island | 1990, PC | AGS wiki | verified | The "escape boat" comeback | |
| You fight like a cow | The Secret of Monkey Island | 1990, PC | AGS wiki (end punctuation unconfirmed) | uncertain | The comeback | |

## Winner

| Line | Game | Year, platform | Verified against | Conf. | Fit | Shipped |
|---|---|---|---|---|---|---|
| A WINNER IS YOU | Pro Wrestling | 1987, NES (US) | Wikipedia; TCRF (regional variants differ) | verified | The definitive one | yes |
| CONGLATURATION !!! | Ghostbusters | 1988, NES | LoL (?p=9672) | verified | Famous ending typo | yes |
| YOU HAVE COMPLETED A GREAT GAME. | Ghostbusters | 1988, NES | LoL (?p=9672) | verified | Exactly 32 chars | yes |
| YOU WON! | EarthBound | 1995, SNES | LoL EarthBound battles | verified | Clean | yes |
| The legends have proven true. | Dragon Warrior | 1989, NES | DW disasm | code | Epic | yes |
| FOR GREAT JUSTICE. | Zero Wing | 1991, Mega Drive | Wikiquote | verified | Righteous win | yes |
| COURSE CLEAR! | Super Mario Bros. 3 | 1990, NES | Mario Wiki "Goal" | verified | Table cleared | yes |
| Yippee! | Lemmings | 1991, Amiga/PC | Retro Gamer #39 | voice | The survivor's cheer | yes |
| Hurrah! | Dragon Warrior | 1989, NES | DW disasm | code | Plain joy | yes |
| NOW GO AND REST OUR HEROES ! | Ghostbusters | 1988, NES | LoL (?p=9672) | verified | Game over, go home | yes |
| I've not seen such bravery | Gauntlet | 1985, arcade | Wikipedia | voice | Praise | yes |
| Thou art truly brave. | Dragon Warrior | 1989, NES | DW disasm | code | Kingly praise | yes |
| Do you expect me to applaud? | Zork I | 1980, PC | Zork src | code | Snarky win | yes |
| BOY, YOU'RE RICH! | The Legend of Zelda | 1987, NES | LoL Zelda comparison | verified | Rich in life | yes |
| Go home and be a family man! | Street Fighter II | 1991, arcade | LoL (?p=944) (casing unconfirmed) | verified | Sends the table home | yes |
| HOW ARE YOU GENTLEMEN !! | Zero Wing | 1991, Mega Drive | Wikiquote | verified | Gloat | yes |
| I am glad thou hast returned. | Dragon Warrior | 1989, NES | DW disasm | code | Repeat winner | |
| Invulnerability! | Doom | 1993, DOS | Doom src | code | Untouchable | |
| Flawless victory | Mortal Kombat | 1992, arcade | no transcript | uncertain | No damage taken | |

## Other spots (for later: T25b call sites)

| Line | Game | Year, platform | Verified against | Conf. | Spot / fit |
|---|---|---|---|---|---|
| Do you wish to restart? | Zork I | 1980, PC | Zork src | code | Reset confirm: the real prompt |
| Main screen turn on. | Zero Wing | 1991, Mega Drive | Wikiquote | verified | Reset: new board |
| IT'S DANGEROUS TO GO ALONE! | The Legend of Zelda | 1987, NES | LoL; Wikiquote | verified | Reset / new game |
| TAKE THIS. | The Legend of Zelda | 1987, NES | LoL; Wikiquote | verified | Reset: fresh life totals |
| This isn't the time to use that! | Pokemon Red/Blue | 1998, Game Boy | pokered `text_6` | code | Reset: are you sure |
| It is pitch black. | Zork I | 1980, PC | Wikipedia "Zork I" | verified | Empty history |
| Looks like there's nothing here. | Pokemon Red/Blue | 1998, Game Boy | pokered `text_1` | code | Empty history |
| You have no possessions. | Zork I | 1980, PC | Zork src | code | Empty history |
| Nothing happened! | Pokemon Red/Blue | 1998, Game Boy | pokered `text_3` | code | Empty history |
| Beats me. | Zork I | 1980, PC | Zork src | code | Empty history |
| HERE COMES A NEW CHALLENGER! | Street Fighter II | 1991, arcade | Wikipedia | verified | Starting player |
| Let's go! | Lemmings | 1991, Amiga/PC | Retro Gamer #39 | voice | Starting player |
| Another visitor. | Impossible Mission | 1984, C64 | Wikipedia | voice | Starting player |
| We get signal. | Zero Wing | 1991, Mega Drive | Wikiquote | verified | Starting player |
| TAKE ANY ONE YOU WANT. | The Legend of Zelda | 1987, NES | LoL | verified | Starting player: random pick |
| Test your might | Mortal Kombat | 1992, arcade | Wikipedia (casing unconfirmed) | verified | Starting player |
| I AM ERROR. | Zelda II | 1988, NES | LoL; Wikipedia | verified | d20 natural 1 ("Error" is a name, reads as a word) |
| But, it failed! | Pokemon Red/Blue | 1998, Game Boy | pokered `text_3` | code | d20 natural 1 |
| The spell will not work. | Dragon Warrior | 1989, NES | DW disasm | code | d20 natural 1 |
| But that spell has been blocked. | Dragon Warrior | 1989, NES | DW disasm | code | d20 natural 1 (32 chars) |
| Nice try. | Zork I | 1980, PC | Zork src | code | d20 natural 1 |
| It won't have any effect. | Pokemon Red/Blue | 1998, Game Boy | pokered `text_6` | code | d20 natural 1 |
| Thou art asleep. | Dragon Warrior | 1989, NES | DW disasm | code | d20 natural 1 |
| You are lucky! | NetHack | 1987, PC | NetHack src `allmain.c` | code | d20 natural 20 |
| Supercharge! | Doom | 1993, DOS | Doom src | code | d20 natural 20 |
| Wheeeeeeeeee!!!!! | Zork I | 1980, PC | Zork src | code | d20 natural 20 |
| Power up! | Altered Beast | 1988, arcade / MD | VGMRips track list | voice | d20 natural 20 |
| LET'S PLAY MONEY MAKING GAME. | The Legend of Zelda | 1987, NES | LoL | verified | Dice / coin |
| HOW HIGH CAN YOU GET ? | Donkey Kong | 1981, arcade | TCRF (space before ?) | verified | d20 natural 20 |
| Very Happy Ammo Added | Doom | 1993, DOS | Doom src | code | d20 natural 20 |
| PAY ME AND I'LL TALK. | The Legend of Zelda | 1987, NES | LoL | verified | Spare |
| Reinforcements have arrived. | Command & Conquer | 1995, DOS | PCGamesN | voice | A player joins |
| But wait... | NetHack | 1987, PC | NetHack src | code | Undo |
| Don't be a thief! | Pokemon Red/Blue | 1998, Game Boy | pokered | code | Spare |

## Rejected

- **Too long:** "What a horrible night to have a curse." (38; wording confirmed — Sam's example), "You are on the
  way to destruction." (34), "You have no chance to survive make your time.", "But our princess is in
  another castle!", "But... the future refused to change.", "The poison doesn't seem to affect you."
- **Names:** "Take off every 'ZIG'!!", "Move 'ZIG'.", "Elf needs food badly" / "Warrior needs food, badly",
  "Valkyrie is about to die", "You must defeat Sheng Long…", "I'm Nester, don't tell me!".
- **Out of era / misattributed:** "Do a barrel roll!" (1997), "Hey! Listen!" (1998); "But nothing
  happened!" is Pokemon Gen 3 (Gen 1 Splash says "No effect!") — shipped instead as Dragon Warrior's
  "But nothing happened.".
- **Other:** "Finish him!" (gendered), Q*bert's cursing (needs `@`), Doom's harsher quit lines (kid-safe).
- **Not verified** (no transcript found; worth a second pass): Metroid "GREAT !!", Galaga "FIGHTER
  CAPTURED", NBA Jam "He's on fire!", Killer Instinct "C-C-C-COMBO BREAKER", Diablo "Stay awhile and
  listen." (also 1996/97), Warcraft II "Job's done!", Ghosts 'n Goblins "MAKE RAPID PROGRESS!",
  "You have died of dysentery." (Oregon Trail; only secondary sources), Mortal Kombat "Fatality" /
  "Flawless victory" (words certain, no transcript for on-screen form).
