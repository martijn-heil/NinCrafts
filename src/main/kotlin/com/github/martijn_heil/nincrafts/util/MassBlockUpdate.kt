/*
 *     wac-core
 *     Copyright (C) 2016 Martijn Heil
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.martijn_heil.nincrafts.util

import org.bukkit.Material
import org.bukkit.block.BlockState
import java.util.concurrent.TimeUnit

interface MassBlockUpdate {
    enum class RelightingStrategy {
        /**
         * Do not do any relighting calculations at all.  If any block
         * lighting properties (i.chunkPropagateSkylightOcclusion. light emission or light blocking)
         * change, this may result in incorrect lighting of the changed
         * blocks.  This strategy should be used if you are certain
         * that no lighting properties are being changed, or if your
         * plugin will handle relighting itself.
         */
        NEVER,

        /**
         * Immediately relight any blocks whose lighting properties
         * have changed.  For very large changes (on the order of tens
         * of thousands or more), this may result in some server lag.
         */
        IMMEDIATE,

        /**
         * Carry out relighting over the next several ticks, to
         * minimise the risk of server lag.  Note that this carries
         * a non-trivial server-side memory cost, as updated block
         * locations need to be temporarily stored pending lighting
         * updates.
         */
        DEFERRED,

        /**
         * Immediately notify the client which blocks have changed.
         * Recalculate relighting in the background like DEFERRED mode.
         */
        HYBRID
    }

    fun setBlock(x: Int, y: Int, z: Int, material: Material): Boolean

    /**
     * Make a fast block change at the given coordinates.  Clients will
     * not see this change until [.notifyClients] is called.

     * @param x X-coordinate of the block
     * *
     * @param y Y-coordinate of the block
     * *
     * @param z Z-coordinate of the block
     * *
     * @param materialId the new material ID for the block
     * *
     * @return whether the block was actually changed
     */
    fun setBlock(x: Int, y: Int, z: Int, materialId: Int): Boolean

    /**
     * Make a fast block change at the given coordinates.  Clients will
     * not see this change until [.notifyClients] is called.

     * @param x X-coordinate of the block
     * *
     * @param y Y-coordinate of the block
     * *
     * @param z Z-coordinate of the block
     * *
     * @param materialId the new material ID for the block
     * *
     * @param data the new block data
     * *
     * @return whether the block was actually changed
     */
    fun setBlock(x: Int, y: Int, z: Int, materialId: Int, data: Int): Boolean

    fun setBlockState(x: Int, y: Int, z:Int, state: BlockState): Boolean

    /**
     * Recalculate lighting on all chunks affected by this mass block
     * update, and resend any altered chunks to all players within
     * viewing distance of the change.
     */
    fun notifyClients()

    /**
     * Set the block relighting strategy for this mass block update.
     * The default strategy is RelightingStrategy.IMMEDIATE.

     * @param strategy the desired re-lighting strategy
     */
    var relightingStrategy: RelightingStrategy

    /**
     * For relighting with RelightingStrategy.DEFERRED, specify the
     * maximum time the plugin should spend carrying out re-lighting
     * per server tick.  The default is 1ms (a server tick is 50ms).
     * This value is ignored for other relighting strategies.

     * @param value the value in units of the given time unit
     * *
     * @param timeUnit the time unit
     */
    fun setMaxRelightTimePerTick(value: Long, timeUnit: TimeUnit)

    /**
     * For relighting with RelightingStrategy.DEFERRED, get the number
     * of blocks that still need relighting.

     * @return the number of blocks the need re-lighting
     */
    val blocksToRelight: Int
}