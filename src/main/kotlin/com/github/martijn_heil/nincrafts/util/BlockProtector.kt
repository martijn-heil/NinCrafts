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

package com.github.martijn_heil.nincrafts.util

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority.HIGHEST
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPhysicsEvent
import org.bukkit.event.entity.EntityBreakDoorEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.material.Door
import org.bukkit.plugin.Plugin
import com.github.martijn_heil.nincrafts.Rotation
import java.io.Closeable
import java.util.*


class BlockProtector(private val plugin: Plugin) : Closeable {
    var protectedBlocks: MutableCollection<Location> = ArrayList()

    private val protectedBlocksListener = object : Listener {
        @EventHandler(ignoreCancelled = true, priority = HIGHEST)
        fun onEntityBreakDoor(e: EntityBreakDoorEvent) {
            val b = e.block
            val state = e.block.state
            val data = state.data as? Door ?: return
            val topHalf = (if(data.isTopHalf) b else b.world.getBlockAt(b.x, b.y + 1, b.z))
            val bottomHalf = (if(!data.isTopHalf) b else b.world.getBlockAt(b.x, b.y - 1, b.z))

            protectedBlocks.forEach {
                val itBlock = it.block
                e.isCancelled = itBlock == topHalf || itBlock == bottomHalf
            }
        }

        @EventHandler(ignoreCancelled = true, priority = HIGHEST)
        fun onEntityExplode(e: EntityExplodeEvent) {
            protectedBlocks.forEach { e.blockList().remove(it.block) }
        }

        @EventHandler(ignoreCancelled = true, priority = HIGHEST)
        fun onBlockBreak(e: BlockBreakEvent) {
            protectedBlocks.forEach { if(e.block == it.block) e.isCancelled = true }
        }

        @EventHandler(ignoreCancelled = true, priority = HIGHEST)
        fun onBlockPhysics(e: BlockPhysicsEvent) {
            protectedBlocks.forEach { if(e.block == it.block) e.isCancelled = true }
        }
    }

    init {
        plugin.server.pluginManager.registerEvents(protectedBlocksListener, plugin)
    }

    fun updateAllLocations(world: World, relativeX: Int, relativeY: Int, relativeZ: Int) {
        protectedBlocks.forEach {
            it.world = world
            it.x += relativeX
            it.y += relativeY
            it.z += relativeZ
        }
    }

    fun updateAllLocationsRotated(rotation: Rotation, rotationPoint: Location) {
        protectedBlocks.forEach {
            val newLoc = getRotatedLocation(rotationPoint, rotation, rotationPoint)
            it.x = newLoc.x
            it.y = newLoc.y
            it.z = newLoc.z
        }
    }

    override fun close() {
        HandlerList.unregisterAll(protectedBlocksListener)
    }
}