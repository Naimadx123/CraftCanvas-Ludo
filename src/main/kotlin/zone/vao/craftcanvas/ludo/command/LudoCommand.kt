package zone.vao.craftcanvas.ludo.command

import net.kyori.adventure.text.Component
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import zone.vao.craftcanvas.ludo.config.Messages
import zone.vao.craftcanvas.ludo.game.GameError
import zone.vao.craftcanvas.ludo.game.Games
import zone.vao.craftcanvas.ludo.ui.LudoViews

class LudoCommand(
    private val games: Games,
    private val views: LudoViews,
    private val messages: Messages,
    private val ready: () -> Boolean,
) : CommandExecutor, TabCompleter {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): Boolean {
        if (sender !is Player) {
            sender.sendMessage(messages.text("players-only"))
            return true
        }
        if (!sender.hasPermission("ludo.play")) {
            sender.sendMessage(messages.text("permission"))
            return true
        }
        if (!ready()) {
            sender.sendMessage(messages.text("unavailable"))
            return true
        }
        views.remember(sender)
        guarded(sender) {
            val g = games.mine(sender.uniqueId)
            val version = g?.version ?: -1
            when (args.firstOrNull()?.lowercase()) {
                null -> views.show(sender, "ludo:menu")
                "solo" -> {
                    games.solo(
                        sender.uniqueId,
                        sender.name,
                        args.getOrNull(1)?.toIntOrNull() ?: throw GameError("bots-range"),
                    )
                    views.resume(sender)
                }
                "create" -> {
                    val game = games.create(sender.uniqueId, sender.name)
                    views.tell(sender, "joined", "code" to game.code)
                    views.resume(sender)
                }
                "join" -> {
                    games.join(sender.uniqueId, sender.name, args.getOrNull(1) ?: "")
                    views.resume(sender)
                }
                "bot" ->
                    when (args.getOrNull(1)) {
                        "add" -> games.bots(sender.uniqueId, version, true)
                        "remove" -> games.bots(sender.uniqueId, version, false)
                        else -> views.tell(sender, "help")
                    }
                "ready" -> games.ready(sender.uniqueId, version)
                "colour" -> games.colour(sender.uniqueId, version)
                "start" -> games.start(sender.uniqueId, version)
                "resume" -> views.resume(sender)
                "hide" -> {
                    views.hide(sender)
                    views.tell(sender, "hidden")
                }
                "debug" -> {
                    if (!sender.hasPermission("ludo.admin")) throw GameError("admin-permission")
                    sender.sendMessage(Component.text(views.diagnostics(sender)))
                    sender.sendMessage(
                        Component.text(
                            "Resource-pack status: /cc debug ${sender.name}"
                        )
                    )
                }
                "leave" -> {
                    games.leave(sender.uniqueId)
                    views.hide(sender)
                    views.tell(sender, "left")
                }
                else -> views.tell(sender, "help")
            }
        }
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): List<String> {
        if (!ready() || !sender.hasPermission("ludo.play")) return emptyList()
        val options =
            when {
                args.size == 1 ->
                    listOf(
                        "solo",
                        "create",
                        "join",
                        "bot",
                        "ready",
                        "colour",
                        "start",
                        "resume",
                        "hide",
                        "leave",
                    ) + if (sender.hasPermission("ludo.admin")) listOf("debug") else emptyList()
                args.size == 2 && args[0] == "solo" -> listOf("1", "2", "3")
                args.size == 2 && args[0] == "bot" -> listOf("add", "remove")
                args.size == 2 && args[0] == "join" ->
                    games.list().filter { it.seats.size < 4 }.map { it.code }
                else -> emptyList()
            }
        return options.filter { it.startsWith(args.lastOrNull() ?: "", true) }
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

}
