package com.github.martijn_heil.nincrafts

import com.github.martijn_heil.nincrafts.vessel.sail.*
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Sign
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import java.io.Closeable
import java.util.*

class NinCrafts : JavaPlugin() {
    private val random = Random()
    var windFrom: Int = 0 // Wind coming from x degrees
        private set

    override fun onEnable() {
        instance = this
        saveDefaultConfig()
        val cannons = server.pluginManager.getPlugin("Cannons")
        if(cannons != null) {
            cannonsAPI = (cannons as at.pavlov.cannons.Cannons).cannonsAPI
        }

        server.scheduler.scheduleSyncRepeatingTask(this, {
            windFrom = random.nextInt(360)
        }, 0, 72000) // Every hour

        server.scheduler.scheduleSyncRepeatingTask(this, {
            Bukkit.broadcastMessage(ChatColor.AQUA.toString() + "[Wind] " + ChatColor.GRAY + "The wind now blows from $windFrom°.")
        }, 0, 6000) // Every five minutes

        CraftManager.init(this)
        logger.info("Sea levels:")
        server.worlds.forEach { logger.info(it.name + ": " + it.configuredSeaLevel) }
    }

    override fun onDisable() {
        CraftManager.close()
    }

    private object CraftManager : AutoCloseable {
        private val crafts: MutableCollection<Craft> = ArrayList()
        private lateinit var plugin: Plugin
        fun init(plugin: Plugin) {
            this.plugin = plugin
            plugin.server.pluginManager.registerEvents(CraftManagerListener, plugin)
        }

        private object CraftManagerListener : Listener {
            @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
            fun onPlayerInteract(e: PlayerInteractEvent) {
                if (e.clickedBlock != null &&
                        (e.clickedBlock.type == Material.SIGN ||
                                e.clickedBlock.type == Material.SIGN_POST ||
                                e.clickedBlock.type == Material.WALL_SIGN) && (e.clickedBlock.state as Sign).lines[0] == "[Craft]") {
                    val type = (e.clickedBlock.state as Sign).lines[1]
                    if (type == "") {
                        e.player.sendMessage(ChatColor.RED.toString() + "Error: Craft type not specified."); return
                    }


                    when (type) {
                        "Trireme" -> {
                            try {
                                crafts.add(Trireme.detect(plugin, plugin.logger, e.clickedBlock.location))
                            } catch(ex: IllegalStateException) {
                                e.player.sendMessage(ChatColor.RED.toString() + "Error: " + ex.message)
                            } catch(ex: Exception) {
                                e.player.sendMessage(ChatColor.RED.toString() + "An internal server error occurred." + ex.message)
                                ex.printStackTrace()
                            }
                        }

                        "Unireme" -> {
                            try {
                                crafts.add(Unireme.detect(plugin, plugin.logger, e.clickedBlock.location))
                            } catch(ex: IllegalStateException) {
                                e.player.sendMessage(ChatColor.RED.toString() + "Error: " + ex.message)
                            } catch(ex: Exception) {
                                e.player.sendMessage(ChatColor.RED.toString() + "An internal server error occurred." + ex.message)
                                ex.printStackTrace()
                            }
                        }

                        "Count" -> {
                            try {
                                crafts.add(Count.detect(plugin, plugin.logger, e.clickedBlock.location))
                            } catch(ex: IllegalStateException) {
                                e.player.sendMessage(ChatColor.RED.toString() + "Error: " + ex.message)
                            } catch(ex: Exception) {
                                e.player.sendMessage(ChatColor.RED.toString() + "An internal server error occurred." + ex.message)
                                ex.printStackTrace()
                            }
                        }

                        "Speedy" -> {
                            try {
                                crafts.add(Speedy.detect(plugin, plugin.logger, e.clickedBlock.location))
                            } catch(ex: IllegalStateException) {
                                e.player.sendMessage(ChatColor.RED.toString() + "Error: " + ex.message)
                            } catch(ex: Exception) {
                                e.player.sendMessage(ChatColor.RED.toString() + "An internal server error occurred." + ex.message)
                                ex.printStackTrace()
                            }
                        }

                        "Cutter" -> {
                            try {
                                crafts.add(Cutter.detect(plugin, plugin.logger, e.clickedBlock.location))
                            } catch(ex: IllegalStateException) {
                                e.player.sendMessage(ChatColor.RED.toString() + "Error: " + ex.message)
                            } catch(ex: Exception) {
                                e.player.sendMessage(ChatColor.RED.toString() + "An internal server error occurred." + ex.message)
                                ex.printStackTrace()
                            }
                        }
                    }
                }
            }
        }

        override fun close() {
            crafts.forEach {
                if(it is Closeable) it.close() else if (it is AutoCloseable) it.close()
            }
        }
    }

    companion object {
        lateinit var instance: NinCrafts
        var cannonsAPI: Any? = null
    }
}

val World.configuredSeaLevel
    get() = NinCrafts.instance.config.getInt("seaLevels." + this.name, this.seaLevel)