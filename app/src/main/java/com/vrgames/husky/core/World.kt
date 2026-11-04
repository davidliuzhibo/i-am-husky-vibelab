package com.vrgames.husky.core

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** A point on the diorama's floor (meters; x right, z toward the player). */
data class P(val x: Double, val z: Double) {
    operator fun plus(o: P) = P(x + o.x, z + o.z)
    operator fun minus(o: P) = P(x - o.x, z - o.z)
    operator fun times(s: Double) = P(x * s, z * s)
    fun len() = hypot(x, z)
    fun dist(o: P) = hypot(x - o.x, z - o.z)
    fun norm(): P = len().let { if (it < 1e-9) P(0.0, 0.0) else P(x / it, z / it) }
}

/** A box on the floor that the animals walk around ([cx], [cz] its middle, [hx], [hz] half sizes). */
data class Block(val cx: Double, val cz: Double, val hx: Double, val hz: Double) {
    fun has(p: P, pad: Double = 0.0) = abs(p.x - cx) <= hx + pad && abs(p.z - cz) <= hz + pad
}

/**
 * The walkable floor of one diorama: a grid of [CELL]-sized cells inside [w]×[d] (centered on the
 * origin), blocked by the furniture's footprints; paths are A* over the 8 neighbors, then shortened
 * by walking straight wherever the line is clear.
 */
class Floor(val w: Double, val d: Double, blocks: List<Block>) {
    val nx = (w / CELL).toInt()
    val nz = (d / CELL).toInt()
    private val open = BooleanArray(nx * nz)

    init {
        for (j in 0 until nz) for (i in 0 until nx) {
            val p = center(i, j)
            open[j * nx + i] = blocks.none { it.has(p, PAD) }
        }
    }

    fun center(i: Int, j: Int) = P(-w / 2 + (i + 0.5) * CELL, -d / 2 + (j + 0.5) * CELL)
    private fun cell(p: P) = ((p.x + w / 2) / CELL).toInt().coerceIn(0, nx - 1) to ((p.z + d / 2) / CELL).toInt().coerceIn(0, nz - 1)
    fun walkable(p: P): Boolean = cell(p).let { (i, j) -> open[j * nx + i] } && abs(p.x) < w / 2 && abs(p.z) < d / 2

    /** The walkable point nearest [p] (searching outward). */
    fun nearestOpen(p: P): P {
        if (walkable(p)) return p
        val (ci, cj) = cell(p)
        for (r in 1..max(nx, nz)) {
            var best: P? = null
            for (j in cj - r..cj + r) for (i in ci - r..ci + r) {
                if (i !in 0 until nx || j !in 0 until nz || !open[j * nx + i]) continue
                val c = center(i, j)
                if (best == null || c.dist(p) < best.dist(p)) best = c
            }
            if (best != null) return best
        }
        return p
    }

    /** True if the straight line [a]→[b] crosses only open cells. */
    fun clear(a: P, b: P): Boolean {
        val n = (a.dist(b) / (CELL * 0.5)).toInt() + 1
        for (k in 0..n) if (!walkable(a + (b - a) * (k.toDouble() / n))) return false
        return true
    }

    /** A path from [a] to [b] (both pulled onto open floor), or empty if none. */
    fun path(a0: P, b0: P): List<P> {
        val a = nearestOpen(a0)
        val b = nearestOpen(b0)
        if (clear(a, b)) return listOf(b)
        val (si, sj) = cell(a)
        val (gi, gj) = cell(b)
        val g = DoubleArray(nx * nz) { Double.MAX_VALUE }
        val from = IntArray(nx * nz) { -1 }
        val closed = BooleanArray(nx * nz)
        val q = java.util.PriorityQueue<Pair<Double, Int>>(compareBy { it.first })
        val s = sj * nx + si
        val goal = gj * nx + gi
        g[s] = 0.0
        q += 0.0 to s
        while (q.isNotEmpty()) {
            val (_, k) = q.poll()!!
            if (closed[k]) continue
            closed[k] = true
            if (k == goal) break
            val i = k % nx
            val j = k / nx
            for (dj in -1..1) for (di in -1..1) {
                if (di == 0 && dj == 0) continue
                val x = i + di
                val y = j + dj
                if (x !in 0 until nx || y !in 0 until nz) continue
                val n = y * nx + x
                if (!open[n] || closed[n]) continue
                // No cutting corners past a blocked cell.
                if (di != 0 && dj != 0 && (!open[j * nx + x] || !open[y * nx + i])) continue
                val c = g[k] + if (di != 0 && dj != 0) 1.4142 else 1.0
                if (c < g[n]) {
                    g[n] = c
                    from[n] = k
                    q += (c + hypot((x - gi).toDouble(), (y - gj).toDouble())) to n
                }
            }
        }
        if (from[goal] < 0 && goal != s) return emptyList()
        val cells = ArrayList<P>()
        var k = goal
        while (k != s && k >= 0) {
            cells += center(k % nx, k / nx)
            k = from[k]
        }
        cells.reverse()
        if (cells.isNotEmpty()) cells[cells.size - 1] = b
        // Shorten: from each point, jump to the farthest one in plain line.
        val out = ArrayList<P>()
        var cur = a
        var idx = 0
        while (idx < cells.size) {
            var far = idx
            for (t in cells.size - 1 downTo idx) if (clear(cur, cells[t])) { far = t; break }
            out += cells[far]
            cur = cells[far]
            idx = far + 1
        }
        return out
    }

    companion object {
        const val CELL = 0.025
        /** How far the animals keep from furniture (their half width). */
        const val PAD = 0.03
    }
}

/** A field of view on the floor: from [at], facing [heading] (radians, 0 = -z), [half] angle, [range]. */
fun sees(at: P, heading: Double, half: Double, range: Double, target: P, walls: List<Block>): Boolean {
    val d = target - at
    if (d.len() > range) return false
    val a = atan2(d.x, -d.z)
    var diff = a - heading
    while (diff > Math.PI) diff -= 2 * Math.PI
    while (diff < -Math.PI) diff += 2 * Math.PI
    if (abs(diff) > half) return false
    // Tall furniture blocks the view.
    val n = (d.len() / 0.02).toInt() + 1
    for (k in 1 until n) {
        val p = at + d * (k.toDouble() / n)
        if (walls.any { it.has(p) }) return false
    }
    return true
}

/** Turns [from] toward [to] (radians) by at most [step]. */
fun turnToward(from: Double, to: Double, step: Double): Double {
    var d = to - from
    while (d > Math.PI) d -= 2 * Math.PI
    while (d < -Math.PI) d += 2 * Math.PI
    return from + d.coerceIn(-step, step)
}

/** How far [from] is off [to], the short way round (radians). */
fun angleBetween(from: Double, to: Double): Double {
    var d = to - from
    while (d > Math.PI) d -= 2 * Math.PI
    while (d < -Math.PI) d += 2 * Math.PI
    return d
}

fun headingOf(d: P) = atan2(d.x, -d.z)
fun dirOf(h: Double) = P(sin(h), -cos(h))

/** A little deterministic random (the owner's moods), the same on every run of a level. */
class Dice(seed: Long) {
    private var s = seed xor 0x5DEECE66DL
    fun next(): Double {
        s = (s * 0x5DEECE66DL + 0xBL) and ((1L shl 48) - 1)
        return (s ushr 17).toDouble() / (1L shl 31)
    }
    fun range(a: Double, b: Double) = a + (b - a) * next()
}
