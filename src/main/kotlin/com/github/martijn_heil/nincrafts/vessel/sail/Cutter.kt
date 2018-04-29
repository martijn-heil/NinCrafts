/*
 *     wac-core
 *     Copyright (C) 2016 Martijn Heil
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.martijn_heil.nincrafts.vessel.sail

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.Sign
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.plugin.Plugin
import com.github.martijn_heil.nincrafts.RowingDirection
import com.github.martijn_heil.nincrafts.vessel.SimpleRudder
import java.util.*
import java.util.logging.Logger


class Cutter private constructor(plugin: Plugin, logger: Logger, blocks: Collection<Block>, rotationPoint: Location,
                                 sails: Collection<SimpleSail>, rudder: SimpleRudder, rowingSign: Sign,
                                 rowingDirectionSign: Sign) :
        SimpleSailingVessel(plugin, logger, blocks, rotationPoint, sails, rudder, rowingSign, rowingDirectionSign) {
    override var normalMaxSpeed: Int = 18000
    override var updateInterval: Int = 40
    override val minWindAngle: Int = 30

    private val listener2 = object : Listener {
        @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
        fun onPlayerInteract(e: PlayerInteractEvent) {
            if(e.clickedBlock == null) return

            val state = e.clickedBlock.state
            if (state is Sign && state.lines[0] == "[Craft]" && containsBlock(e.clickedBlock)) {
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
                val maxSize = 10000
                val allowedBlocks: Collection<Material> = Material.values().filter { it != Material.AIR && it != Material.WATER && it != Material.STATIONARY_WATER && it != Material.LAVA && it != Material.STATIONARY_LAVA }
                val blocks: Collection<Block>
                // Detect vessel
                try {
                    logger.info("Detecting count at " + detectionLoc.x + "x " + detectionLoc.y + "y " + detectionLoc.z + "z")
                    blocks = com.github.martijn_heil.nincrafts.util.detect(detectionLoc, allowedBlocks, maxSize)
                } catch(e: Exception) {
                    logger.info("Failed to detect sailing vessel: " + (e.message ?: "unknown error"))
                    throw IllegalStateException(e.message)
                }
                val signs = blocks.map { it.state }.filter { it is Sign }.map { it as Sign }
                val rotationPointSign = signs.find { it.lines[0] == "[RotationPoint]" }
                if (rotationPointSign == null) {
                    logger.warning("Could not detect rotation point")
                    throw IllegalStateException("Could not detect rotation point.")
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
                    logger.fine("Found sail sign at " + it.x + " " + it.y + " " + it.z)
                    sails.add(SimpleSail(plugin, it))
                }
                if (sails.isEmpty()) throw IllegalStateException("No sails found.")

                val cutter = Cutter(plugin, logger, blocks, rotationPoint, sails, rudder, rowingSign, rowingDirectionSign)
                cutter.init()
                return cutter
            } catch(t: Throwable) {
                sails.forEach { it.isHoisted = true; it.close() }
                throw t
            }
        }
    }
}
