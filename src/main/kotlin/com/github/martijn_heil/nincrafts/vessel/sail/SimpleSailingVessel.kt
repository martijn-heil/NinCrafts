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

import com.github.martijn_heil.nincrafts.NinCrafts
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Material.*
import org.bukkit.block.Block
import org.bukkit.block.Sign
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.EventPriority.HIGHEST
import org.bukkit.event.EventPriority.MONITOR
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPhysicsEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.material.Door
import org.bukkit.plugin.Plugin
import com.github.martijn_heil.nincrafts.Rotation
import com.github.martijn_heil.nincrafts.Rotation.ANTICLOCKWISE
import com.github.martijn_heil.nincrafts.Rotation.CLOCKWISE
import com.github.martijn_heil.nincrafts.RowingDirection
import com.github.martijn_heil.nincrafts.RowingDirection.BACKWARD
import com.github.martijn_heil.nincrafts.RowingDirection.FORWARD
import com.github.martijn_heil.nincrafts.exception.CouldNotMoveCraftException
import com.github.martijn_heil.nincrafts.util.detectFloodFill
import com.github.martijn_heil.nincrafts.util.getRotatedLocation
import com.github.martijn_heil.nincrafts.vessel.HasRudder
import com.github.martijn_heil.nincrafts.vessel.SimpleRudder
import com.github.martijn_heil.nincrafts.vessel.SimpleSurfaceShip
import java.lang.Math.*
import java.util.*
import java.util.logging.Logger


