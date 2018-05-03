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
import com.github.martijn_heil.nincrafts.util.MassBlockUpdate
import com.github.martijn_heil.nincrafts.util.detectAirBlocksBelowWaterLevel
import com.github.martijn_heil.nincrafts.util.getRotatedLocation
import com.github.martijn_heil.nincrafts.util.nms.CraftMassBlockUpdate
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material.*
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.BlockState
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.material.Attachable
import org.bukkit.material.Directional
import org.bukkit.material.Ladder
import org.bukkit.plugin.Plugin
import org.bukkit.util.Vector
import java.util.stream.Collectors


open class SimpleSurfaceShip(private val plugin: Plugin, blocks: ArrayList<Block>, rotationPoint: Location) : SimpleCraft(plugin, blocks, rotationPoint), Ship {
    override var heading: Int = 0
    protected var waterLevel: Int = 0
    private var floodTaskId = -1
    protected open var floodPeriod = 20L
        set(value) {
            if(floodTaskId != -1 && isFlooding) {
                plugin.server.scheduler.cancelTask(floodTaskId)
                plugin.server.scheduler.scheduleSyncRepeatingTask(plugin, floodingTask, 0, floodPeriod)
            }
        }
    var isFlooding = false
        private set(value) {
            if(value && value != field) {
                plugin.logger.info(this.toString() + " has started flooding!")
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
                plugin.logger.info(this.toString() + " has started sinking!")
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
        val airBlocks = this.blocks.parallelStream().filter { it.type == AIR && it.y < waterLevel }.collect(Collectors.toList())
        if(airBlocks.isEmpty()) {
            isFlooding = false
            isSinking = true
        } else {
            airBlocks.sortBy { it.y }
            airBlocks.first().type = WATER
        }
    }

    protected open fun init() {
        waterLevel = world.configuredSeaLevel
        blocks.addAll(detectAirBlocksBelowWaterLevel(rotationPoint.world, boundingBox, waterLevel))

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

    override fun rotate(rotation: Rotation) {
        fun getNewLocation(loc: Location): Location = getRotatedLocation(rotationPoint, rotation, loc)

        fun setBlockStateFast(fromState: BlockState, x: Int, y: Int, z: Int, massBlockUpdate: MassBlockUpdate) {
            val materialData = fromState.data
            if (materialData is Directional) {
                if (materialData is Attachable && fromState.type != LADDER) {
                    when (rotation) {
                        Rotation.CLOCKWISE -> {
                            when (materialData.facing) {
                                BlockFace.NORTH -> materialData.setFacingDirection(BlockFace.EAST)
                                BlockFace.EAST -> materialData.setFacingDirection(BlockFace.SOUTH)
                                BlockFace.SOUTH -> materialData.setFacingDirection(BlockFace.WEST)
                                BlockFace.WEST -> materialData.setFacingDirection(BlockFace.NORTH)
                            }
                        }

                        Rotation.ANTICLOCKWISE -> {
                            when (materialData.facing) {
                                BlockFace.NORTH -> materialData.setFacingDirection(BlockFace.WEST)
                                BlockFace.EAST -> materialData.setFacingDirection(BlockFace.NORTH)
                                BlockFace.SOUTH -> materialData.setFacingDirection(BlockFace.EAST)
                                BlockFace.WEST -> materialData.setFacingDirection(BlockFace.SOUTH)
                            }
                        }
                    }
                } else {
                    when (rotation) {
                        Rotation.CLOCKWISE -> {
                            when (materialData.facing) {
                                BlockFace.NORTH -> materialData.setFacingDirection(BlockFace.WEST)
                                BlockFace.EAST -> materialData.setFacingDirection(BlockFace.NORTH)
                                BlockFace.SOUTH -> materialData.setFacingDirection(BlockFace.EAST)
                                BlockFace.WEST -> materialData.setFacingDirection(BlockFace.SOUTH)
                            }
                        }

                        Rotation.ANTICLOCKWISE -> {
                            when (materialData.facing) {
                                BlockFace.NORTH -> materialData.setFacingDirection(BlockFace.EAST)
                                BlockFace.EAST -> materialData.setFacingDirection(BlockFace.SOUTH)
                                BlockFace.SOUTH -> materialData.setFacingDirection(BlockFace.WEST)
                                BlockFace.WEST -> materialData.setFacingDirection(BlockFace.NORTH)
                            }
                        }
                    }
                }

                fromState.data = materialData
            }

            massBlockUpdate.setBlockState(x, y, z, fromState)
        }

        val torches = ArrayList<BlockState>()
        val oldBlockStates = ArrayList<BlockState>(blocks.size)
        for (b in blocks) {
            val newBlock = world.getBlockAt(getNewLocation(b.location))
            if (newBlock.type.isSolid && !containsBlock(newBlock)) { // Collision
                throw CouldNotMoveCraftException("Craft is obstructed by " + newBlock.type.toString() + " at " + newBlock.x +
                        ", " + newBlock.y + ", " + newBlock.z)
            }
            if (b.type == TORCH) torches.add(b.state) else oldBlockStates.add(b.state)
        }


        val onBoardEntities = onBoardEntities

        var cannons: ArrayList<Any>? = null
        if(NinCrafts.cannonsAPI != null) {
            cannons = ArrayList()
            Bukkit.getOnlinePlayers().filter { it is Player }.forEach { p ->
                (NinCrafts.cannonsAPI as at.pavlov.cannons.API.CannonsAPI)
                        .getCannons(oldBlockStates.map { it.location }, p.uniqueId).forEach { cannons.add(it) }
            }
        }



        blocks.clear()
        val massBlockUpdate: MassBlockUpdate = CraftMassBlockUpdate(plugin, world)
        massBlockUpdate.relightingStrategy = MassBlockUpdate.RelightingStrategy.NEVER

        for (s in oldBlockStates) {
            val newBlock = world.getBlockAt(getNewLocation(s.location))
            setBlockStateFast(s, newBlock.x, newBlock.y, newBlock.z, massBlockUpdate)
            blocks.add(newBlock)
        }

        torches.forEach {
            val newBlock = world.getBlockAt(getNewLocation(it.location))
            setBlockStateFast(it, newBlock.x, newBlock.y, newBlock.z, massBlockUpdate)
            blocks.add(newBlock)
        }

        onBoardEntities.forEach {
            val loc = it.location
            val newLoc = getNewLocation(it.location)

            newLoc.yaw = if (rotation == Rotation.CLOCKWISE) loc.yaw + 90 else loc.yaw - 90
            if (newLoc.yaw < -179) newLoc.yaw += 360
            if (newLoc.yaw > 180) newLoc.yaw -= 360
            newLoc.pitch = loc.pitch

            // Correction for teleport behaviour
            when (rotation) {
                Rotation.CLOCKWISE -> newLoc.x += 1
                Rotation.ANTICLOCKWISE -> newLoc.z += 1
            }

            it.teleport(newLoc, PlayerTeleportEvent.TeleportCause.PLUGIN)
        }

        // Update bounding box
        val first = getNewLocation(Location(world, boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ))
        val second = getNewLocation(Location(world, boundingBox.minX, boundingBox.minY, boundingBox.minZ))
        boundingBox.minX = first.x
        boundingBox.maxX = first.x
        boundingBox.minZ = first.z
        boundingBox.maxZ = first.z
        if (second.x.toInt() < boundingBox.minX) boundingBox.minX = second.x
        if (second.x.toInt() > boundingBox.maxX) boundingBox.maxX = second.x
        if (second.z.toInt() < boundingBox.minZ) boundingBox.minZ = second.z
        if (second.z.toInt() > boundingBox.maxZ) boundingBox.maxZ = second.z

        cannons?.forEach { if(it !is at.pavlov.cannons.cannon.Cannon) { return }
            if (rotation == Rotation.CLOCKWISE) it.rotateRight(rotationPoint.toVector()) else it.rotateLeft(rotationPoint.toVector()) }


        for (s in oldBlockStates) {
            if (!containsBlock(s.block)) {
                if (s.y < waterLevel) massBlockUpdate.setBlock(s.x, s.y, s.z, WATER) else massBlockUpdate.setBlock(s.x, s.y, s.z, AIR)
            }
        }

        for (s in torches) {
            if (!containsBlock(s.block)) {
                if (s.y < waterLevel) massBlockUpdate.setBlock(s.x, s.y, s.z, WATER) else massBlockUpdate.setBlock(s.x, s.y, s.z, AIR)
            }
        }

        blockProtector.updateAllLocationsRotated(rotation, rotationPoint)
        massBlockUpdate.notifyClients()
    }

    override fun move(relativeX: Int, relativeY: Int, relativeZ: Int) {
        if (boundingBox.minY + relativeY < 1) throw CouldNotMoveCraftException("Craft can not descend below " + boundingBox.minY + relativeY + ".")
        if (relativeX == 0 && relativeY == 0 && relativeZ == 0) return

        val torches = ArrayList<BlockState>()
        val oldBlockStates = ArrayList<BlockState>(blocks.size)
        for (b in blocks) {
            val newBlock = world.getBlockAt(b.x + relativeX, b.y + relativeY, b.z + relativeZ)
            if (newBlock.type.isSolid && !containsBlock(newBlock)) { // Collision
                // Notify everyone on board
                throw CouldNotMoveCraftException("Craft is obstructed by " + newBlock.type.toString() + " at " + newBlock.x +
                        ", " + newBlock.y + ", " + newBlock.z)
            }
            if (b.type == TORCH) torches.add(b.state) else oldBlockStates.add(b.state)
        }

        val onBoardEntities = onBoardEntities
        var cannons: ArrayList<Any>? = null
        if(NinCrafts.cannonsAPI != null) {
            cannons = ArrayList()
            Bukkit.getOnlinePlayers().forEach {
                p -> cannons.addAll((NinCrafts.cannonsAPI as at.pavlov.cannons.API.CannonsAPI)
                    .getCannons(oldBlockStates.map { it.location }, p.uniqueId))
            }
        }


        blocks.clear()
        val massBlockUpdate: MassBlockUpdate = CraftMassBlockUpdate(NinCrafts.instance, world)
        massBlockUpdate.relightingStrategy = MassBlockUpdate.RelightingStrategy.NEVER

        for (s in oldBlockStates) {
            val newBlock = world.getBlockAt(s.x + relativeX, s.y + relativeY, s.z + relativeZ)
            massBlockUpdate.setBlockState(newBlock.x, newBlock.y, newBlock.z, s)
            blocks.add(newBlock)
        }

        torches.forEach {
            val newBlock = world.getBlockAt(it.x + relativeX, it.y + relativeY, it.z + relativeZ)
            massBlockUpdate.setBlockState(newBlock.x, newBlock.y, newBlock.z, it)
            blocks.add(newBlock)
        }

        onBoardEntities.forEach {
            val newLoc = it.location
            newLoc.x += relativeX
            newLoc.z += relativeZ
            newLoc.y += relativeY
            val blockState = newLoc.block.state
            val blockData = blockState.data
            if (blockData is Ladder) { // This is to prevent players getting stuck in ladders
                val amount = 0.1
                when (blockData.facing) {
                    BlockFace.SOUTH -> if (newLoc.z < newLoc.z + amount) newLoc.z += amount
                    BlockFace.WEST -> if (newLoc.x > newLoc.x - amount) newLoc.x -= amount
                    BlockFace.NORTH -> if (newLoc.z > newLoc.z - amount) newLoc.z -= amount
                    BlockFace.EAST -> if (newLoc.x < newLoc.x + amount) newLoc.x += amount
                    else -> throw IllegalStateException()
                }
            }
            it.teleport(newLoc, PlayerTeleportEvent.TeleportCause.PLUGIN)
        }

        boundingBox.minX += relativeX
        boundingBox.maxX += relativeX
        boundingBox.minZ += relativeZ
        boundingBox.maxZ += relativeZ
        boundingBox.minY += relativeY
        boundingBox.maxY += relativeY

        rotationPoint.x += relativeX
        rotationPoint.z += relativeZ
        rotationPoint.y += relativeY

        cannons?.forEach { if(it !is at.pavlov.cannons.cannon.Cannon) { return }
            it.move(Vector(relativeX, relativeY, relativeZ)) }

        for (s in oldBlockStates) {
            if (!containsBlock(s.block)) {
                if (s.y < waterLevel) massBlockUpdate.setBlock(s.x, s.y, s.z, WATER) else massBlockUpdate.setBlock(s.x, s.y, s.z, AIR)
            }
        }

        for (s in torches) {
            if (!containsBlock(s.block)) {
                if (s.y < waterLevel) massBlockUpdate.setBlock(s.x, s.y, s.z, WATER) else massBlockUpdate.setBlock(s.x, s.y, s.z, AIR)
            }
        }

        blockProtector.updateAllLocations(world, relativeX, relativeY, relativeZ)
        massBlockUpdate.notifyClients()
    }
}