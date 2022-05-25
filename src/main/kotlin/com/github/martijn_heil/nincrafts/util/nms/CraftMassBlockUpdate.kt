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

package com.github.martijn_heil.nincrafts.util.nms

import com.github.martijn_heil.nincrafts.util.MassBlockUpdate
import net.minecraft.core.BlockPos
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.BlockFace
import org.bukkit.block.BlockState
import org.bukkit.block.Sign
import org.bukkit.block.data.BlockData
import org.bukkit.block.data.Powerable
import org.bukkit.craftbukkit.v1_18_R2.CraftWorld
import org.bukkit.craftbukkit.v1_18_R2.block.data.CraftBlockData
import org.bukkit.material.Button
import org.bukkit.material.Redstone
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min


class CraftMassBlockUpdate(private val plugin: Plugin, private val world: World) : MassBlockUpdate {
    override var relightingStrategy: MassBlockUpdate.RelightingStrategy = MassBlockUpdate.RelightingStrategy.IMMEDIATE
    private var deferredBlocks: Queue<DeferredBlock> = ArrayDeque()
    private var relightTask: BukkitTask? = null
    private var maxRelightTimePerTick = TimeUnit.NANOSECONDS.convert(1, TimeUnit.MILLISECONDS)

    private var minX = Integer.MAX_VALUE
    private var minZ = Integer.MAX_VALUE
    private var maxX = Integer.MIN_VALUE
    private var maxZ = Integer.MIN_VALUE
    private var blocksModified = 0

    override fun setBlockState(x: Int, y: Int, z: Int, state: BlockState): Boolean {
        val stateData = state.blockData
        if(stateData is Powerable) {
            stateData.isPowered = false
            state.blockData = stateData
        }

        val res = setBlock(x, y, z, state.blockData)
        if(!res) return false
        val newBlock = world.getBlockAt(x, y, z)

        if(state is Sign) {
            val toSign = newBlock.state as Sign
            for (i in state.lines.indices) {
                toSign.setLine(i, state.getLine(i))
            }
            toSign.update(true, false)
        }

//        if(state is Chest) {
//            val newChest = newBlock.state as Chest
//            newChest.inventory.contents = state.blockInventory.contents
//            newChest.update(true, false)
//        }

        return true
    }

    override fun setBlock(x: Int, y: Int, z: Int, material: Material) = setBlock(x, y, z, material.createBlockData())

    override fun setBlock(x: Int, y: Int, z: Int, data: BlockData): Boolean {
        minX = min(minX, x)
        minZ = min(minZ, z)
        maxX = max(maxX, x)
        maxZ = max(maxZ, z)

        blocksModified++
        val res = setBlockFast(world, x, y, z, data)

        if (relightingStrategy != MassBlockUpdate.RelightingStrategy.NEVER) {
            if (getBlockLightBlocking(data) != getBlockLightBlocking(data) ||
                getBlockLightEmission(data) != getBlockLightEmission(data)) {
                // lighting or light blocking by this block has changed; force a recalculation
                if (relightingStrategy == MassBlockUpdate.RelightingStrategy.IMMEDIATE) {
                    recalculateBlockLighting(world, x, y, z)
                } else if (relightingStrategy == MassBlockUpdate.RelightingStrategy.DEFERRED || relightingStrategy == MassBlockUpdate.RelightingStrategy.HYBRID) {
                    deferredBlocks.add(DeferredBlock(x, y, z))
                }
            }
        }
        return res
    }

    override fun notifyClients() {
        if (relightingStrategy == MassBlockUpdate.RelightingStrategy.DEFERRED || relightingStrategy == MassBlockUpdate.RelightingStrategy.HYBRID) {
            relightTask = Bukkit.getScheduler().runTaskTimer(plugin, CraftMassBlockUpdateRunnable(), 1L, 1L)
        }
        if (relightingStrategy != MassBlockUpdate.RelightingStrategy.DEFERRED) {
            for (cc in calculateChunks()) {
                world.refreshChunk(cc.x, cc.z)
            }
        }
    }

