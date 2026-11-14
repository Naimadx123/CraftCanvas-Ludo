package zone.vao.craftcanvas.ludo.ui

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import zone.vao.craftcanvas.ludo.config.Messages
import zone.vao.craftcanvas.ludo.game.*
import zone.vao.craftcanvas.*

class LudoViews(
    private val owner: Plugin,
    val canvas: CanvasService,
    private val games: Games,
    private val m: Messages,
    private val animate: Boolean,
    private val failure: (Throwable) -> Unit = {},
) {
    data class Binding(
        val session: UUID,
        val screen: String,
        var version: Long = -1,
        var roll: UUID? = null,
        var settling: Boolean = false,
    )

    private val players = ConcurrentHashMap<UUID, Player>()
    private val bindings = ConcurrentHashMap<UUID, Binding>()
    private val epochs = ConcurrentHashMap<UUID, AtomicLong>()
    private val intents = ConcurrentHashMap<UUID, String>()

    fun remember(p: Player) {
        players[p.uniqueId] = p
    }

    fun forget(p: Player) {
        hide(p, false)
        players.remove(p.uniqueId)
        epochs.remove(p.uniqueId)
    }

    private fun epoch(id: UUID) = epochs.computeIfAbsent(id) { AtomicLong() }

    fun tell(p: Player, key: String, vararg args: Pair<String, Any>) {
        canvas
            .withPlayer(p) {
                p.sendMessage(Component.text(m.text(key, *args)))
                Unit
            }
            .exceptionally { failure(it) }
    }

    private fun onOwner(p: Player, block: () -> Unit) {
        if (!owner.isEnabled) return
        p.scheduler.run(
            owner,
            {
                if (owner.isEnabled)
                    canvas
                        .withPlayer(p) {
                            block()
                            Unit
                        }
                        .exceptionally { failure(it) }
            },
            null,
        )
    }

    private fun gameScreen(screen: String) =
        screen in setOf("ludo:lobby", "ludo:board", "ludo:result") ||
            screen.startsWith("ludo:rolling")

    fun target(g: Game) =
        when (g.phase) {
            Phase.LOBBY -> "ludo:lobby"
            Phase.FINISHED -> "ludo:result"
            else -> "ludo:board"
        }

    fun resume(p: Player) {
        val g = games.mine(p.uniqueId) ?: throw GameError("no-room")
        show(p, target(g))
    }

    fun show(p: Player, screen: String) {
        remember(p)
        val e = epoch(p.uniqueId).incrementAndGet()
        intents[p.uniqueId] = screen
        val requestedRoll =
            if (screen.startsWith("ludo:rolling")) games.mine(p.uniqueId)?.roll?.id else null
        onOwner(p) {
            if (epoch(p.uniqueId).get() != e) return@onOwner
            val g = games.mine(p.uniqueId)
            val actual =
                if (gameScreen(screen) && g != null) {
                    if (
                        screen.startsWith("ludo:rolling") &&
                            g.phase == Phase.ROLLING &&
                            screen == "ludo:rolling${g.roll?.value}" &&
                            g.roll?.id == requestedRoll
                    )
                        screen
                    else target(g)
                } else screen
            intents[p.uniqueId] = actual
            val data =
                if (g != null && gameScreen(actual)) variables(g, p.uniqueId, actual)
                else emptyMap()
            val result = canvas.openDetailed(p, actual, data)
            when (result.status) {
                CanvasOpenStatus.OPENED -> bind(p, result.sessionId!!, actual)
                CanvasOpenStatus.QUEUED -> tell(p, "queued")
                else -> tell(p, "open-failed", "status" to result.status)
            }
        }
    }

    fun diagnostics(p: Player): String {
        val session = canvas.session(p)
        val rolling = session?.let { canvas.uiState(p, it.id)?.component("rollingDie") }
        val g = games.mine(p.uniqueId)
        return "animation=$animate; screen=${session?.screen ?: "none"}; profile=${session?.let { canvas.uiState(p,it.id)?.profile } ?: "none"}; " +
            "atlas=${rolling?.autoplay ?: false}; phase=${g?.phase ?: "none"}; roll=${g?.roll?.id ?: "none"}"
    }

    fun opened(p: Player, session: SessionView) {
        if (!session.screen.startsWith("ludo:")) return
        val e = epoch(p.uniqueId).get()
        onOwner(p) {
            if (epoch(p.uniqueId).get() != e || intents[p.uniqueId] != session.screen)
                return@onOwner
            val actual = canvas.session(p)
            if (actual?.id == session.id) bind(p, actual.id, actual.screen)
        }
    }

    private fun bind(p: Player, id: UUID, screen: String) {
        if (bindings[p.uniqueId]?.session == id) return
        val g = games.mine(p.uniqueId)
        val binding =
            Binding(
                id,
                screen,
                roll = g?.roll?.id,
                settling = screen.startsWith("ludo:rolling") && g?.phase == Phase.ROLLING,
            )
        bindings[p.uniqueId] = binding
        if (binding.settling)
            p.scheduler.runDelayed(
                owner,
                {
                    canvas
                        .withPlayer(p) {
                            if (bindings[p.uniqueId] === binding) {
                                binding.settling = false
                                val latest = games.mine(p.uniqueId)
                                if (latest != null) update(p, latest, true)
                            }
                            Unit
                        }
                        .exceptionally { failure(it) }
                },
                null,
                DiceTimeline.ticks + 2,
            )
        if (screen == "ludo:join") roomList(p, id) else if (g != null) update(p, g, false)
    }

    fun closed(p: Player, id: UUID) {
        bindings.computeIfPresent(p.uniqueId) { _, b -> if (b.session == id) null else b }
    }

    fun hide(p: Player, close: Boolean = true) {
        epoch(p.uniqueId).incrementAndGet()
        intents.remove(p.uniqueId)
        bindings.remove(p.uniqueId)
        if (close)
            canvas.closeAsync(p).exceptionally {
                failure(it)
                null
            }
    }

    fun changed(g: Game) {
        g.seats
            .filter { !it.bot }
            .forEach { seat ->
                val p = players[seat.id] ?: return@forEach
                val e = epoch(seat.id).get()
                if (bindings[seat.id] == null) return@forEach
                onOwner(p) { if (epoch(seat.id).get() == e) update(p, g, true) }
            }
    }

    private fun update(p: Player, g: Game, allowAnimation: Boolean) {
        val b = bindings[p.uniqueId] ?: return
        val session = canvas.session(p)
        if (session?.id != b.session) {
            bindings.remove(p.uniqueId, b)
            return
        }
        if (g.version <= b.version) return
        if (!gameScreen(b.screen)) return
        val latest = games.mine(p.uniqueId)
        if (latest?.code != g.code || latest.version > g.version) return
        if (b.settling && b.roll == g.roll?.id && g.phase != Phase.ROLLING) return
        val newRoll = g.phase == Phase.ROLLING && g.roll != null && b.roll != g.roll.id
        val next =
            when {
                g.phase == Phase.ROLLING && b.screen.startsWith("ludo:rolling") && !newRoll ->
                    b.screen
                newRoll && allowAnimation && animate -> "ludo:rolling${g.roll.value}"
                else -> target(g)
            }
        if (b.screen != next || newRoll && allowAnimation && animate) {
            show(p, next)
            return
        }
        val data = variables(g, p.uniqueId, b.screen)
        if (!canvas.setVariables(p, b.session, data)) return
        b.version = g.version
        if (newRoll) b.roll = g.roll.id
        if (b.screen == "ludo:lobby") {
            val host = g.host == p.uniqueId
            for (id in listOf("addBot", "removeBot", "start", "console_a")) canvas.patchComponent(
                p,
                b.session,
                id,
                CanvasComponentPatch(
                    appearance =
                        if (host) CanvasComponentAppearance.NORMAL
                        else CanvasComponentAppearance.DISABLED
                ),
            )
        }
        if (b.screen == "ludo:result")
            for (id in listOf("rematch", "console_a")) canvas.patchComponent(
                p,
                b.session,
                id,
                CanvasComponentPatch(
                    appearance =
                        if (g.host == p.uniqueId) CanvasComponentAppearance.NORMAL
                        else CanvasComponentAppearance.DISABLED
                ),
            )
    }

    private fun variables(g: Game, id: UUID, screen: String): Map<String, Any> {
        val common = mapOf<String, Any>("version" to g.version.toString(), "room" to g.code)
        return common +
            when (if (screen.startsWith("ludo:rolling")) "ludo:board" else screen) {
                "ludo:lobby" ->
                    (0..3).associate { i ->
                        "seat$i" to
                            (g.seats.getOrNull(i)?.let { s ->
                                m.text(
                                    "seat",
                                    "colour" to m.text("colour-${s.colour}"),
                                    "name" to
                                        s.name + (if (s.id == g.host) m.text("host-mark") else ""),
                                    "ready" to
                                        if (!s.online) m.text("offline")
                                        else m.text(if (s.ready) "ready-yes" else "ready-no"),
                                )
                            } ?: m.text("empty-seat"))
                    }
                "ludo:result" ->
                    mapOf(
                        "winner" to
                            (g.winnerName ?: g.seats.firstOrNull { it.id == g.winner }?.name ?: "")
                    )
                "ludo:board" ->
                    buildMap {
                        val occupants = BoardGeometry.occupants(g)
                        BoardGeometry.pawnCells.forEach { cell ->
                            val value = occupants.getValue(cell.id)
                            if (cell.size == 8)
                                put(
                                    cell.id,
                                    if (value == "empty") "empty"
                                    else "m${value.substringAfter('_')}",
                                )
                            put(
                                "colour_${cell.id}",
                                if (value == "empty") "empty" else "c${value[1]}",
                            )
                        }
                        put("phase", g.phase.name)
                        put(
                            "status",
                            m.text(
                                "turn",
                                "name" to g.active.name,
                                "colour" to m.text("colour-${g.active.colour}"),
                            ),
                        )
                        val rolling = animate && g.phase == Phase.ROLLING
                        put("result", if (rolling) "-" else g.roll?.value?.toString() ?: "-")
                        put(
                            "dieFace",
                            if (rolling || screen.startsWith("ludo:rolling")) "0"
                            else (g.roll?.value ?: 0).toString(),
                        )
                        put("canRoll", g.phase == Phase.WAIT_ROLL && g.active.id == id)
                        put(
                            "hint",
                            m.text(
                                if (g.phase == Phase.ROLLING) "hint-ROLLING"
                                else if (g.active.id != id) "hint-other" else "hint-${g.phase}"
                            ),
                        )
                        repeat(4) { put("legal$it", g.active.id == id && it in g.legal()) }
                    }
                else -> emptyMap()
            }
    }

    fun refreshRooms(p: Player) {
        val b = bindings[p.uniqueId] ?: return
        onOwner(p) { roomList(p, b.session) }
    }

    private fun roomList(p: Player, id: UUID) {
        val rooms = games.list().filter { it.seats.size < 4 }.take(4)
        canvas.setVariables(
            p,
            id,
            (0..3).associate { i ->
                "room$i" to (rooms.getOrNull(i)?.let { "${it.code}  ${it.seats.size}/4" } ?: "-")
            },
        )
    }

    fun close() {
        players.values.forEach { hide(it) }
        bindings.clear()
        players.clear()
        epochs.clear()
        intents.clear()
    }
}
