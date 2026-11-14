package zone.vao.craftcanvas.ludo.canvas

import org.bukkit.entity.Player
import zone.vao.craftcanvas.CanvasActionSchema
import zone.vao.craftcanvas.CanvasParameter
import zone.vao.craftcanvas.CanvasService
import zone.vao.craftcanvas.SessionView
import zone.vao.craftcanvas.ludo.game.GameError
import zone.vao.craftcanvas.ludo.game.Games
import zone.vao.craftcanvas.ludo.ui.LudoViews

class LudoActions(
    private val canvas: CanvasService,
    private val games: Games,
    private val views: LudoViews,
) {
    fun execute(player: Player, session: SessionView, op: String) {
        if (!player.hasPermission("ludo.play")) {
            views.tell(player, "permission")
            return
        }
        if (canvas.session(player)?.id != session.id) return
        guarded(player) { callback(player, session, op) }
    }

    private fun guarded(p: Player, task: () -> Unit) {
        try {
            task()
        } catch (e: GameError) {
            views.tell(p, e.key)
        } catch (e: NumberFormatException) {
            views.tell(p, "bots-range")
        }
    }

    private fun callback(p: Player, s: SessionView, op: String) {
        val version = s.variables["session.version"]?.toString()?.toLongOrNull() ?: -1
        when {
            op == "menu" -> views.show(p, "ludo:menu")
            op == "setup" -> views.show(p, "ludo:setup")
            op == "joinScreen" -> views.show(p, "ludo:join")
            op == "refreshRooms" -> views.refreshRooms(p)
            op == "resume" -> views.resume(p)
            op == "hide" -> {
                views.hide(p)
                views.tell(p, "hidden")
            }
            op == "create" -> {
                val g = games.create(p.uniqueId, p.name)
                views.tell(p, "joined", "code" to g.code)
                views.resume(p)
            }
            op == "solo" -> {
                games.solo(p.uniqueId, p.name, (s.variables["session.bots"] as Number).toInt())
                views.resume(p)
            }
            op == "joinCode" || op.matches(Regex("room[0-3]")) -> {
                val code =
                    if (op == "joinCode") s.variables["session.code"].toString()
                    else s.variables["session.$op"].toString().substringBefore(' ')
                games.join(p.uniqueId, p.name, code.trim())
                views.resume(p)
            }
            op == "ready" -> games.ready(p.uniqueId, version)
            op == "colour" -> games.colour(p.uniqueId, version)
            op == "addBot" -> games.bots(p.uniqueId, version, true)
            op == "removeBot" -> games.bots(p.uniqueId, version, false)
            op == "start" -> games.start(p.uniqueId, version)
            op == "roll" -> games.roll(p.uniqueId, version)
            op.matches(Regex("pawn[0-3]")) ->
                games.move(p.uniqueId, version, op.removePrefix("pawn").toInt())
            op == "rematch" -> games.rematch(p.uniqueId, version)
            op == "leave" -> {
                games.leave(p.uniqueId)
                views.show(p, "ludo:menu")
                views.tell(p, "left")
            }
        }
    }

    companion object {
        const val ID = "ludo:action"
        val operation = CanvasParameter.choice("op", listOf(
            "menu", "setup", "joinScreen", "refreshRooms", "resume", "hide", "create", "solo",
            "joinCode", "ready", "colour", "addBot", "removeBot", "start", "roll", "rematch", "leave",
        ) + (0..3).flatMap { listOf("room$it", "pawn$it") })
        val schema = CanvasActionSchema.of(operation)
    }
}
