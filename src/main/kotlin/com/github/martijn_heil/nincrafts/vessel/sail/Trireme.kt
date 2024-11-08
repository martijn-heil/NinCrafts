/*
 *
 * NinCrafts
 * Copyright (C) 2018 Martijn Heil
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.martijn_heil.nincrafts.vessel.sail

import com.github.martijn_heil.nincrafts.RowingDirection
import com.github.martijn_heil.nincrafts.util.detectFloodFill
import com.github.martijn_heil.nincrafts.vessel.SimpleRudder
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.Sign
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority.HIGHEST
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.plugin.Plugin
import java.util.logging.Logger


class Trireme private constructor(plugin: Plugin, logger: Logger, blocks: ArrayList<Block>, rotationPoint: Location,
                                  sails: Collection<SimpleSail>, rudder: SimpleRudder,
                                  rowingSign: Sign, rowingDirectionSign: Sign) :
    SimpleSailingVessel(plugin, logger, blocks, rotationPoint, sails, rudder, rowingSign, rowingDirectionSign) {
    private val listener2 = object : Listener {
        @EventHandler(ignoreCancelled = true, priority = HIGHEST)
        fun onPlayerInteract(e: PlayerInteractEvent) {
            if(e.clickedBlock == null) return

            val state = e.clickedBlock!!.state
            if (state is Sign && state.lines[0] == "[Craft]" && containsBlock(e.clickedBlock!!)) {
                e.isCancelled = true
            }
        }
    }

    override fun init() {
        super.init()
        plugin.server.pluginManager.registerEvents(listener2, plugin)
    }

    override fun close() {
        super.close()
        HandlerList.unregisterAll(listener2)
    }

    companion object {
        fun detect(plugin: Plugin, logger: Logger, detectionLoc: Location): SimpleSailingVessel {
            val sails: MutableCollection<SimpleSail> = ArrayList()
            try {
                val maxSize = 5000
                val disAllowedBlocks = hashSetOf(Material.AIR, Material.WATER, Material.LAVA, Material.KELP, Material.KELP_PLANT)
                val blocks: ArrayList<Block>
                // Detect vessel
                try {
                    logger.info("Detecting trireme at " + detectionLoc.x + "x " + detectionLoc.y + "y " + detectionLoc.z + "z")
                    blocks = detectFloodFill(detectionLoc, disAllowedBlocks, true, maxSize)
                } catch(e: Exception) {
                    logger.info("Failed to detectFloodFill sailing vessel: " + (e.message ?: "unknown error"))
                    throw IllegalStateException(e.message)
                }
                val signs = blocks.map { it.state }.filter { it is Sign }.map { it as Sign }
                val rotationPointSign = signs.find { it.lines[0] == "[RotationPoint]" }
                if (rotationPointSign == null) {
                    logger.warning("Could not detectFloodFill rotation point")
                    throw IllegalStateException("Could not detectFloodFill rotation point.")
                }
                val rotationPoint = rotationPointSign.location

                // Detect rudder
                val rudderSign = signs.find { it.lines[0] == "[Rudder]" } ?: throw IllegalStateException("No rudder found.")
                logger.info("Found rudder sign at " + rudderSign.x + " " + rudderSign.y + " " + rudderSign.z)
                val rudder = SimpleRudder(rudderSign)

                val rowingSign = signs.find { it.lines[0] == "[Rowing]" } ?: throw IllegalStateException("No rowing sign found.")
                rowingSign.setLine(1, "false")
                rowingSign.update(true, false)
                logger.info("Found rowing sign at " + rowingSign.x + " " + rowingSign.y + " " + rudderSign.z)

                val rowingDirectionSign = signs.find { it.lines[0] == "[RowingDirection]" } ?: throw IllegalStateException("No rowing direction sign found.")
                if(rowingDirectionSign.lines[1] == "") rowingDirectionSign.setLine(1, RowingDirection.FORWARD.toString().toLowerCase())
                logger.info("Found RowingDirection sign at " + rowingDirectionSign.x + " " + rowingDirectionSign.y + " " + rowingDirectionSign.z)

                // Detect sails
                signs.filter { it.lines[0] == "[Sail]" }.forEach {
                    logger.info("Found sail sign at " + it.x + " " + it.y + " " + it.z)
                    sails.add(SimpleSail(plugin, it))
                }
                if (sails.isEmpty()) throw IllegalStateException("No sails found.")

                val trireme = Trireme(plugin, logger, blocks, rotationPoint, sails, rudder, rowingSign, rowingDirectionSign)
                trireme.init()
                return trireme
            } catch(t: Throwable) {
                sails.forEach { it.isHoisted = true; it.close() }
                throw t
            }
        }
    }
}
