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
import org.bukkit.block.Block


class BoundingBox(var minY: Double, var maxY: Double, var minX: Double, var maxX: Double, var minZ: Double, var maxZ: Double) : Cloneable {

    fun contains(b: Block) = b.x >= minX && b.x <= maxX && b.y >= minY && b.y <= maxY && b.z >= minZ && b.z <= maxZ
    fun contains(loc: Location) = loc.x >= minX && loc.x <= maxX && loc.y >= minY && loc.y <= maxY && loc.z >= minZ && loc.z <= maxZ

    override fun clone() = BoundingBox(minY, maxY, minX, maxX, minZ, maxZ)
}