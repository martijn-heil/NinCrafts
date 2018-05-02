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

package com.github.martijn_heil.nincrafts.vessel.motor

import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin
import com.github.martijn_heil.nincrafts.Rotation
import com.github.martijn_heil.nincrafts.vessel.MotorShip
import com.github.martijn_heil.nincrafts.vessel.Ship


class Battleship(plugin: Plugin, detectionPoint: Location) : Ship {
    private val updateInterval = 40L
    private var motorShip: MotorShip
    init {
        motorShip = MotorShip(plugin, detectionPoint, updateInterval)
        plugin.server.scheduler.scheduleSyncRepeatingTask(plugin, { update() }, 0, updateInterval)
    }

    private fun update() {
        motorShip.update()
    }

    override fun rotate(rotation: Rotation) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override var heading: Int
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
        set(value) {}
    override var location: Location
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
        set(value) {}
    override val onBoardEntities: Collection<Entity>
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

}