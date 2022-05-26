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
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.block.Sign
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.EventPriority.HIGHEST
import org.bukkit.event.EventPriority.MONITOR
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPhysicsEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.player.PlayerInteractEvent
import com.github.martijn_heil.nincrafts.Rotation
import com.github.martijn_heil.nincrafts.util.getRotatedLocation
import org.bukkit.block.data.type.WallSign
import org.bukkit.material.MaterialData

/*
    [Rudder]
       0
 */
class SimpleRudder(private var sign: Sign) : AutoCloseable {
    private var world = sign.world

    private val listener = object : Listener {
        @EventHandler(ignoreCancelled = true, priority = HIGHEST)
        fun onEntityExplode(e: EntityExplodeEvent) {
            e.blockList().remove(sign.block)
            e.blockList().remove(sign.block.getRelative((sign.data as org.bukkit.material.Sign).attachedFace))
        }

        @EventHandler(ignoreCancelled = true, priority = HIGHEST)
        fun onBlockPhysics(e: BlockPhysicsEvent) {
            if(e.block == sign.block) e.isCancelled = true
        }

        @EventHandler(ignoreCancelled = true, priority = MONITOR)
        fun onPlayerInteract(e: PlayerInteractEvent) {
            try {
                if (e.clickedBlock == sign.block) {
                    if (e.action == Action.LEFT_CLICK_BLOCK) {
                        var newCourse = course - 10
                        if (newCourse < 0) newCourse += 360
                        course = newCourse
                    } else if (e.action == Action.RIGHT_CLICK_BLOCK) {
                        var newCourse = course + 10
                        if (newCourse >= 360) newCourse = 0 + (newCourse - 360)
                        course = newCourse
                    }
                }
            } catch(t: Throwable) {
                t.printStackTrace()
            }
        }

        @EventHandler(ignoreCancelled = true)
        fun onBlockBreak(e: BlockBreakEvent) {
            val signData = (sign.blockData as? WallSign) ?: return

            if (e.block == sign.block || e.block == sign.block.getRelative(signData.facing.oppositeFace)) {
                e.isCancelled = true
            }
        }
    }

    var onChangeCourseCallback: (heading: Int) -> Boolean = { true }

    var course: Int = 0
        set(value) {
            val previous = field
            field = value
            if(!onChangeCourseCallback(value)) { field = previous; return }
            sign.setLine(1, course.toString())
            sign.update(true, false)
        }


    init {
        Bukkit.getPluginManager().registerEvents(listener, NinCrafts.instance)
        if(sign.lines.size >= 2) {
            try {
                course = sign.lines[1].toInt()
            } catch(e: NumberFormatException) {
                sign.setLine(1, course.toString())
                sign.update(true, false)
            }
        } else {
            sign.setLine(1, course.toString())
            sign.update(true, false)
        }
    }

    fun updateLocation(relativeX: Int, relativeY: Int, relativeZ: Int) {
        val newLoc = Location(world, (sign.block.x + relativeX).toDouble(), (sign.block.y + relativeY).toDouble(), (sign.block.z + relativeZ).toDouble())
        sign = newLoc.block.state as Sign
    }

    fun updateLocationRotated(rotationPoint: Location, rotation: Rotation) {
        sign = world.getBlockAt(getRotatedLocation(rotationPoint, rotation, sign.location)).state as Sign
    }

    override fun close() {
        HandlerList.unregisterAll(listener)
    }
}
