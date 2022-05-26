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
import com.github.martijn_heil.nincrafts.util.*
import com.github.martijn_heil.nincrafts.util.nms.CraftMassBlockUpdate
import org.bukkit.Location
import org.bukkit.Material.*
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.BlockState
import org.bukkit.entity.Entity
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.plugin.Plugin
import kotlin.collections.HashMap
import kotlin.streams.asSequence


open class SimpleCraft(private val plugin: Plugin, blocks: Collection<Block>, rotationPoint: Location)
    : MoveableCraft, RotatableCraft, AutoCloseable {

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
    protected open var blocks: MutableMap<RelativeBlock, BlockState> =
        HashMap(blocks
            .asSequence()
            .map { RelativeBlock.fromAbsoluteLocation(location, it.location) }
            .associateWith { it.resolve(location).state }
        )

    override val onBoardEntities: List<Entity>
        get() {
            val widthX = (boundingBox.maxX - boundingBox.minX)
            val widthY = (boundingBox.maxY - boundingBox.minY)
            val widthZ = (boundingBox.maxZ - boundingBox.minZ)
            val centerX = boundingBox.minX + widthX / 2
            val centerY = boundingBox.minY + widthY / 2
            val centerZ = boundingBox.minZ + widthZ / 2
            // For some reason that filter is really required
            return world.getNearbyEntities(Location(world, centerX, centerY, centerZ), widthX, widthY, widthZ)
                .filter { boundingBox.contains(it.location) }
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

    fun getBlock(at: RelativeBlock) = at.resolve(location)

    fun relativeBlockToLocation(at: RelativeBlock) = Location(world,
        location.x + at.x.toDouble(), location.y + at.y.toDouble(), location.z + at.z.toDouble())

    fun getRelativeBlock(at: Block) = RelativeBlock(
        location.x.toInt() - at.x,
        location.y.toInt() - at.y,
        location.z.toInt() - at.z
    )

    open fun containsBlock(relative: RelativeBlock) = blocks.contains(relative)
    open fun containsBlock(block: Block) = blocks.contains(RelativeBlock.fromAbsoluteLocation(location, block.location))

    open fun containsBlockAny(blocks: Collection<Block>) =
        blocks.parallelStream().anyMatch { this.blocks.contains(RelativeBlock.fromAbsoluteLocation(location, it.location)) }

    open fun addBlock(block: Block) {
        blocks[getRelativeBlock(block)] = block.state
        if (block.x > boundingBox.maxX) boundingBox.maxX = block.x.toDouble()
        if (block.y > boundingBox.maxY) boundingBox.maxY = block.y.toDouble()
        if (block.z > boundingBox.maxZ) boundingBox.maxZ = block.z.toDouble()

        if (block.x < boundingBox.minX) boundingBox.minX = block.x.toDouble()
        if (block.y < boundingBox.minY) boundingBox.minY = block.y.toDouble()
        if (block.z < boundingBox.minZ) boundingBox.minZ = block.z.toDouble()
    }

    open fun removeBlock(block: Block) = blocks.remove(getRelativeBlock(block))

    open fun restoreBlockInWake(block: Location, massBlockUpdate: MassBlockUpdate) =
        massBlockUpdate.setBlock(block.blockX, block.blockY, block.blockZ, AIR)

    open fun restoreBlockInWake(block: Block, massBlockUpdate: MassBlockUpdate) =
        restoreBlockInWake(block.location, massBlockUpdate)

    open fun doesObstructCraft(block: Block) = block.isSolid


    override fun rotate(rotation: Rotation) {
        fun getNewLocation(loc: Location): Location = getRotatedLocation(rotationPoint, rotation, loc)
        fun getNewRelativeBlock(loc: RelativeBlock): RelativeBlock = getRotatedRelativeBlock(rotation, loc)

        fun setBlockStateFast(fromState: BlockState, x: Int, y: Int, z: Int, massBlockUpdate: MassBlockUpdate) {
            val blockData = fromState.blockData
            if (blockData is org.bukkit.block.data.Directional) {
                if (blockData is org.bukkit.block.data.Attachable && fromState.type != LADDER) {
                    when (rotation) {
                        Rotation.CLOCKWISE -> {
                            when (blockData.facing.oppositeFace) {
                                BlockFace.NORTH -> blockData.facing = BlockFace.EAST
                                BlockFace.EAST -> blockData.facing = BlockFace.SOUTH
                                BlockFace.SOUTH -> blockData.facing = BlockFace.WEST
                                BlockFace.WEST -> blockData.facing = BlockFace.NORTH
                            }
                        }

                        Rotation.ANTICLOCKWISE -> {
                            when (blockData.facing.oppositeFace) {
                                BlockFace.NORTH -> blockData.facing = BlockFace.WEST
                                BlockFace.EAST -> blockData.facing = BlockFace.NORTH
                                BlockFace.SOUTH -> blockData.facing = BlockFace.EAST
                                BlockFace.WEST -> blockData.facing = BlockFace.SOUTH
                            }
                        }
                    }
                } else {
                    when (rotation) {
                        Rotation.CLOCKWISE -> {
                            when (blockData.facing.oppositeFace) {
                                BlockFace.NORTH -> blockData.facing = BlockFace.WEST
                                BlockFace.EAST -> blockData.facing = BlockFace.NORTH
                                BlockFace.SOUTH -> blockData.facing = BlockFace.EAST
                                BlockFace.WEST -> blockData.facing = BlockFace.SOUTH
                            }
                        }

                        Rotation.ANTICLOCKWISE -> {
                            when (blockData.facing.oppositeFace) {
                                BlockFace.NORTH -> blockData.facing = BlockFace.EAST
                                BlockFace.EAST -> blockData.facing = BlockFace.SOUTH
                                BlockFace.SOUTH -> blockData.facing = BlockFace.WEST
                                BlockFace.WEST -> blockData.facing = BlockFace.NORTH
                            }
                        }
                    }
                }

                fromState.blockData = blockData
            }

            if (fromState.type == world.getBlockAt(x, y, z).type) return
            massBlockUpdate.setBlockState(x, y, z, fromState)
        }

        val newLocation = getNewLocation(location)

        for (b in blocks) {
            val newBlock = b.key.resolve(newLocation)
            if (doesObstructCraft(newBlock) && !containsBlock(RelativeBlock.fromAbsoluteLocation(location, newBlock.location))) { // Collision
                throw CouldNotMoveCraftException("Craft is obstructed by " + newBlock.type.toString() + " at " + newBlock.x +
                        ", " + newBlock.y + ", " + newBlock.z)
            }
        }


        val onBoardEntities = onBoardEntities
//        var cannons: ArrayList<Any>? = null
//        if (NinCrafts.cannonsAPI != null) {
//            cannons = ArrayList() // TODO only detectFloodFill cannons when detecting ship, saves time
//            val cannonsAPI = NinCrafts.cannonsAPI as at.pavlov.cannons.API.CannonsAPI
//            onBoardEntities
//                    .filterIsInstance<Player>()
//                    .forEach { p ->
//                        cannonsAPI.getCannons(oldBlockStates
//                                .map { it.location }, p.uniqueId).forEach { cannons.add(it) }
//                    }
//        }


        val massBlockUpdate: MassBlockUpdate = CraftMassBlockUpdate(plugin, world)
        massBlockUpdate.relightingStrategy = MassBlockUpdate.RelightingStrategy.NEVER

        // Set new blocks
        for (b in blocks) {
            val newBlock = b.key.resolve(newLocation)
            setBlockStateFast(b.value, newBlock.x, newBlock.y, newBlock.z, massBlockUpdate)
        }

        // Teleport entities
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


//        cannons?.forEach {
//            if (it !is at.pavlov.cannons.cannon.Cannon) {
//                return
//            }
//            if (rotation == Rotation.CLOCKWISE) it.rotateRight(rotationPoint.toVector()) else it.rotateLeft(rotationPoint.toVector())
//        }

        for (b in blocks) {
            val oldBlockLocation = b.key.resolveToLocation(location)
            if (!containsBlock(RelativeBlock.fromAbsoluteLocation(newLocation, oldBlockLocation))) {
                restoreBlockInWake(oldBlockLocation, massBlockUpdate)
            }
        }

        blockProtector.updateAllLocationsRotated(rotation, rotationPoint)

        massBlockUpdate.notifyClients()
    }

    protected open fun move(relativeX: Int, relativeY: Int, relativeZ: Int) {
        if (boundingBox.minY + relativeY < 1) throw CouldNotMoveCraftException("Craft can not descend any further.")
        if (relativeX == 0 && relativeY == 0 && relativeZ == 0) return

        val newLocation = location + Location(world, relativeX.toDouble(), relativeY.toDouble(), relativeZ.toDouble())

        // Check for block obstruction
        for (b in blocks) {
            val newBlock = b.key.resolve(newLocation)
            if (doesObstructCraft(newBlock) && !containsBlock(newBlock)) { // Collision
                // Notify everyone on board
                throw CouldNotMoveCraftException("Craft is obstructed by " + newBlock.type.toString() + " at " + newBlock.x +
                        ", " + newBlock.y + ", " + newBlock.z)
            }
        }

        val onBoardEntities = onBoardEntities
//        var cannons: ArrayList<Any>? = null
//        if (NinCrafts.cannonsAPI != null) {
//            cannons = ArrayList() // TODO only detectFloodFill cannons when detecting ship, saves time
//            val cannonsAPI = NinCrafts.cannonsAPI as at.pavlov.cannons.API.CannonsAPI
//            onBoardEntities
//                    .filterIsInstance<Player>()
//                    .forEach { p ->
//                        cannonsAPI.getCannons(oldBlockStates
//                                .map { it.location }, p.uniqueId).forEach { cannons.add(it) }
//                    }
//        }


        val massBlockUpdate: MassBlockUpdate = CraftMassBlockUpdate(NinCrafts.instance, world)
        massBlockUpdate.relightingStrategy = MassBlockUpdate.RelightingStrategy.NEVER

        for (b in blocks) {
            val newBlockLocation = b.key.resolveToLocation(newLocation)
            massBlockUpdate.setBlockState(newBlockLocation.blockX, newBlockLocation.blockY, newBlockLocation.blockZ, b.value)
        }

        // Teleport entities
        onBoardEntities.forEach {
            val newLoc = it.location
            newLoc.x += relativeX
            newLoc.z += relativeZ
            newLoc.y += relativeY
            val blockState = newLoc.block.state
            val blockData = blockState.blockData

            // This is to prevent players getting stuck in ladders
            if (blockData is org.bukkit.block.data.type.Ladder && blockData.facing != null) {
                val amount = 0.1
                when (blockData.facing) {
                    BlockFace.SOUTH -> if (newLoc.z < newLoc.z + amount) newLoc.z += amount
                    BlockFace.WEST -> if (newLoc.x > newLoc.x - amount) newLoc.x -= amount
                    BlockFace.NORTH -> if (newLoc.z > newLoc.z - amount) newLoc.z -= amount
                    BlockFace.EAST -> if (newLoc.x < newLoc.x + amount) newLoc.x += amount
                    else -> throw IllegalStateException()
                }
            }

            val velo = it.velocity
            it.teleport(newLoc, PlayerTeleportEvent.TeleportCause.PLUGIN)
            it.velocity = velo
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

//        cannons?.forEach {
//            if (it !is at.pavlov.cannons.cannon.Cannon) {
//                return
//            }
//            it.move(Vector(relativeX, relativeY, relativeZ))
//        }

        for (b in blocks) {
            val oldBlockLocation = b.key.resolveToLocation(location)
            if (containsBlock(b.key)) continue
            restoreBlockInWake(oldBlockLocation, massBlockUpdate)
        }

        blockProtector.updateAllLocations(world, relativeX, relativeY, relativeZ)

        massBlockUpdate.notifyClients()
    }

    override fun close() {
        blockProtector.close()
    }
}
