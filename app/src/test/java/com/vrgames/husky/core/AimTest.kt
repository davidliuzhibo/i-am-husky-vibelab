package com.vrgames.husky.core

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

/**
 * How often a shaky real pinch means what the player aimed at (device report v0.2.2: "点不中，然后乱走").
 * Rays from the seat (sitting and standing eyes, both hands held low, a raised hand) aim at every thing that can be
 * dealt with, not just at its middle but anywhere within [JITTER] of it, the way a hand's ray wobbles a metre away.
 * The first box a ray enters decides, as on the device (the SDK only reports the first entity): the thing itself or
 * its name tag; the furniture it stands on (tapFurniture picks it); the floor near it (aimedAt pulls it in); the dog
 * standing beside it (pinchDog does what is at its feet). Anything else is a miss: the dog walks off somewhere.
 * The dog's own box is in the way too: it stands at the spot of a neighbouring thing (where the last job left it).
 */
class AimTest {
    private class Box(val c: DoubleArray, val h: DoubleArray, val what: String)

    private fun enters(o: DoubleArray, d: DoubleArray, b: Box): Double? {
        var t0 = 0.0
        var t1 = Double.MAX_VALUE
        for (k in 0..2) {
            if (abs(d[k]) < 1e-9) { if (abs(o[k] - b.c[k]) > b.h[k]) return null; continue }
            val a = (b.c[k] - b.h[k] - o[k]) / d[k]
            val e = (b.c[k] + b.h[k] - o[k]) / d[k]
            t0 = maxOf(t0, minOf(a, e)); t1 = minOf(t1, maxOf(a, e))
            if (t0 > t1) return null
        }
        return t0
    }

    private fun boxes(l: Level): List<Box> =
        l.props.values.mapNotNull { p -> Looks.hitBox(p)?.let { b -> Box(doubleArrayOf(p.pos.x, p.lift + b.cy, p.pos.z), doubleArrayOf(b.w / 2, b.h / 2, b.d / 2), p.id) } } +
            l.props.values.filter { Looks.pinchTag(it) }.map { p -> Box(doubleArrayOf(p.pos.x, p.lift + Looks.tagY(p), p.pos.z), doubleArrayOf(Looks.tagWide(p) / 2, 0.025, 0.01), p.id) }

    private fun dogBox(at: P) = Box(doubleArrayOf(at.x, Looks.DOG_H / 2, at.z), doubleArrayOf(Looks.DOG_W / 2, Looks.DOG_H / 2, Looks.DOG_W / 2), "dog")

    /** Whether the game reads a pinch on the floor at [at] (seen from [eye], or none: a pinch on the dog) as [p]. */
    private fun near(l: Level, p: Prop, at: P, eye: Vec3?) = l.aimedAt(at, eye) === p

    /** Whether one ray from [o] toward [aim] ends up meaning [p]. */
    private fun means(l: Level, p: Prop, o: DoubleArray, aim: DoubleArray, all: List<Box>, dog: P?): Boolean {
        val d = DoubleArray(3) { aim[it] - o[it] }
        val list = if (dog != null) all + dogBox(dog) else all
        val first = list.mapNotNull { b -> enters(o, d, b)?.let { b to it } }.minByOrNull { it.second }?.first
        if (first == null) {
            // The floor: where the ray meets it.
            if (d[1] >= 0) return false
            val t = -o[1] / d[1]
            return near(l, p, P(o[0] + d[0] * t, o[2] + d[2] * t), if (o[1] > 1.0) STAND_EYE else SIT_EYE)
        }
        if (first.what == p.id) return true
        if (first.what == "dog") return dog != null && near(l, p, dog, null)
        val q = l.props[first.what]!!
        // A place to put things down is aimed at while carrying: what lies right beside it means it (dropBeside).
        if (p.kind == Kind.DROP && q.kind != Kind.SOLID && l.dropBeside(q) === p) return true
        // Any flag means the next flag (anyFlagMeansTheNextFlag).
        if (p.kind == Kind.GATE && q.kind == Kind.GATE) return true
        return q.kind == Kind.SOLID && q.block?.has(p.pos, Level.ON_FURNITURE) == true
    }

