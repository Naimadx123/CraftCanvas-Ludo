package zone.vao.craftcanvas.ludo.scheduler

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import zone.vao.craftcanvas.ludo.game.Cancel
import zone.vao.craftcanvas.ludo.game.GameClock

class ServerClock(private val plugin: JavaPlugin) : GameClock {
    override fun after(ticks: Long, task: () -> Unit): Cancel {
        val scheduled =
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, { task() }, ticks.coerceAtLeast(1))
        return Cancel {
            scheduled.cancel()
        }
    }

    override fun close() {
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin)
    }
}
