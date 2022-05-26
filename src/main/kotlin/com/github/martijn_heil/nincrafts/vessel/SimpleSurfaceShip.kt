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

package com.github.martijn_heil.nincrafts.vessel

import com.github.martijn_heil.nincrafts.NinCrafts
import com.github.martijn_heil.nincrafts.Rotation
import com.github.martijn_heil.nincrafts.SimpleCraft
import com.github.martijn_heil.nincrafts.configuredSeaLevel
import com.github.martijn_heil.nincrafts.exception.CouldNotMoveCraftException
import com.github.martijn_heil.nincrafts.util.*
import com.github.martijn_heil.nincrafts.util.nms.CraftMassBlockUpdate
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material.*
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.BlockState
import org.bukkit.block.data.Attachable
import org.bukkit.block.data.type.Ladder
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.plugin.Plugin
import org.bukkit.util.Vector
import java.util.stream.Collectors


open class SimpleSurfaceShip(private val plugin: Plugin, blocks: ArrayList<Block>, rotationPoint: Location) :
    SimpleCraft(plugin, blocks, rotationPoint), Ship {
    override var heading: Int = 0
    protected var waterLevel: Int = 0
    private var floodTaskId = -1
    protected open var floodPeriod = 40L
        set(value) {
            if(floodTaskId != -1 && isFlooding) {
                plugin.server.scheduler.cancelTask(floodTaskId)
                plugin.server.scheduler.scheduleSyncRepeatingTask(plugin, floodingTask, 0, floodPeriod)
            }
            field = value
        }
    var isFlooding = false
        private set(value) {
            if(value && value != field) {
                plugin.logger.info("$this has started flooding!")
                floodTaskId = plugin.server.scheduler.scheduleSyncRepeatingTask(plugin, floodingTask, 0, floodPeriod)
                if(floodTaskId == -1) throw RuntimeException("Could not schedule flooding task.")
            } else if(!value && floodTaskId != -1) {
                plugin.server.scheduler.cancelTask(floodTaskId)
            }
            field = value
        }
    var leaks = ArrayList<Triple<Int, Int, Int>>()

    protected open var sinkingPeriod = 20L
    var sinkingTaskId = -1
    var hasSunk = false
    open var isSinking = false
        set(value) {
            if(value && value != field) {
                plugin.logger.info("$this has started sinking!")
                isFlooding = false
                sinkingTaskId = plugin.server.scheduler.scheduleSyncRepeatingTask(plugin, {
                    try {
                        this.move(0, -1, 0)
                    } catch(e: CouldNotMoveCraftException) {
                        hasSunk = true
                        this.close()
                        plugin.server.scheduler.cancelTask(sinkingTaskId)
                    }
                }, 0, sinkingPeriod)
                if(sinkingTaskId == -1) throw RuntimeException("Could not schedule sinking task.")
            } else if(!value) {
                if(isSinking && sinkingTaskId != -1) plugin.server.scheduler.cancelTask(sinkingTaskId)
            }
            field = value
        }

    private val floodingTask = Runnable {
        val airBlocks = this.blocks
            .asSequence()
            .filter { it.value.type == AIR && getBlock(it.key).y < waterLevel }
            .map { Pair(it.key, it.value) }
            .toMutableList()
        if(airBlocks.isEmpty()) {
            isFlooding = false
            isSinking = true
        } else {
            airBlocks.sortBy { it.first.y }
            airBlocks.first().second.type = WATER
        }
    }

    override fun restoreBlockInWake(block: Location, massBlockUpdate: MassBlockUpdate) = when {
        block.y < waterLevel -> massBlockUpdate.setBlock(block.blockX, block.blockY, block.blockZ, WATER)
        else -> massBlockUpdate.setBlock(block.blockX, block.blockY, block.blockZ, AIR)
    }


    protected open fun init() {
        waterLevel = world.configuredSeaLevel
        detectAirBlocksBelowWaterLevel(rotationPoint.world!!, boundingBox, waterLevel)
            .forEach { blocks[getRelativeBlock(it)] = it.state }

        plugin.server.pluginManager.registerEvents(object : Listener {
            @EventHandler(priority = EventPriority.MONITOR)
            fun onBlockBreak(e: BlockBreakEvent) {
                if(e.block.y < waterLevel && containsBlock(e.block)) {
                    leaks.add(Triple(
                            rotationPoint.blockX - e.block.x,
                            rotationPoint.blockY - e.block.y,
                            rotationPoint.blockZ - e.block.z))
                    isFlooding = true
                }
            }
        }, plugin)
    }
}
