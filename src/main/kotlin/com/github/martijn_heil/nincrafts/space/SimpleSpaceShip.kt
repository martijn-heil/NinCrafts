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

package com.github.martijn_heil.nincrafts.space

import com.github.martijn_heil.nincrafts.NinCrafts
import com.github.martijn_heil.nincrafts.Rotation
import com.github.martijn_heil.nincrafts.SimpleCraft
import com.github.martijn_heil.nincrafts.exception.CouldNotMoveCraftException
import com.github.martijn_heil.nincrafts.util.getRotatedLocation
import com.github.martijn_heil.nincrafts.vessel.SimpleRudder
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.Sign
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPhysicsEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.material.Door
import org.bukkit.plugin.Plugin
import java.util.logging.Logger

class SimpleSpaceShip(val plugin: Plugin, blocks: Collection<Block>,
                      rotationPoint: Location, val rudder: SimpleRudder, var movingSign: Sign)
    : SimpleCraft(plugin, blocks, rotationPoint) {

    init {
        super.rotationPoint = rotationPoint
    }

    var isMoving
        get() = movingSign.lines[1].toBoolean()
        set(value) { movingSign.setLine(1, value.toString()); movingSign.update(true, false) }

    override fun rotate(rotation: Rotation) {
        super.rotate(rotation)
        rudder.updateLocationRotated(rotationPoint, rotation)
        movingSign = world.getBlockAt(getRotatedLocation(rotationPoint, rotation, movingSign.location)).state as Sign
    }

    override fun move(relativeX: Int, relativeY: Int, relativeZ: Int) {
        super.move(relativeX, relativeY, relativeZ)
        rudder.updateLocation(relativeX, relativeY, relativeZ)
        movingSign = world.getBlockAt(movingSign.x + relativeX, movingSign.y + relativeY, movingSign.z + relativeZ).state as Sign
    }

    var heading: Int = 0
        set(value) {
            if (value == 360) throw IllegalArgumentException("360 degrees should be 0 degrees.")
            if (value < 0 || value > 360) throw IllegalArgumentException("Invalid number of degrees.")


            if (currentlyFacing == -1) { // Need to initialize it
                when {
                    value > 315 || value < 45 -> currentlyFacing = 0
                    value > 45 && value < 135 -> currentlyFacing = 90
                    value > 135 && value < 225 -> currentlyFacing = 180
                    value > 225 && value < 315 -> currentlyFacing = 270
                }
            }

            var shouldFaceNext = 0
            when {
                value > 315 || value < 45 -> shouldFaceNext = 0
                value > 45 && value < 135 -> shouldFaceNext = 90
                value > 135 && value < 225 -> shouldFaceNext = 180
                value > 225 && value < 315 -> shouldFaceNext = 270
            }

            if (shouldFaceNext != currentlyFacing) {
                var rotation = Rotation.CLOCKWISE
                var amount = 0

                when (currentlyFacing) {
                    0 -> {
                        when (shouldFaceNext) {
                            90 -> {
                                rotation = Rotation.CLOCKWISE; amount = 1
                            }
                            180 -> {
                                rotation = Rotation.CLOCKWISE; amount = 2
                            }
                            270 -> {
                                rotation = Rotation.ANTICLOCKWISE; amount = 1
                            }
                        }
                    }
                    90 -> {
                        when (shouldFaceNext) {
                            0 -> {
                                rotation = Rotation.ANTICLOCKWISE; amount = 1
                            }
                            180 -> {
                                rotation = Rotation.CLOCKWISE; amount = 1
                            }
                            270 -> {
                                rotation = Rotation.CLOCKWISE; amount = 2
                            }
                        }
                    }
                    180 -> {
                        when (shouldFaceNext) {
                            0 -> {
                                rotation = Rotation.CLOCKWISE; amount = 2
                            }
                            90 -> {
                                rotation = Rotation.ANTICLOCKWISE; amount = 1
                            }
                            270 -> {
                                rotation = Rotation.CLOCKWISE; amount = 1
                            }
                        }
                    }
                    270 -> {
                        when (shouldFaceNext) {
                            0 -> {
                                rotation = Rotation.CLOCKWISE; amount = 1
                            }
                            90 -> {
                                rotation = Rotation.CLOCKWISE; amount = 2
                            }
                            180 -> {
                                rotation = Rotation.ANTICLOCKWISE; amount = 1
                            }
                        }
                    }
                }

                for (i in 1..amount) {
                    rotate(rotation)
                }

                currentlyFacing = shouldFaceNext
            }
            field = value
        }
    var currentlyFacing: Int = -1

    private val listener2 = object : Listener {
        @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
        fun onPlayerInteract(e: PlayerInteractEvent) {
            if(e.clickedBlock == null) return

            val state = e.clickedBlock!!.state
            if (state is Sign && state.lines[0] == "[Craft]" && containsBlock(e.clickedBlock!!)) {
                e.isCancelled = true
            }
        }
    }

    protected open val listener = object : Listener {
        @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
        fun onPlayerInteract(e: PlayerInteractEvent) {
            if (e.clickedBlock != null) {
                if (e.clickedBlock == movingSign.block) isMoving = !isMoving
            }
        }

        @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
        fun onEntityExplode(e: EntityExplodeEvent) {
            e.blockList().forEach { if (boundingBox.contains(it)) removeBlock(it) }
        }

        @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
        fun onBlockBreak(e: BlockBreakEvent) {
            if (boundingBox.contains(e.block)) removeBlock(e.block)
        }

        @EventHandler(ignoreCancelled = true)
        fun onBlockPhysics(e: BlockPhysicsEvent) {
            if (boundingBox.contains(e.block)) e.isCancelled = true // TODO
        }

        @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
        fun onBlockPlace(e: BlockPlaceEvent) {
            var containsBlock = false
            outer@ for (modX in -1..1) {
                for (modY in -1..1) {
                    for (modZ in -1..1) {
                        if (containsBlock(world.getBlockAt(e.block.x + modX, e.block.y + modY, e.block.z + modZ))) {
                            containsBlock = true
                            break@outer
                        }
                    }
                }
            }
            if (containsBlock) {
                val blockState = e.block.state
                val blockData = blockState.data
                addBlock(e.block)
                if (blockData is Door) addBlock(world.getBlockAt(e.block.x, e.block.y + if (blockData.isTopHalf) -1 else 1, e.block.z))
                // TODO beds
            }
        }

        @EventHandler(ignoreCancelled = true)
        fun onCreatureSpawn(e: CreatureSpawnEvent) { // No annoying mobs spawning on the ship.
            if (boundingBox.contains(e.location)) e.isCancelled = true
        }
    }

    private fun onChangeCourseCallback(heading: Int): Boolean {
        try {
            this.heading = heading
        } catch(e: CouldNotMoveCraftException) {
            onBoardEntities.filter { it is Player }.forEach { it.sendMessage(ChatColor.RED.toString() + e.message) }
            return false
        }
        return true
    }

    fun init() {
        plugin.server.pluginManager.registerEvents(listener, plugin)
        plugin.server.pluginManager.registerEvents(listener2, plugin)
        val updateTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(NinCrafts.instance, {
            try {
                update()
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }, 0, 80L)
        rudder.onChangeCourseCallback = { onChangeCourseCallback(it) }
    }

    private fun update() {
        if(!isMoving) return

        val newLoc = location.clone()
        var angle = 360 - heading.toDouble() + 90
        if (angle > 360) angle -= 360
        val radians = Math.toRadians(angle)
        newLoc.x += Math.round(0.0 + (20 * Math.cos(radians))).toInt()
        newLoc.z += Math.round(0.0 - (20 * Math.sin(radians))).toInt()
        try {
            location = newLoc
        } catch(e: CouldNotMoveCraftException) {
            onBoardEntities.filter { it is Player }.forEach { it.sendMessage(ChatColor.RED.toString() + e.message) }
        }
    }

    companion object {
        fun detect(plugin: Plugin, logger: Logger, detectionLoc: Location): SimpleSpaceShip {
            try {
                val maxSize = 100000
                val disallowedBlocks = listOf(
                        Material.AIR,
                        Material.WATER,
                        Material.LEGACY_STATIONARY_WATER,
                        Material.LAVA,
                        Material.LEGACY_STATIONARY_LAVA
                )
                val blocks: Collection<Block>
                // Detect vessel
                try {
                    logger.info("Detecting spaceship at " + detectionLoc.x + "x " + detectionLoc.y + "y " + detectionLoc.z + "z")
                    blocks = com.github.martijn_heil.nincrafts.util.detectFloodFill(detectionLoc, disallowedBlocks, true, maxSize)
                } catch (e: Exception) {
                    logger.info("Failed to detectFloodFill spaceship: " + (e.message ?: "unknown error"))
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
                val rudderSign = signs.find { it.lines[0] == "[Rudder]" }
                        ?: throw IllegalStateException("No rudder found.")
                logger.info("Found rudder sign at " + rudderSign.x + " " + rudderSign.y + " " + rudderSign.z)
                val rudder = SimpleRudder(rudderSign)

                val movingSign = signs.find { it.lines[0] == "[Moving]" }
                        ?: throw IllegalStateException("No moving sign found.")
                movingSign.setLine(1, "false")
                movingSign.update(true, false)
                logger.info("Found moving sign at " + movingSign.x + " " + movingSign.y + " " + rudderSign.z)

                val ship =  SimpleSpaceShip(plugin, blocks, rotationPoint, rudder, movingSign)
                ship.init()
                return ship
            } catch (t: Throwable) {
                throw t
            }
        }
    }
}
