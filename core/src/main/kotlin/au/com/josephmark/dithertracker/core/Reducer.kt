package au.com.josephmark.dithertracker.core

/**
 * Pure rules: applies [action] to [state].
 *
 * Guarantees relied on by the UI and [History]:
 * - A no-op returns the identical [state] instance.
 * - Players that did not change keep their identical [Player] instance.
 * - A [KoOverride.ALIVE] override is cleared when the player's auto-KO condition flips
 *   (they recover, so the override is no longer needed, or they die again from a fresh change).
 */
fun reduce(state: GameState, action: Action): GameState {
    val next = reduceRaw(state, action)
    if (next === state) return state
    return settleOverrides(state, next)
}

private fun reduceRaw(s: GameState, a: Action): GameState = when (a) {
    is Action.ChangeLife -> s.updatePlayer(a.player) { it.copy(life = clampLife(it.life.toLong() + a.delta)) }
    is Action.SetLife -> s.updatePlayer(a.player) { it.copy(life = clampLife(a.value.toLong())) }
    is Action.ChangeCounter -> s.updatePlayer(a.player) {
        it.copy(counters = it.counters.with(a.counter, clampCounter(it.counters[a.counter].toLong() + a.delta)))
    }
    is Action.SetCounter -> s.updatePlayer(a.player) {
        it.copy(counters = it.counters.with(a.counter, clampCounter(a.value.toLong())))
    }
    is Action.ChangeCommanderTax -> s.updatePlayer(a.player) {
        if (!it.hasSlot(a.slot)) it
        else it.copy(commanderTax = it.commanderTax.with(a.slot, clampCounter(it.commanderTax[a.slot].toLong() + a.delta)))
    }
    is Action.ChangeCommanderDamage -> commanderDamage(s, a)
    is Action.SetToggle -> setToggle(s, a)
    is Action.SetDayNight -> if (s.dayNight == a.value) s else s.copy(dayNight = a.value)
    Action.NextTurn -> nextTurn(s)
    is Action.SetActivePlayer -> when {
        s.player(a.player) == null -> s
        !s.turn.started -> s.copy(turn = TurnState(a.player, a.player, number = 1, round = 1))
        s.turn.activePlayer == a.player -> s
        else -> s.copy(turn = s.turn.copy(activePlayer = a.player))
    }
    is Action.SetStartingPlayer -> {
        val t = TurnState(a.player, a.player, number = 1, round = 1)
        if (s.player(a.player) == null || s.turn == t) s else s.copy(turn = t)
    }
    is Action.Rename -> s.updatePlayer(a.player) { it.copy(name = a.name) }
    is Action.SetPattern -> s.updatePlayer(a.player) { it.copy(pattern = a.pattern) }
    is Action.SetPartner -> setPartner(s, a)
    is Action.Revive -> s.updatePlayer(a.player) {
        val needed = s.settings.autoKo && autoKoReason(it) != null
        it.copy(koOverride = if (needed) KoOverride.ALIVE else KoOverride.NONE)
    }
    is Action.MarkDead -> s.updatePlayer(a.player) { it.copy(koOverride = KoOverride.DEAD) }
    is Action.UpdateSettings -> if (s.settings == a.settings) s else s.copy(settings = a.settings)
    Action.NewGame -> s.copy(
        players = s.players.map { p ->
            GameState.newPlayer(p.id, p.seat, s.settings.startingLife)
                .copy(name = p.name, pattern = p.pattern, partner = p.partner)
        },
        dayNight = DayNight.UNSET,
        turn = TurnState(),
    ).orSame(s)
    Action.Reset -> GameState.create(s.players.size, s.settings).orSame(s)
    is Action.SetPlayerCount -> setPlayerCount(s, a.count)
}

private fun GameState.orSame(old: GameState): GameState = if (this == old) old else this

private fun Player.hasSlot(slot: CommanderSlot) =
    slot == Commander.PRIMARY || (slot == Commander.PARTNER && partner)

private fun clampLife(v: Long): Int = v.coerceIn(Limits.LIFE_MIN.toLong(), Limits.LIFE_MAX.toLong()).toInt()
private fun clampCounter(v: Long): Int = v.coerceIn(0L, Limits.COUNTER_MAX.toLong()).toInt()

/** Replaces one player; returns [this] if the id is unknown or the player is unchanged. */
private inline fun GameState.updatePlayer(id: PlayerId, f: (Player) -> Player): GameState {
    val i = indexOf(id)
    if (i < 0) return this
    val old = players[i]
    val new = f(old)
    if (new == old) return this
    return copy(players = players.toMutableList().also { it[i] = new })
}

