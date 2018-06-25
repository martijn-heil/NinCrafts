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

import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Sign
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import com.github.martijn_heil.nincrafts.Rotation
import com.github.martijn_heil.nincrafts.exception.CouldNotMoveCraftException
import com.github.martijn_heil.nincrafts.util.detectFloodFill
import java.io.Closeable


class MotorShip(plugin: Plugin, detectionPoint: Location, private val updateInterval: Long) : Ship, Closeable {
    private var simpleSurfaceShip: SimpleSurfaceShip

    private val distancePerUpdate: Double
        get() = speed / 60 / 60 * (updateInterval / 20).toDouble()

    private var increaseX: Int = 0
    private var increaseZ: Int = 0

    var speed: Double = 0.0 // in metres per second
        set(value) {
            field = value // important we set this first
            var angle = 360 - heading.toDouble() + 90
            if (angle > 360) angle -= 360
            val radians = Math.toRadians(angle)
            increaseX = Math.round(0.0 + (distancePerUpdate * Math.cos(radians))).toInt()
            increaseZ = Math.round(0.0 - (distancePerUpdate * Math.sin(radians))).toInt()
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

            var angle = 360 - value.toDouble() + 90
            if (angle > 360) angle -= 360
            val radians = Math.toRadians(angle)
            increaseX = Math.round(0.0 + (distancePerUpdate * Math.cos(radians))).toInt()
            increaseZ = Math.round(0.0 - (distancePerUpdate * Math.sin(radians))).toInt()
            field = value
            simpleSurfaceShip.heading = value
        }
    var currentlyFacing: Int = -1

    init {
        val allowedBlocks: Collection<Material> = Material.values().filter { it != Material.AIR && it != Material.WATER &&
                it != Material.STATIONARY_WATER && it != Material.LAVA && it != Material.STATIONARY_LAVA }
        val blocks = detectFloodFill(detectionPoint, allowedBlocks, false, 20000)
        val rotationPoint = blocks.first { it.state is Sign && (it.state as Sign).lines[0] == "[RotationPoint]" }.location

        simpleSurfaceShip = SimpleSurfaceShip(plugin, blocks, rotationPoint)
    }

    override var location: Location
        get() = simpleSurfaceShip.location
        set(value) {simpleSurfaceShip.location = value}
    override val onBoardEntities: Collection<Entity>
        get() = simpleSurfaceShip.onBoardEntities

    override fun rotate(rotation: Rotation) {
        simpleSurfaceShip.rotate(rotation)
    }

    override fun close() {
        simpleSurfaceShip.close()
    }

    fun update() {
        val newLoc = location.clone()
        newLoc.x += increaseX
        newLoc.z += increaseZ
        try {
            location = newLoc
        } catch(e: CouldNotMoveCraftException) {
            onBoardEntities.filter { it is Player }.forEach { it.sendMessage(ChatColor.RED.toString() + e.message) }
        }
    }
}