    override fun setMaxRelightTimePerTick(value: Long, timeUnit: TimeUnit) {
        maxRelightTimePerTick = timeUnit.toNanos(value)
    }

    override val blocksToRelight: Int
        get() = deferredBlocks.size

    fun setDeferredBufferSize(size: Int) {
        if (!deferredBlocks.isEmpty()) {
            // resizing an existing buffer is not supported
            throw IllegalStateException("setDeferredBufferSize() called after block updates made")
        }
        if (relightingStrategy !== MassBlockUpdate.RelightingStrategy.DEFERRED && relightingStrategy !== MassBlockUpdate.RelightingStrategy.HYBRID) {
            // reduce accidental memory wastage if called when not needed
            throw IllegalStateException("setDeferredBufferSize() called when relighting strategy not DEFERRED or HYBRID")
        }
        deferredBlocks = ArrayDeque<DeferredBlock>(size)
    }

    private fun canAffectLighting(world: World, x: Int, y: Int, z: Int): Boolean {
        val base = world.getBlockAt(x, y, z)
        val east = base.getRelative(BlockFace.EAST)
        val west = base.getRelative(BlockFace.WEST)
        val up = base.getRelative(BlockFace.UP)
        val down = base.getRelative(BlockFace.DOWN)
        val south = base.getRelative(BlockFace.SOUTH)
        val north = base.getRelative(BlockFace.NORTH)

        return east.type.isTransparent ||
                west.type.isTransparent ||
                up.type.isTransparent ||
                down.type.isTransparent ||
                south.type.isTransparent ||
                north.type.isTransparent
    }

    private fun calculateChunks(): Set<ChunkCoords> {
        val res = HashSet<ChunkCoords>()
        if (blocksModified == 0) {
            return res
        }
        val x1 = minX shr 4
        val x2 = maxX shr 4
        val z1 = minZ shr 4
        val z2 = maxZ shr 4
        for (x in x1..x2) {
            for (z in z1..z2) {
                res.add(ChunkCoords(x, z))
            }
        }
        return res
    }

    private inner class CraftMassBlockUpdateRunnable : Runnable {
        override fun run() {
            val now = System.nanoTime()
            var n = 1

            while (deferredBlocks.peek() != null) {
                val db = deferredBlocks.poll()
                // Don't consider blocks that are completely surrounded by other non-transparent blocks
                if (canAffectLighting(world, db.x, db.y, db.z)) {
                    recalculateBlockLighting(world, db.x, db.y, db.z)
                    if (n++ % MAX_BLOCKS_PER_TIME_CHECK == 0) {
                        if (System.nanoTime() - now > maxRelightTimePerTick) {
                            break
                        }
                    }
                }
            }

            if (deferredBlocks.isEmpty()) {
                relightTask!!.cancel()
                relightTask = null
                val touched = calculateChunks()
                for (cc in touched) {
                    world.refreshChunk(cc.x, cc.z)
                }
            }
        }
    }

    private inner class ChunkCoords(val x: Int, val z: Int) {

        override fun equals(o: Any?): Boolean {
            if (this === o) return true
            if (o == null || javaClass != o.javaClass) return false

            val that = o as ChunkCoords?

            if (x != that!!.x) return false
            if (z != that.z) return false

            return true
        }

        override fun hashCode(): Int {
            var result = x
            result = 31 * result + z
            return result
        }
    }

    private data class DeferredBlock(val x: Int, val y: Int, val z: Int)

    companion object {
        private val MAX_BLOCKS_PER_TIME_CHECK = 1000
    }
}

private fun setBlockFast(world: World, x: Int, y: Int, z: Int, data: BlockData): Boolean {
    val w = (world as CraftWorld).handle
    val bp = BlockPos(x, y, z)
    val bs = (data as CraftBlockData).state

    var flags = 0
    flags = flags or 1      // Kinda unclear. Prevents blockUpdated() from being called.
    flags = flags or 16     // Don't update neighbour shapes. See net.minecraft.world.level.Level:629
    flags = flags or 128    // Don't recalculate light. See net.minecraft.world.level.Level:550

    return w.setBlock(bp, bs, flags)
}

