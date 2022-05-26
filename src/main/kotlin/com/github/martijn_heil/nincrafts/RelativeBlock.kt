package com.github.martijn_heil.nincrafts

import com.github.martijn_heil.nincrafts.util.minus
import com.github.martijn_heil.nincrafts.util.plus
import org.bukkit.Location

data class RelativeBlock(val x: Int, val y: Int, val z: Int) {
    fun resolve(from: Location) = from.world.getBlockAt(from.blockX + x, from.blockY + y, from.blockZ + z)

    fun resolveToLocation(reference: Location): Location {
        val relative = asRelativeLocation()
        relative.world = reference.world

        return relative + reference
    }

    fun asRelativeLocation() = Location(null, x.toDouble(), y.toDouble(), z.toDouble())

    companion object {
        fun fromAbsoluteLocation(reference: Location, from: Location): RelativeBlock {
            val result = (from - reference)
            return RelativeBlock(
                result.blockX,
                result.blockY,
                result.blockZ
            )
        }

        fun fromRelativeLocation(from: Location) = RelativeBlock(
            from.blockX,
            from.blockY,
            from.blockZ
        )
    }
}