open class SimpleSailingVessel protected constructor(protected val plugin: Plugin, protected val logger: Logger,
                                                     blocks: ArrayList<Block>, rotationPoint: Location,
                                                     override val sails: Collection<SimpleSail>,
                                                     protected val rudder: SimpleRudder,
                                                     protected var rowingSign: Sign,
                                                     protected var rowingDirectionSign: Sign) :
        SimpleSurfaceShip(plugin, blocks, rotationPoint), HasSail, HasRudder, AutoCloseable {
    protected open var rowingSpeed = 1000
    protected open var normalMaxSpeed: Int = 10000                     // in metres per hour
    protected open var speedPerSquareMetreOfSail: Double = 0.0         // in metres per hour
    protected open val accelerationPerUpdate: Int get() = normalMaxSpeed / 8 // in metres per hour
    protected open val decelerationPerUpdate: Int get() = normalMaxSpeed / 8 // in metres per hour
    protected open var updateInterval = 40                             // interval in Minecraft game ticks. A single tick is 50ms
    protected open var updateTaskId = 0
    protected open var isRowing: Boolean
        get() = rowingSign.getLine(1).toBoolean()
        set(value) { rowingSign.setLine(1, value.toString()); rowingSign.update(true, false) }
    protected open var rowingDirection: RowingDirection
        get() = if(rowingDirectionSign.getLine(1) == FORWARD.toString().toLowerCase()) FORWARD else BACKWARD
        set(value) {
            rowingDirectionSign.setLine(1, value.toString().toLowerCase())
            rowingDirectionSign.update(true, false)
        }
    override val minWindAngle = 40

    protected open val currentMaxSpeed: Double
        get() {
            var angleToWind = NinCrafts.instance.windFrom - heading
            if (angleToWind > 180) angleToWind = 360 - angleToWind
            if (angleToWind < 0) angleToWind = -(angleToWind)
            if(angleToWind >= minWindAngle) {
                var tmp = currentSailSurfaceArea * speedPerSquareMetreOfSail
                if(isRowing) {
                    if(rowingDirection == FORWARD) tmp += rowingSpeed else tmp -= rowingSpeed
                }
                return tmp
            } else {
                val allSailsLowered = sails.filter { it.isHoisted }.isEmpty()
                if(isRowing && allSailsLowered) {
                    return if(rowingDirection == FORWARD) rowingSpeed.toDouble() else -rowingSpeed.toDouble()
                } else {
                    return 0.0
                }
            }
        }

    protected open val currentSailSurfaceArea: Int
        get() {
            var surfaceArea = 0
            sails.forEach { surfaceArea += it.currentSurfaceArea }
            return surfaceArea
        }

    protected open val distancePerUpdate: Double
        get() = speed / 60 / 60 * (updateInterval / 20).toDouble()

    protected open var increaseX: Int = 0
    protected open var increaseZ: Int = 0

    protected open var speed: Double = 0.0 // in metres per second
        protected set(value) {
            field = value // important we set this first
            var angle = 360 - heading.toDouble() + 90
            if (angle > 360) angle -= 360
            val radians = toRadians(angle)
            increaseX = round(0.0 + (distancePerUpdate * cos(radians))).toInt()
            increaseZ = round(0.0 - (distancePerUpdate * sin(radians))).toInt()
        }

    override var heading: Int = 0
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
                var rotation = CLOCKWISE
                var amount = 0

                when (currentlyFacing) {
                    0 -> {
                        when (shouldFaceNext) {
                            90 -> {
                                rotation = CLOCKWISE; amount = 1
                            }
                            180 -> {
                                rotation = CLOCKWISE; amount = 2
                            }
                            270 -> {
                                rotation = ANTICLOCKWISE; amount = 1
                            }
                        }
                    }
                    90 -> {
                        when (shouldFaceNext) {
                            0 -> {
                                rotation = ANTICLOCKWISE; amount = 1
                            }
                            180 -> {
                                rotation = CLOCKWISE; amount = 1
                            }
                            270 -> {
                                rotation = CLOCKWISE; amount = 2
                            }
                        }
                    }
                    180 -> {
                        when (shouldFaceNext) {
                            0 -> {
                                rotation = CLOCKWISE; amount = 2
                            }
                            90 -> {
                                rotation = ANTICLOCKWISE; amount = 1
                            }
                            270 -> {
                                rotation = CLOCKWISE; amount = 1
                            }
                        }
                    }
                    270 -> {
                        when (shouldFaceNext) {
                            0 -> {
                                rotation = CLOCKWISE; amount = 1
                            }
                            90 -> {
                                rotation = CLOCKWISE; amount = 2
                            }
                            180 -> {
                                rotation = ANTICLOCKWISE; amount = 1
                            }
                        }
                    }
                }

                for (i in 1..amount) {
                    rotate(rotation)
                }

                currentlyFacing = shouldFaceNext
            }

            var angle = 360 - value.toDouble() + 90
            if (angle > 360) angle -= 360
            val radians = toRadians(angle)
            increaseX = round(0.0 + (distancePerUpdate * cos(radians))).toInt()
            increaseZ = round(0.0 - (distancePerUpdate * sin(radians))).toInt()
            field = value
        }
    var currentlyFacing: Int = -1

    override var isSinking = false
        set(value) {
            if(value) this.sails.forEach { it.destroy() }
            super.isSinking = value
            field = value
        }

    protected open val listener = object : Listener {
        @EventHandler(ignoreCancelled = true, priority = HIGHEST)
        fun onPlayerInteract(e: PlayerInteractEvent) {
            if (e.clickedBlock != null) {
                if(e.clickedBlock == rowingSign.block) isRowing = !isRowing
                if(e.clickedBlock == rowingDirectionSign.block) { rowingDirection = if(rowingDirection == FORWARD) BACKWARD else FORWARD }
            }
        }

        @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
        fun onEntityExplode(e: EntityExplodeEvent) {
            e.blockList().forEach { if(boundingBox.contains(it)) removeBlock(it) }
        }

        @EventHandler(ignoreCancelled = true, priority = MONITOR)
        fun onBlockBreak(e: BlockBreakEvent) {
            if (boundingBox.contains(e.block)) removeBlock(e.block)
        }

        @EventHandler(ignoreCancelled = true)
        fun onBlockPhysics(e: BlockPhysicsEvent) {
            if (boundingBox.contains(e.block)) e.isCancelled = true // TODO
        }

        @EventHandler(ignoreCancelled = true, priority = MONITOR)
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
            if(containsBlock) {
                logger.info("contains block!")
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

    override fun init() {
        super.init()
        heading = rudder.course
        rudder.onChangeCourseCallback = { onChangeCourseCallback(it) }

        var maxSailSurfaceArea = 0
        sails.forEach { maxSailSurfaceArea += it.maxSurfaceArea }
        speedPerSquareMetreOfSail = normalMaxSpeed / maxSailSurfaceArea.toDouble()

        val updateTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(NinCrafts.instance, {
            try {
                update()
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }, 0, updateInterval.toLong())
        if (updateTaskId == -1) {
            throw Exception("Could not schedule Bukkit task.")
        }
        Bukkit.getPluginManager().registerEvents(listener, NinCrafts.instance)

        blockProtector.protectedBlocks.add(rowingSign.location)
        blockProtector.protectedBlocks.add(rowingSign.block.getRelative((rowingSign.data as org.bukkit.material.Sign).attachedFace).location)
        blockProtector.protectedBlocks.add(rowingDirectionSign.location)
        blockProtector.protectedBlocks.add(rowingDirectionSign.block.getRelative((rowingDirectionSign.data as org.bukkit.material.Sign).attachedFace).location)
    }

    private fun update() {
        if(isSinking) return
        val currentMaxSpeed = this.currentMaxSpeed // just cache the value, less recomputing
        if (speed < currentMaxSpeed) { // Should accelerate
            if ((currentMaxSpeed - speed) < accelerationPerUpdate) {
                speed = currentMaxSpeed
            } else {
                speed += accelerationPerUpdate
            }
        } else if (speed > currentMaxSpeed) { // Should decelerate
            speed -= decelerationPerUpdate
            if (speed < currentMaxSpeed) speed = currentMaxSpeed
        }

        val newLoc = location.clone()
        newLoc.x += increaseX
        newLoc.z += increaseZ
        try {
            location = newLoc
        } catch(e: CouldNotMoveCraftException) {
            onBoardEntities.filter { it is Player }.forEach { it.sendMessage(ChatColor.RED.toString() + e.message) }
        }
    }

    override var location: Location
        get() = super.location
        set(value) {
            super.location = value
        }

    override fun move(relativeX: Int, relativeY: Int, relativeZ: Int) {
        super.move(relativeX, relativeY, relativeZ)
        rudder.updateLocation(relativeX, relativeY, relativeZ)
        sails.forEach { it.updateLocation(relativeX, relativeY, relativeZ) }
        rowingSign = world.getBlockAt(rowingSign.x + relativeX, rowingSign.y + relativeY, rowingSign.z + relativeZ).state as Sign
        rowingDirectionSign = world.getBlockAt(rowingDirectionSign.x + relativeX, rowingDirectionSign.y + relativeY, rowingDirectionSign.z + relativeZ).state as Sign
    }

    override fun rotate(rotation: Rotation) {
        super.rotate(rotation)
        rudder.updateLocationRotated(rotationPoint, rotation)
        sails.forEach { it.updateLocationRotated(rotationPoint, rotation) }
        rowingSign = world.getBlockAt(getRotatedLocation(rotationPoint, rotation, rowingSign.location)).state as Sign
        rowingDirectionSign = world.getBlockAt(getRotatedLocation(rotationPoint, rotation, rowingDirectionSign.location)).state as Sign
    }

    override fun close() {
        super.close()
        HandlerList.unregisterAll(listener)
        sails.forEach { if (it is AutoCloseable) it.close() }
        rudder.close()
        Bukkit.getScheduler().cancelTask(updateTaskId)
    }

    companion object {
        fun detect(plugin: Plugin, logger: Logger, detectionLoc: Location): SimpleSailingVessel {
            val sails: MutableCollection<SimpleSail> = ArrayList()
            try {
                val maxSize = 5000
                val allowedBlocks: Collection<Material> = Material.values().filter { it != AIR && it != WATER && it != LEGACY_STATIONARY_WATER && it != LAVA && it != LEGACY_STATIONARY_LAVA }
                val blocks: Collection<Block>
                // Detect vessel
                try {
                    logger.info("Detecting sailing vessel at " + detectionLoc.x + "x " + detectionLoc.y + "y " + detectionLoc.z + "z")
                    blocks = detectFloodFill(detectionLoc, allowedBlocks, false, maxSize)
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
                if(rowingSign.lines[1] == "") rowingSign.setLine(1, "false")
                logger.info("Found rowing sign at " + rowingSign.x + " " + rowingSign.y + " " + rudderSign.z)

                val rowingDirectionSign = signs.find { it.lines[0] == "[RowingDirection]" } ?: throw IllegalStateException("No rowing direction sign found.")
                if(rowingDirectionSign.lines[1] == "") rowingDirectionSign.setLine(1, FORWARD.toString().toLowerCase())
                logger.info("Found RowingDirection sign at " + rowingDirectionSign.x + " " + rowingDirectionSign.y + " " + rowingDirectionSign.z)

                // Detect sails
                signs.filter { it.lines[0] == "[Sail]" }.forEach {
                    logger.fine("Found sail sign at " + it.x + " " + it.y + " " + it.z)
                    sails.add(SimpleSail(plugin, it))
                }
                if (sails.isEmpty()) throw IllegalStateException("No sails found.")

                val simpleSailingVessel = SimpleSailingVessel(plugin, logger, blocks, rotationPoint, sails, rudder, rowingSign, rowingDirectionSign)
                simpleSailingVessel.init()
                return simpleSailingVessel
            } catch(t: Throwable) {
                sails.forEach { it.isHoisted = true }
                throw t
            }
        }
    }
}