private fun getBlockLightBlocking(data: BlockData): Int {
    throw UnsupportedOperationException()
}

private fun recalculateBlockLighting(world: World, x: Int, y: Int, z: Int) {
    throw UnsupportedOperationException()
}

private fun getBlockLightEmission(data: BlockData): Int {
    throw UnsupportedOperationException()
}

// TODO: Fix commented out code
//private fun getBlockLightBlocking(blockId: Int): Int {
//    return Block.getById(blockId).()
//}
//
//private fun recalculateBlockLighting(world: World, x: Int, y: Int, z: Int) {
//    // Don't consider blocks that are completely surrounded by other non-transparent blocks
//    if (!canAffectLighting(world, x, y, z)) return
//
//    val i = x and 0x0F
//    val j = y and 0xFF
//    val k = z and 0x0F
//    val blockPos = BlockPosition(i, j, k)
//    val craftChunk = world.getChunkAt(x shr 4, z shr 4) as CraftChunk
//    val nmsChunk = craftChunk.handle
//
//    val i1 = k shl 4 or i
//    val maxY = nmsChunk.heightMap[i1]
//
//    val block: Block = nmsChunk.a(i, j, k).block //
//    val j2: Int = block
//
//    if (j2 > 0) {
//        if (j >= maxY) {
//            chunkRelightBlock(nmsChunk, i, j + 1, k)
//        }
//    } else if (j == maxY - 1) {
//        chunkRelightBlock(nmsChunk, i, j, k)
//    }
//
//    if (nmsChunk.getBrightness(EnumSkyBlock.SKY, blockPos) > 0 || nmsChunk.getBrightness(EnumSkyBlock.BLOCK, blockPos) > 0) {
//        chunkPropagateSkylightOcclusion(nmsChunk, i, k)
//    }
//
//    val w = (world as CraftWorld).handle
//    w.c(EnumSkyBlock.BLOCK, blockPos) // World#checkLightFor()
//}
//
//// private void relightBlock(int x, int y, int z) (1.11.2, mc-dev)
//// private void relightBlock(int x, int y, int z) (1.11.2, MCP)
//private var chunkRelightBlock: Method? = null
//private fun chunkRelightBlock(nmsChunk: Chunk, i: Int, j: Int, k: Int) {
//    try {
//        if (chunkRelightBlock == null) {
//            val classes = arrayOf(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
//            chunkRelightBlock = Chunk::class.java.getDeclaredMethod("relightBlock", *classes)
//            chunkRelightBlock!!.isAccessible = true
//        }
//        chunkRelightBlock!!.invoke(nmsChunk, i, j, k)
//    } catch (e: Exception) {
//        e.printStackTrace()
//    }
//
//}
//
//// private void propagateSkylightOcclusion(int x, int z) (1.11.2, MCP)
//private var chunkPropagateSkylightOcclusion: Method? = null
//private fun chunkPropagateSkylightOcclusion(nmsChunk: Chunk, i: Int, j: Int) {
//    try {
//        if (chunkPropagateSkylightOcclusion == null) {
//            val classes = arrayOf(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
//            chunkPropagateSkylightOcclusion = Chunk::class.java.getDeclaredMethod("TODO", *classes)
//            chunkPropagateSkylightOcclusion!!.isAccessible = true
//        }
//        chunkPropagateSkylightOcclusion!!.invoke(nmsChunk, i, j)
//    } catch (e: Exception) {
//        e.printStackTrace()
//    }
//}

private fun canAffectLighting(world: World, x: Int, y: Int, z: Int): Boolean {
    val base = world.getBlockAt(x, y, z)
    val east = base.getRelative(BlockFace.EAST)
    val west = base.getRelative(BlockFace.WEST)
    val up = base.getRelative(BlockFace.UP)
    val down = base.getRelative(BlockFace.DOWN)
    val south = base.getRelative(BlockFace.SOUTH)
    val north = base.getRelative(BlockFace.NORTH)

    return east.type.isTransparent ||
            west.type.isTransparent ||
            up.type.isTransparent ||
            down.type.isTransparent ||
            south.type.isTransparent ||
            north.type.isTransparent
}