    /** Share of the shaky rays at [p] that mean it, the dog standing at [dog] (or away). */
    private fun score(l: Level, p: Prop, dog: P?, done: Prop?): Double {
        // The thing the dog was last sent to is done: why it stands there.
        done?.done = true
        try { return scoreNow(l, p, dog) } finally { done?.done = false }
    }

    private fun scoreNow(l: Level, p: Prop, dog: P?): Double {
        val all = boxes(l)
        val b = Looks.hitBox(p)!!
        val c = doubleArrayOf(p.pos.x, p.lift + b.cy, p.pos.z)
        var ok = 0; var n = 0
        for (o in ORIGINS) for (i in -2..2) for (j in -2..2) {
            val aim = doubleArrayOf(c[0] + i * JITTER / 2, c[1] + j * JITTER / 2, c[2])
            n++
            if (means(l, p, o, aim, all, dog)) ok++
        }
        return ok.toDouble() / n
    }

    private fun targets(l: Level) = l.props.values.filter { it.kind != Kind.SOLID && Looks.hitBox(it) != null && it.lift <= 0.2 }

    /** The dog's places: away, or at the spot of any other thing within 0.35 m (that thing then done: why it is there). */
    private fun dogSpots(l: Level, p: Prop): List<Pair<P?, Prop?>> = listOf<Pair<P?, Prop?>>(null to null) +
        l.props.values.filter { it !== p && it.spot != null && it.spot!!.dist(p.pos) < 0.35 }.map { it.spot to it }

    fun report(): List<Triple<String, Double, Double>> = Levels.IDS.flatMap { id ->
        val l = Levels.make(id)
        // Only what a pinch right on it means at the start (the flags wait for the sled: measured once it is pulled).
        targets(l).mapNotNull { p ->
            // A place to put things down is aimed at with something in the mouth (the remote, for the sofa gap).
            l.husky.carrying = if (p.kind == Kind.DROP) l.props.values.firstOrNull { it.kind == Kind.CARRY } else null
            try {
                if (l.aimedAt(p.pos) !== p) return@mapNotNull null
                val scores = dogSpots(l, p).map { (at, done) -> score(l, p, at, done) }
                Triple("$id/${p.id}", scores.first(), scores.minOrNull()!!)
            } finally { l.husky.carrying = null }
        }
    }

    @Test
    fun shakyPinchesMeanWhatTheyAimAt() {
        val r = report()
        println(r.joinToString("\n") { (k, away, worst) -> "%-20s dog away %3.0f%%   dog beside worst %3.0f%%".format(k, away * 100, worst * 100) })
        val bad = r.filter { it.second < AWAY_MIN || it.third < BESIDE_MIN }
        assertTrue(bad.joinToString("\n") { "${it.first}: ${(it.second * 100).toInt()}% / ${(it.third * 100).toInt()}%" }, bad.isEmpty())
    }

    companion object {
        /** How far a pinch lands from the aim (each way), a metre from the hand. */
        const val JITTER = 0.06
        const val AWAY_MIN = 0.85
        const val BESIDE_MIN = 0.65
        /** Where the eyes are when sitting and standing (the stage passes the tracked eye to the floor's pinch). */
        val SIT_EYE = Vec3(0.0, 0.75, 1.15)
        val STAND_EYE = Vec3(0.0, 1.2, 1.15)
        /** Diorama frame: the player 1.15 m toward +z of the middle, the floor 0.45 m below the eyes when sitting. */
        val ORIGINS = listOf(
            doubleArrayOf(0.0, 0.75, 1.15), doubleArrayOf(0.0, 1.2, 1.15),
            doubleArrayOf(-0.25, 0.3, 0.8), doubleArrayOf(0.25, 0.3, 0.8), doubleArrayOf(0.25, 0.6, 0.7),
        )
    }
}
