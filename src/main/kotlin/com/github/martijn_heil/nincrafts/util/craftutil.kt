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

import com.github.martijn_heil.nincrafts.RelativeBlock
import com.github.martijn_heil.nincrafts.Rotation
import com.github.martijn_heil.nincrafts.Rotation.CLOCKWISE
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Material.AIR
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.Entity
import org.bukkit.event.player.PlayerTeleportEvent
import java.util.*
import org.bukkit.util.Vector
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

fun getAdjactentBlocks(block: Block): Collection<Block> {
    val list = ArrayList<Block>()
    for (modX in -1..1) {
        for(modY in -1..1) {
            for(modZ in -1..1) {
                list.add(block.world.getBlockAt(block.x + modX, block.y + modY, block.z + modZ))
            }
        }
    }
    return list
}

fun getAdjacentLocations(origin: Location): Array<Location> {
    val locations = Array(27) { Location(null, 0.00, 0.00, 0.00) }
    var i = 0
    for (modX in -1..1) {
        for(modY in -1..1) {
            for(modZ in -1..1) {
                locations[i] = Location(origin.world, origin.x + modX, origin.y + modY, origin.z + modZ)
                i++
            }
        }
    }
    return locations
}

fun detectFloodFill(startLocation: Location, conditionalBlockList: HashSet<Material>, isDisallowedList: Boolean, maxSize: Int): ArrayList<Block> {
    val blocks = HashSet<Block>(maxSize)

    val s = Stack<Location>()
    s.push(startLocation)
    getAdjacentLocations(startLocation).forEach { s.push(it) }

    while(!s.isEmpty()) {
        if(blocks.size >= maxSize) throw Exception("Maximum detection size ($maxSize) exceeded.")

        val loc = s.pop()
        val block = loc.block

        when(isDisallowedList) {
            // List of disallowed blocks contains our type.
            true -> if (conditionalBlockList.contains(block.type)) continue

            // List of allowed blocks doesn't contain our type.
            false -> if (!conditionalBlockList.contains(block.type)) continue
        }
        if(!blocks.add(block)) continue

        getAdjacentLocations(loc).forEach { s.push(it) }
    }

    if(blocks.isEmpty()) throw Exception("Could not detectFloodFill any allowed blocks.")
    return ArrayList(blocks)
}

fun getRotatedLocation(rotationPoint: Location, rotation: Rotation, loc: Location): Location {
    val newRelativeX = if (rotation == CLOCKWISE) rotationPoint.z - loc.z else -(rotationPoint.z - loc.z)
    val newRelativeZ = if(rotation == CLOCKWISE) -(rotationPoint.x - loc.x) else rotationPoint.x - loc.x
    return Location(loc.world, rotationPoint.x + newRelativeX, loc.y, rotationPoint.z + newRelativeZ)
}

fun getRotatedRelativeBlock(rotation: Rotation, loc: RelativeBlock): RelativeBlock {
    val phonyRotationPoint = Location(null, 0.00, 0.00, 0.00)
    val rotated = getRotatedLocation(phonyRotationPoint, rotation, loc.asRelativeLocation())
    return RelativeBlock.fromRelativeLocation(rotated)
}

fun getRotatedLocation(output: Location, rotationPoint: Location, rotation: Rotation, loc: Location) {
    val newRelativeX = if (rotation == CLOCKWISE) rotationPoint.z - loc.z else -(rotationPoint.z - loc.z)
    val newRelativeZ = if(rotation == CLOCKWISE) -(rotationPoint.x - loc.x) else rotationPoint.x - loc.x
    output.x += newRelativeX
    output.y += loc.y
    output.z += newRelativeZ
}


fun detectAirBlocksBelowWaterLevel(world: World, box: BoundingBox, waterLevel: Int): ArrayList<Block> {
    val minX = box.minX.toInt()
    val maxX = box.maxX.toInt()
    val minY = box.minY.toInt()
    val minZ = box.minZ.toInt()
    val maxZ = box.maxZ.toInt()
    if(minY >= waterLevel) return ArrayList<Block>()

    val list = ArrayList<Block>()
    for(x in minX..maxX) {
        for(y in minY until waterLevel) {
            for(z in minZ..maxZ) {
                val block = world.getBlockAt(x, y, z)
                if(block.type == AIR) list.add(block)
            }
        }
    }
    return list
}

fun rotateEntities(entities: Collection<Entity>, rotationPoint: Location, rotation: Rotation) {
    entities.forEach {
        val loc = it.location
        val newLoc = getRotatedLocation(rotationPoint, rotation, loc)

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
}


operator fun Vector.plus(what: Vector) = this.clone().add(what)!! // v1 + v2
operator fun Vector.minus(what: Vector) = this.clone().subtract(what)!! // v1 - v2
operator fun Vector.times(what: Double) = this.clone().multiply(what)!! // v1 * a
operator fun Vector.div(what: Double) = this.clone().multiply(1 / what)!! // v1 / a

operator fun Location.plus(what: Location) = this.clone().add(what)!! // l1 + l2
operator fun Location.plus(what: Vector) = this.clone().add(what)!! // l + v
operator fun Location.minus(what: Location) = this.clone().subtract(what)!! // l1 - l2
operator fun Location.minus(what: Vector) = this.clone().subtract(what)!! // l - v
operator fun Location.times(what: Double) = this.clone().multiply(what)!! // l1 * a
operator fun Location.div(what: Double) = this.clone().multiply(1 / what)!! // l1 / a
