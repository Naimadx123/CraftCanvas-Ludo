package zone.vao.craftcanvas.ludo.game

import java.util.UUID

class Games(
    private val clock: GameClock,
    private val dice: Dice = Dice {
        java.util.concurrent.ThreadLocalRandom.current().nextInt(1, 7)
    },
    private val botDelay: Long = 14,
    private val graceTicks: Long = 1800,
    private val maxRooms: Int = 100,
    private val changed: (Game) -> Unit = {},
    private val removed: (String) -> Unit = {},
    private val botName: (Colour) -> String = { "Bot ${it.ordinal+1}" },
) : AutoCloseable {
    private val rooms = linkedMapOf<String, Game>()
    private val members = mutableMapOf<UUID, String>()
    private val jobs = mutableMapOf<String, MutableSet<Cancel>>()
    private val departures = mutableMapOf<UUID, UUID>()
    private var closed = false

    @Synchronized fun room(code: String): Game? = rooms[code.uppercase()]

    @Synchronized fun mine(id: UUID): Game? = members[id]?.let(rooms::get)

    @Synchronized fun list(): List<Game> = rooms.values.filter { it.phase == Phase.LOBBY }.toList()

    private fun fail(key: String): Nothing = throw GameError(key)

    private fun game(id: UUID): Game = mine(id) ?: fail("no-room")

    private fun check(game: Game, version: Long) {
        if (game.version != version) fail("stale")
    }

    private fun host(game: Game, id: UUID) {
        if (game.host != id) fail("host-only")
    }

    private fun lobby(game: Game) {
        if (game.phase != Phase.LOBBY) fail("lobby-only")
    }

    private fun publish(game: Game) {
        val frozen =
            game.copy(
                seats =
                    java.util.Collections.unmodifiableList(
                        game.seats.map {
                            it.copy(
                                pawns = java.util.Collections.unmodifiableList(it.pawns.toList())
                            )
                        }
                    )
            )
        rooms[game.code] = frozen
        changed(frozen)
        scheduleBot(frozen)
    }

    private fun later(code: String, ticks: Long, task: () -> Unit) {
        lateinit var handle: Cancel
        handle =
            clock.after(ticks) {
                synchronized(this) {
                    jobs[code]?.remove(handle)
                    if (!closed && code in rooms) task()
                }
            }
        jobs.getOrPut(code) { mutableSetOf() }.add(handle)
    }

    @Synchronized
    fun create(id: UUID, name: String, bots: Int = 0): Game {
        if (closed) fail("unavailable")
        if (id in members) fail("already-room")
        if (bots !in 0..3) fail("bots-range")
        if (rooms.size >= maxRooms) fail("room-limit")
        var code: String
        do {
            code = UUID.randomUUID().toString().take(6).uppercase()
        } while (code in rooms)
        val seats =
            listOf(Seat(id, name.take(14), Colour.RED, false)) +
                (1..bots).map {
                    Seat(UUID.randomUUID(), botName(Colour.entries[it]), Colour.entries[it], true)
                }
        members[id] = code
        val game = Game(code, id, seats)
        publish(game)
        return game
    }

    @Synchronized
    fun join(id: UUID, name: String, code: String): Game {
        if (id in members) fail("already-room")
        val game = room(code) ?: fail("invalid-code")
        lobby(game)
        if (game.seats.size == 4) fail("full")
        val colour = Colour.entries.first { c -> game.seats.none { it.colour == c } }
        val joined =
            game.copy(
                seats = game.seats + Seat(id, name.take(14), colour, false),
                version = game.version + 1,
            )
        members[id] = game.code
        publish(joined)
        return joined
    }

    @Synchronized
    fun bots(id: UUID, version: Long, add: Boolean) {
        val g = game(id)
        check(g, version)
        host(g, id)
        lobby(g)
        val seats =
            if (add) {
                if (g.seats.size == 4) fail("full")
                val c = Colour.entries.first { c -> g.seats.none { it.colour == c } }
                g.seats + Seat(UUID.randomUUID(), botName(c), c, true)
            } else {
                val bot = g.seats.lastOrNull { it.bot } ?: fail("no-bots")
                g.seats - bot
            }
        publish(g.copy(seats = seats, version = g.version + 1))
    }

    @Synchronized
    fun ready(id: UUID, version: Long) {
        val g = game(id)
        check(g, version)
        lobby(g)
        publish(
            g.copy(
                seats = g.seats.map { if (it.id == id) it.copy(ready = !it.ready) else it },
                version = g.version + 1,
            )
        )
    }

    @Synchronized
    fun colour(id: UUID, version: Long) {
        val g = game(id)
        check(g, version)
        lobby(g)
        val me = g.seats.first { it.id == id }
        val next =
            (1..4)
                .map { Colour.entries[(me.colour.ordinal + it) % 4] }
                .firstOrNull { c -> g.seats.none { it.colour == c } } ?: fail("no-colour")
        publish(
            g.copy(
                seats =
                    g.seats.map { if (it.id == id) it.copy(colour = next, ready = false) else it },
                version = g.version + 1,
            )
        )
    }

    @Synchronized
    fun start(id: UUID, version: Long) {
        val g = game(id)
        check(g, version)
        host(g, id)
        lobby(g)
        if (g.seats.size !in 2..4 || g.seats.any { !it.ready || !it.online }) fail("not-ready")
        publish(g.copy(phase = Phase.WAIT_ROLL, version = g.version + 1))
    }

    @Synchronized
    fun solo(id: UUID, name: String, bots: Int): Game {
        if (bots !in 1..3) fail("bots-range")
        val g = create(id, name, bots)
        ready(id, g.version)
        start(id, mine(id)!!.version)
        return mine(id)!!
    }

    private fun actor(g: Game, id: UUID, version: Long, phase: Phase) {
        check(g, version)
        if (g.active.id != id) fail("not-turn")
        if (g.phase != phase) fail("wrong-phase")
    }

    @Synchronized
    fun roll(id: UUID, version: Long): Roll {
        val g = gameOrBot(id)
        actor(g, id, version, Phase.WAIT_ROLL)
        val value = dice.roll()
        require(value in 1..6)
        val roll = Roll(UUID.randomUUID(), g.version + 1, value)
        publish(g.copy(phase = Phase.ROLLING, roll = roll, version = roll.version))
        later(g.code, DiceTimeline.ticks) { completeRoll(g.code, roll.id, roll.version) }
        return roll
    }

    private fun gameOrBot(id: UUID): Game =
        mine(id)
            ?: rooms.values.firstOrNull { g -> g.seats.any { it.id == id && it.bot } }
            ?: fail("no-room")

    @Synchronized
    fun completeRoll(code: String, id: UUID, version: Long): Boolean {
        val g = room(code) ?: return false
        if (g.phase != Phase.ROLLING || g.roll?.id != id || g.roll.version != version) return false
        if (Rules.legal(g.active.pawns, g.roll.value).isEmpty()) next(g)
        else publish(g.copy(phase = Phase.WAIT_MOVE, version = g.version + 1))
        return true
    }

    private fun next(g: Game, seats: List<Seat> = g.seats) {
        publish(
            g.copy(
                seats = seats,
                phase = Phase.WAIT_ROLL,
                current = if (g.roll!!.value == 6) g.current else (g.current + 1) % seats.size,
                version = g.version + 1,
            )
        )
    }

    @Synchronized
    fun move(id: UUID, version: Long, pawn: Int) {
        val g = gameOrBot(id)
        actor(g, id, version, Phase.WAIT_MOVE)
        if (pawn !in g.legal()) fail("illegal")
        val seats = Rules.move(g.seats, g.current, pawn, g.roll!!.value)
        if (Rules.won(seats[g.current]))
            publish(
                g.copy(
                    seats = seats,
                    phase = Phase.FINISHED,
                    winner = id,
                    winnerName = seats[g.current].name,
                    version = g.version + 1,
                )
            )
        else next(g, seats)
    }

    private fun scheduleBot(g: Game) {
        if (!g.active.bot || g.phase !in setOf(Phase.WAIT_ROLL, Phase.WAIT_MOVE)) return
        later(g.code, botDelay) {
            val current = room(g.code) ?: return@later
            if (
                current.version != g.version ||
                    current.active.id != g.active.id ||
                    current.phase != g.phase
            )
                return@later
            if (g.phase == Phase.WAIT_ROLL) roll(g.active.id, g.version)
            else
                Bots.choose(g.seats, g.current, g.roll!!.value)?.let {
                    move(g.active.id, g.version, it)
                }
        }
    }

    @Synchronized
    fun rematch(id: UUID, version: Long) {
        val g = game(id)
        check(g, version)
        host(g, id)
        if (g.phase != Phase.FINISHED) fail("wrong-phase")
        publish(
            g.copy(
                seats = g.seats.map { it.copy(pawns = List(4) { -1 }, ready = it.bot) },
                phase = Phase.LOBBY,
                current = 0,
                roll = null,
                winner = null,
                winnerName = null,
                version = g.version + 1,
            )
        )
    }

    private fun removeRoom(code: String) {
        val g = rooms.remove(code) ?: return
        g.seats.forEach {
            members.remove(it.id)
            departures.remove(it.id)
        }
        jobs.remove(code)?.forEach { it.cancel() }
        removed(code)
    }

    @Synchronized
    fun leave(id: UUID) {
        depart(id, false)
    }

    private fun depart(id: UUID, keepLobbySeat: Boolean) {
        val g = game(id)
        members.remove(id)
        departures.remove(id)
        val humans = g.seats.filter { !it.bot && it.id != id }
        if (humans.isEmpty()) {
            removeRoom(g.code)
            return
        }
        val seats =
            if (g.phase == Phase.LOBBY && !keepLobbySeat) g.seats.filter { it.id != id }
            else
                g.seats.map {
                    if (it.id == id)
                        it.copy(
                            id = UUID.randomUUID(),
                            name = botName(it.colour),
                            bot = true,
                            online = true,
                            ready = true,
                        )
                    else it
                }
        publish(
            g.copy(
                host = if (g.host == id) humans.first().id else g.host,
                seats = seats,
                version = g.version + 1,
            )
        )
    }

    @Synchronized
    fun disconnect(id: UUID) {
        val g = mine(id) ?: return
        val token = UUID.randomUUID()
        departures[id] = token
        publish(
            g.copy(
                seats = g.seats.map { if (it.id == id) it.copy(online = false) else it },
                version = g.version + 1,
            )
        )
        later(g.code, graceTicks) {
            if (departures[id] == token && mine(id) != null) depart(id, true)
        }
    }

    @Synchronized
    fun reconnect(id: UUID) {
        val g = mine(id) ?: return
        departures.remove(id)
        publish(
            g.copy(
                seats = g.seats.map { if (it.id == id) it.copy(online = true) else it },
                version = g.version + 1,
            )
        )
    }

    @Synchronized
    override fun close() {
        closed = true
        rooms.keys.toList().forEach(::removeRoom)
        clock.close()
    }
}
