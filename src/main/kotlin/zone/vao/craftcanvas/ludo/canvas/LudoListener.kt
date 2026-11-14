package zone.vao.craftcanvas.ludo.canvas

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import zone.vao.craftcanvas.event.ScreenCloseEvent
import zone.vao.craftcanvas.event.ScreenOpenEvent
import zone.vao.craftcanvas.ludo.game.Games
import zone.vao.craftcanvas.ludo.ui.LudoViews

class LudoListener(private val games: Games, private val views: LudoViews) : Listener {
    @EventHandler
    fun opened(e: ScreenOpenEvent) {
        views.opened(e.player, e.session)
    }

    @EventHandler
    fun closed(e: ScreenCloseEvent) {
        views.closed(e.player, e.session.id)
    }

    @EventHandler
    fun quit(e: PlayerQuitEvent) {
        views.forget(e.player)
        games.disconnect(e.player.uniqueId)
    }

    @EventHandler
    fun join(e: PlayerJoinEvent) {
        views.remember(e.player)
        games.reconnect(e.player.uniqueId)
    }

}
