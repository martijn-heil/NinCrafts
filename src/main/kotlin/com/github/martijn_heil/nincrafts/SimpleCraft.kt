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

package com.github.martijn_heil.nincrafts

import com.github.martijn_heil.nincrafts.exception.CouldNotMoveCraftException
import com.github.martijn_heil.nincrafts.util.BlockProtector
import com.github.martijn_heil.nincrafts.util.BoundingBox
import com.github.martijn_heil.nincrafts.util.MassBlockUpdate
import com.github.martijn_heil.nincrafts.util.getRotatedLocation
import com.github.martijn_heil.nincrafts.util.nms.CraftMassBlockUpdate
import org.bukkit.Location
import org.bukkit.Material.*
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.BlockState
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.material.Attachable
import org.bukkit.material.Directional
import org.bukkit.material.Ladder
import org.bukkit.plugin.Plugin
import org.bukkit.util.Vector
import java.util.*


open class SimpleCraft(private val plugin: Plugin, blocks: Collection<Block>, rotationPoint: Location) : MoveableCraft, RotatableCraft, AutoCloseable {

    var boundingBox: BoundingBox = BoundingBox(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    protected val blockProtector = BlockProtector(plugin)
    protected var rotationPoint: Location = rotationPoint.clone()
    override var location: Location = rotationPoint
        set(value) {
            if (value != field) {
                move((value.x - location.x).toInt(), (value.y - location.y).toInt(), (value.z - location.z).toInt())
                field = value
            }
        }

    protected open val world: World get() = location.world!!
    protected open var blocks: ArrayList<Block> = ArrayList(blocks)

    override val onBoardEntities: List<Entity>
        get() {
            val widthX = (boundingBox.maxX - boundingBox.minX)
            val widthY = (boundingBox.maxY - boundingBox.minY)
            val widthZ = (boundingBox.maxZ - boundingBox.minZ)
            val centerX = boundingBox.minX + widthX / 2
            val centerY = boundingBox.minY + widthY / 2
            val centerZ = boundingBox.minZ + widthZ / 2
            // For some reason that filter is really required
            return world.getNearbyEntities(Location(world, centerX, centerY, centerZ), widthX, widthY, widthZ).filter { boundingBox.contains(it.location) }
        }


    init {
        val first = blocks.first()
        boundingBox.minX = first.x.toDouble()
        boundingBox.maxX = first.x.toDouble()
        boundingBox.minY = first.y.toDouble()
        boundingBox.maxY = first.y.toDouble()
        boundingBox.maxZ = first.z.toDouble()
        boundingBox.minZ = first.z.toDouble()

        blocks.forEach {
            if (it.x < boundingBox.minX) boundingBox.minX = it.x.toDouble()
            if (it.x > boundingBox.maxX) boundingBox.maxX = it.x.toDouble()
            if (it.y < boundingBox.minY) boundingBox.minY = it.y.toDouble()
            if (it.y > boundingBox.maxY) boundingBox.maxY = it.y.toDouble()
            if (it.z < boundingBox.minZ) boundingBox.minZ = it.z.toDouble()
            if (it.z > boundingBox.maxZ) boundingBox.maxZ = it.z.toDouble()
        }
    }

    open fun containsBlock(block: Block) = boundingBox.contains(block) && blocks.parallelStream().anyMatch { it == block }

    open fun containsBlockAny(blocks: Collection<Block>) = this.blocks
            .parallelStream()
            .anyMatch { val shipBlock = it; blocks.parallelStream()
                    .anyMatch { boundingBox.contains(it) && it == shipBlock } }

    open fun addBlock(block: Block) = {
        blocks.add(block)
        if (block.x > boundingBox.maxX) boundingBox.maxX = block.x.toDouble()
        if (block.y > boundingBox.maxY) boundingBox.maxY = block.y.toDouble()
        if (block.z > boundingBox.maxZ) boundingBox.maxZ = block.z.toDouble()

        if (block.x < boundingBox.minX) boundingBox.minX = block.x.toDouble()
        if (block.y < boundingBox.minY) boundingBox.minY = block.y.toDouble()
        if (block.z < boundingBox.minZ) boundingBox.minZ = block.z.toDouble()
    }

    open fun removeBlock(block: Block) = blocks.remove(block)


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
        if (NinCrafts.cannonsAPI != null) {
            cannons = ArrayList() // TODO only detectFloodFill cannons when detecting ship, saves time
            val cannonsAPI = NinCrafts.cannonsAPI as at.pavlov.cannons.API.CannonsAPI
            onBoardEntities
                    .filter { it is Player }
                    .forEach { p ->
                        cannonsAPI.getCannons(oldBlockStates
                                .map { it.location }, p.uniqueId).forEach { cannons.add(it) }
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


        cannons?.forEach {
            if (it !is at.pavlov.cannons.cannon.Cannon) {
                return
            }
            if (rotation == Rotation.CLOCKWISE) it.rotateRight(rotationPoint.toVector()) else it.rotateLeft(rotationPoint.toVector())
        }

        for (s in oldBlockStates) {
            if (!containsBlock(s.block)) massBlockUpdate.setBlock(s.x, s.y, s.z, AIR)
        }

        for (s in torches) {
            if (!containsBlock(s.block)) massBlockUpdate.setBlock(s.x, s.y, s.z, AIR)
        }

        blockProtector.updateAllLocationsRotated(rotation, rotationPoint)

        massBlockUpdate.notifyClients()
    }

    protected open fun move(relativeX: Int, relativeY: Int, relativeZ: Int) {
        if (boundingBox.minY + relativeY < 1) throw CouldNotMoveCraftException("Craft can not descend any further.")
        if (relativeX == 0 && relativeY == 0 && relativeZ == 0) return

        val torches = ArrayList<BlockState>()
        val oldBlockStates = ArrayList<BlockState>(blocks.size)
        for (b in blocks) {
            val newBlock = world.getBlockAt(b.x + relativeX, b.y + relativeY, b.z + relativeZ)
            if (newBlock.type != AIR && !containsBlock(newBlock)) { // Collision
                // Notify everyone on board
                throw CouldNotMoveCraftException("Craft is obstructed by " + newBlock.type.toString() + " at " + newBlock.x +
                        ", " + newBlock.y + ", " + newBlock.z)
            }
            if (b.type == TORCH) torches.add(b.state) else oldBlockStates.add(b.state)
        }

        val onBoardEntities = onBoardEntities
        var cannons: ArrayList<Any>? = null
        if (NinCrafts.cannonsAPI != null) {
            cannons = ArrayList() // TODO only detectFloodFill cannons when detecting ship, saves time
            val cannonsAPI = NinCrafts.cannonsAPI as at.pavlov.cannons.API.CannonsAPI
            onBoardEntities
                    .filter { it is Player }
                    .forEach { p ->
                        cannonsAPI.getCannons(oldBlockStates
                                .map { it.location }, p.uniqueId).forEach { cannons.add(it) }
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

        cannons?.forEach {
            if (it !is at.pavlov.cannons.cannon.Cannon) {
                return
            }
            it.move(Vector(relativeX, relativeY, relativeZ))
        }

        for (s in oldBlockStates) {
            if (!containsBlock(s.block)) massBlockUpdate.setBlock(s.x, s.y, s.z, AIR)
        }

        for (s in torches) {
            if (!containsBlock(s.block)) massBlockUpdate.setBlock(s.x, s.y, s.z, AIR)
        }

        blockProtector.updateAllLocations(world, relativeX, relativeY, relativeZ)

        massBlockUpdate.notifyClients()
    }

    override fun close() {
        blockProtector.close()
    }
}