private fun commanderDamage(s: GameState, a: Action.ChangeCommanderDamage): GameState {
    if (a.target == a.source) return s
    val source = s.player(a.source) ?: return s
    if (!source.hasSlot(a.slot)) return s
    return s.updatePlayer(a.target) { target ->
        val from = target.commanderDamage[a.source] ?: CommanderDamage()
        val slot = from[a.slot]
        val newDamage = (slot.damage.toLong() + a.delta).coerceIn(0L, Limits.COUNTER_MAX.toLong()).toInt()
        val applied = newDamage - slot.damage
        if (applied == 0) return@updatePlayer target

        val lifeChange: Int
        val linked: Int
        if (applied > 0) {
            val link = a.linkLife ?: s.settings.commanderDamageAffectsLife
            lifeChange = if (link) -applied else 0
            linked = slot.lifeLinked - lifeChange
        } else {
            // Refund the most recent (linked) damage first, never more than was taken.
            val refund = if (a.linkLife == false) 0 else minOf(-applied, slot.lifeLinked)
            lifeChange = refund
            linked = minOf(slot.lifeLinked - refund, newDamage)
        }
        val newFrom = from.with(a.slot, DamageSlot(newDamage, linked))
        target.copy(
            life = clampLife(target.life.toLong() + lifeChange),
            commanderDamage = if (newFrom.isZero) target.commanderDamage - a.source
            else target.commanderDamage + (a.source to newFrom),
        )
    }
}

private fun setToggle(s: GameState, a: Action.SetToggle): GameState {
    val p = s.player(a.player) ?: return s
    if (p.toggles[a.toggle] == a.on) return s
    val clearOthers = a.on && a.toggle.exclusive
    return s.copy(players = s.players.map {
        when {
            it.id == a.player -> it.copy(toggles = it.toggles.with(a.toggle, a.on))
            clearOthers && it.toggles[a.toggle] -> it.copy(toggles = it.toggles.with(a.toggle, false))
            else -> it
        }
    })
}

private fun setPartner(s: GameState, a: Action.SetPartner): GameState {
    val p = s.player(a.player) ?: return s
    if (p.partner == a.partner) return s
    if (a.partner) return s.updatePlayer(a.player) { it.copy(partner = true) }
    return s.copy(players = s.players.map {
        if (it.id == a.player) {
            it.copy(partner = false, commanderTax = it.commanderTax.with(Commander.PARTNER, 0))
        } else {
            val from = it.commanderDamage[a.player]
            if (from == null || from.partner.isZero) it
            else {
                val cleared = from.copy(partner = DamageSlot())
                it.copy(
                    commanderDamage = if (cleared.isZero) it.commanderDamage - a.player
                    else it.commanderDamage + (a.player to cleared),
                )
            }
        }
    })
}

private fun nextTurn(s: GameState): GameState {
    val players = s.players
    val t = s.turn
    val current = t.activePlayer?.let(s::indexOf) ?: -1
    if (!t.started || current < 0) {
        val start = t.startingPlayer?.takeIf { s.player(it) != null }
            ?: players.firstOrNull { !s.isKo(it) }?.id ?: players[0].id
        return s.copy(turn = TurnState(start, start, number = 1, round = 1))
    }
    val n = players.size
    var wrapped = false
    var chosen = -1
    for (step in 1..n) {
        val i = (current + step) % n
        val candidate = players[i]
        if (candidate.id == t.startingPlayer || (t.startingPlayer == null && i == 0)) wrapped = true
        if (!s.isKo(candidate)) {
            chosen = i
            break
        }
    }
    if (chosen < 0) {
        // Everyone is KO'd: just advance one seat.
        chosen = (current + 1) % n
        wrapped = players[chosen].id == t.startingPlayer || (t.startingPlayer == null && chosen == 0)
    }
    return s.copy(
        turn = t.copy(
            activePlayer = players[chosen].id,
            number = t.number + 1,
            round = t.round + if (wrapped) 1 else 0,
        ),
    )
}

private fun setPlayerCount(s: GameState, count: Int): GameState {
    if (count !in GameState.MIN_PLAYERS..GameState.MAX_PLAYERS || count == s.players.size) return s
    if (count > s.players.size) {
        val used = s.players.map { it.id }.toMutableSet()
        val added = (s.players.size until count).map { seat ->
            val id = generateSequence(0) { it + 1 }.first { it !in used }
            used += id
            GameState.newPlayer(id, seat, s.settings.startingLife)
        }
        return s.copy(players = s.players + added)
    }
    val kept = s.players.take(count)
    val removed = s.players.drop(count).map { it.id }.toSet()
    val players = kept.map { p ->
        if (p.commanderDamage.keys.none { it in removed }) p
        else p.copy(commanderDamage = p.commanderDamage - removed)
    }
    val firstId = players[0].id
    val t = s.turn
    val turn = t.copy(
        activePlayer = t.activePlayer?.let { if (it in removed) firstId else it },
        startingPlayer = t.startingPlayer?.let { if (it in removed) firstId else it },
    )
    return s.copy(players = players, turn = turn)
}

private fun settleOverrides(old: GameState, new: GameState): GameState {
    var changed = false
    val players = new.players.map { p ->
        if (p.koOverride != KoOverride.ALIVE) return@map p
        val before = old.player(p.id)
        if (before == null || before === p) return@map p
        val wasDead = autoKoReason(before) != null
        val isDead = autoKoReason(p) != null
        if (wasDead == isDead) p else {
            changed = true
            p.copy(koOverride = KoOverride.NONE)
        }
    }
    return if (changed) new.copy(players = players) else new
}
