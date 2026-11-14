package zone.vao.craftcanvas.ludo

import java.util.concurrent.atomic.AtomicBoolean
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import zone.vao.craftcanvas.CanvasApi
import zone.vao.craftcanvas.CanvasRegistration
import zone.vao.craftcanvas.CanvasTypedAction
import zone.vao.craftcanvas.ludo.assets.LudoAssets
import zone.vao.craftcanvas.ludo.canvas.LudoActions
import zone.vao.craftcanvas.ludo.canvas.LudoListener
import zone.vao.craftcanvas.ludo.command.LudoCommand
import zone.vao.craftcanvas.ludo.config.LudoSettings
import zone.vao.craftcanvas.ludo.config.Messages
import zone.vao.craftcanvas.ludo.game.Games
import zone.vao.craftcanvas.ludo.scheduler.ServerClock
import zone.vao.craftcanvas.ludo.ui.LudoScreens
import zone.vao.craftcanvas.ludo.ui.LudoViews

class LudoPlugin : JavaPlugin() {
    private val ready = AtomicBoolean(false)
    private val registrations = mutableListOf<CanvasRegistration>()
    @Volatile private var command: LudoCommand? = null
    @Volatile private var games: Games? = null
    @Volatile private var views: LudoViews? = null

    override fun onEnable() {
        saveDefaultConfig()
        val settings = LudoSettings.load(config)
        val messages = Messages.load(this)
        val art = LudoAssets.install(this)
        CanvasApi.whenReady(this).thenAccept { canvas ->
            if (!isEnabled) return@thenAccept
            val animate = settings.animationEnabled && "autoplay" in canvas.rendererCapabilities("book-glyph")
            if (settings.animationEnabled && !animate) {
                logger.warning("Dice animation requires shaders.animations in CraftCanvas. Using a static die.")
            }
            val games = Games(
                ServerClock(this),
                botDelay = settings.botDelayTicks,
                graceTicks = settings.disconnectGraceTicks,
                maxRooms = settings.maxRooms,
                changed = { game -> views?.changed(game) },
                botName = { colour -> messages.text("bot-name", "n" to colour.ordinal + 1) },
            )
            this.games = games
            val views = LudoViews(this, canvas, games, messages, animate) {
                logger.warning("Could not update a Ludo screen: ${it.message}")
            }
            this.views = views
            val actions = LudoActions(canvas, games, views)
            registrations += canvas.registerAction(this, LudoActions.ID, LudoActions.schema,
                CanvasTypedAction { player, session, arguments ->
                    actions.execute(player, session, arguments[LudoActions.operation])
                })
            LudoScreens(art, messages).definitions(animate).forEach { definition ->
                registrations += canvas.registerScreen(this, definition)
            }
            server.pluginManager.registerEvents(LudoListener(games, views), this)
            command = LudoCommand(games, views, messages, ready::get)
            canvas.reload().whenComplete { result, error ->
                if (!isEnabled) return@whenComplete
                Bukkit.getGlobalRegionScheduler().execute(this) {
                    if (!isEnabled) return@execute
                    if (error != null || !result.successful) {
                        fail(error?.message ?: result.toString())
                    } else {
                        ready.set(true)
                        logger.info("Ludo is ready. Dice animation: ${if (animate) "enabled" else "disabled"}.")
                    }
                }
            }
        }.exceptionally { error ->
            if (!isEnabled) return@exceptionally null
            Bukkit.getGlobalRegionScheduler().execute(this) {
                if (isEnabled) fail(error.cause?.message ?: error.message ?: "Unknown startup failure")
            }
            null
        }
    }

    private fun fail(reason: String) {
        logger.severe("Ludo could not start: $reason")
        server.pluginManager.disablePlugin(this)
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        this.command?.let { return it.onCommand(sender, command, label, args) }
        sender.sendMessage(Component.text("Ludo is still starting. Try again shortly."))
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> =
        this.command?.onTabComplete(sender, command, alias, args).orEmpty()

    override fun onDisable() {
        ready.set(false)
        games?.close()
        views?.close()
        registrations.asReversed().forEach(CanvasRegistration::unregister)
        registrations.clear()
        Bukkit.getGlobalRegionScheduler().cancelTasks(this)
        command = null
        games = null
        views = null
    }
}
