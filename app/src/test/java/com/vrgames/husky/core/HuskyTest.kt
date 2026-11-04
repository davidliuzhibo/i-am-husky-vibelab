package com.vrgames.husky.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every level can be won with three stars by careful play, and the rules bite when they should. */
class HuskyTest {
    private fun play(id: String, log: Boolean = false): Level {
        val l = Levels.make(id)
        val s = Solver(l)
        var t = 0.0
        while (!l.over && t < l.limit + 5) {
            s.update(Level.STEP)
            for (e in l.step(Level.STEP)) if (log && e !is Ev.Sfx) println("${"%.1f".format(l.time)} $e dog=${l.husky.at} hid=${l.husky.hiddenIn?.id} owner=${l.owner.at}")
            t += Level.STEP
        }
        println("$id: won=${l.won} time=${"%.1f".format(l.time)} strikes=${l.strikes} stars=${l.stars()} goals=${l.goals.map { "${it.id}:${it.met}" }}")
        return l
    }

    @Test
    fun everyLevelCanBeWonWithThreeStars() {
        val bad = ArrayList<String>()
        for (id in Levels.IDS) {
            val l = play(id, System.getProperty("key") == id)
            if (!l.won || l.stars() < 3) bad += "$id won=${l.won} stars=${l.stars()} strikes=${l.strikes} unmet=${l.goals.filter { !it.met }.map { it.id }}"
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    /** How far along the ray from [o] (direction [d]) it enters the box around [c], or null if it misses. */
    private fun enters(o: DoubleArray, d: DoubleArray, c: DoubleArray, half: DoubleArray): Double? {
        var t0 = 0.0
        var t1 = Double.MAX_VALUE
        for (k in 0..2) {
            if (kotlin.math.abs(d[k]) < 1e-9) {
                if (kotlin.math.abs(o[k] - c[k]) > half[k]) return null
                continue
            }
            val a = (c[k] - half[k] - o[k]) / d[k]
            val b = (c[k] + half[k] - o[k]) / d[k]
            t0 = maxOf(t0, minOf(a, b))
            t1 = minOf(t1, maxOf(a, b))
            if (t0 > t1) return null
        }
        return t0
    }

    /**
     * What the player's ray really reaches, from the seat (the AutoPilot taps by name and so never
     * finds out): aimed at a thing's middle from the eyes, sitting or standing, its own box must be
     * the first it enters; from either hand, held low and to the side, at least part of it must be.
     */
    @Test
    fun everyThingCanBePinchedFromTheSeat() {
        // Diorama frame: the player is 1.15 m toward +z of its middle, the floor 0.45 m up.
        val eyes = listOf(doubleArrayOf(0.0, 0.75, 1.15), doubleArrayOf(0.0, 1.2, 1.15))
        val hands = listOf(doubleArrayOf(-0.25, 0.3, 0.8), doubleArrayOf(0.25, 0.3, 0.8), doubleArrayOf(0.25, 0.6, 0.7))
        val bad = ArrayList<String>()
        for (id in Levels.IDS) {
            val l = Levels.make(id)
            val bodies = l.props.values.mapNotNull { p -> Looks.hitBox(p)?.let { b -> p to Pair(doubleArrayOf(p.pos.x, p.lift + b.cy, p.pos.z), doubleArrayOf(b.w / 2, b.h / 2, b.d / 2)) } }
            val tags = l.props.values.filter { Looks.pinchTag(it) }.map { p -> p to Pair(doubleArrayOf(p.pos.x, p.lift + Looks.tagY(p), p.pos.z), doubleArrayOf(Looks.tagWide(p) / 2, 0.025, 0.01)) }
            val boxes = bodies + tags
            for ((p, box) in bodies) {
                if (p.kind == Kind.SOLID) continue
                fun reaches(o: DoubleArray, aim: DoubleArray): Boolean {
                    val d = DoubleArray(3) { aim[it] - o[it] }
                    return boxes.mapNotNull { (q, b) -> enters(o, d, b.first, b.second)?.let { q to it } }.minByOrNull { it.second }?.first === p
                }
                val c = box.first
                val h = box.second
                val parts = listOf(c, doubleArrayOf(c[0] - h[0] * 0.7, c[1], c[2]), doubleArrayOf(c[0] + h[0] * 0.7, c[1], c[2]), doubleArrayOf(c[0], c[1] + h[1] * 0.7, c[2]))
                for (o in eyes) if (!reaches(o, c)) bad += "$id/${p.id}: the eyes at ${o.toList()} can't reach its middle"
                for (o in hands) if (parts.none { reaches(o, it) }) bad += "$id/${p.id}: the hand at ${o.toList()} can't reach any of it"
            }
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    private fun Level.run(seconds: Double) { var t = 0.0; while (t < seconds && !over) { step(Level.STEP); t += Level.STEP } }

    /** A rough hand: every pinch lands [MISS] off the thing, and it waits for the dog like a player. */
    private fun Level.pinchNear(id: String, seconds: Double = 8.0) {
        tapAt(props[id]!!.pos + P(MISS, -MISS * 0.6))
        run(seconds)
    }

    @Test
    fun pointingAtTheSofaMeansThePillowOnIt() {
        val l = Levels.make("living")
        l.tapProp("sofa")
        l.run(8.0)
        assertTrue(l.props["pillow"]!!.done)
        // Nothing left to do on it: the dog still goes over.
        val start = l.husky.at
        l.tapFloor(P(0.5, 0.3))
        l.run(6.0)
        val there = l.husky.at
        l.tapProp("sofa")
        l.run(6.0)
        assertTrue("the dog went from $there (first at $start), now ${l.husky.at}", l.husky.at.dist(there) > 0.1)
    }

    @Test
    fun aNearMissIsTheThingMeant() {
        for (id in Levels.IDS) {
            val l = Levels.make(id)
            for (p in l.props.values) {
                if (p.kind == Kind.SOLID || p.kind == Kind.DROP || p.kind == Kind.GATE || p.needs != null || p.kind == Kind.DOOR && id == "bath") continue
                for (k in 0 until 8) {
                    val h = k * Math.PI / 4
                    val got = l.aimedAt(p.pos + P(Math.cos(h), Math.sin(h)) * MISS)
                    assertTrue("$id: ${MISS} m off ${p.id} means ${got?.id}", got != null && got.pos.dist(p.pos) < 0.3)
                }
            }
        }
        assertEquals(null, Levels.make("living").aimedAt(P(0.45, -0.1)))
    }

    @Test
    fun theFirstRoomCanBeDoneByPointingRoughly() {
        val l = Levels.make("living") as Living
        l.pinchNear("pillow")
        assertTrue("pillow", l.props["pillow"]!!.done)
        // One pinch on the toilet roll is the whole job: it runs off with it, then puts it down.
        l.pinchNear("tissue", 12.0)
        assertTrue("trail ${l.trail.length}", l.trail.length >= Living.TRAIL_ENOUGH)
        assertEquals("the roll is put down", null, l.husky.trailing)
        // One pinch on the baozi: the stool first, then the baozi.
        l.pinchNear("baozi", 16.0)
        assertTrue("stool", l.props["stool"]!!.done)
        assertTrue("baozi", l.props["baozi"]!!.done)
        l.pinchNear("remote")
        assertTrue("remote in mouth", l.husky.carrying != null)
        l.pinchNear("sofagap")
        assertTrue("remote hidden", l.props["remote"]!!.hidden)
        assertTrue("main goals met", l.mainAt >= 0)
    }

    @Test
    fun theTeaTableMeansTheBaoziOnIt() {
        val l = Levels.make("living")
        l.tapProp("teatable")
        l.run(16.0)
        assertTrue(l.props["baozi"]!!.done)
    }

    @Test
    fun theToiletRollIsNeverLost() {
        val l = Levels.make("living") as Living
        l.tapProp("tissue")
        var t = 0.0
        while (l.husky.trailing == null && t < 10.0) { l.step(Level.STEP); t += Level.STEP }
        assertTrue("took it up", l.husky.trailing != null)
        // A dash early on lets go of it, short of the job; pinching it again takes it up again.
        l.dash(P(1.0, 0.0), 0.2)
        l.run(1.0)
        assertEquals(null, l.husky.trailing)
        assertTrue("not enough yet: ${l.trail.length}", l.trail.length < Living.TRAIL_ENOUGH)
        l.tapProp("tissue")
        l.run(15.0)
        assertTrue("done in the end: ${l.trail.length}", l.trail.length >= Living.TRAIL_ENOUGH)
        assertEquals("and put down", null, l.husky.trailing)
        assertTrue("and no more of it", l.aimedAt(l.props["tissue"]!!.pos) !== l.props["tissue"])
    }

    @Test
    fun pinchingNearTheDogMeansTheDogWithTheRollInItsMouth() {
        val l = Levels.make("living") as Living
        l.tapProp("tissue")
        var t = 0.0
        while (l.husky.trailing == null && t < 10.0) { l.step(Level.STEP); t += Level.STEP }
        l.tapFloor(l.husky.at + P(0.1, 0.1))
        l.run(1.0)
        val from = l.husky.at
        l.pinchDog()
        l.run(3.0)
        assertTrue("ran off from $from, now ${l.husky.at}", l.husky.at.dist(from) > 0.1)
    }

    @Test
    fun aDogAlreadyStandingThereGetsOnWithIt() {
        val l = Levels.make("living")
        val p = l.props["pillow"]!!
        l.tapFloor(p.spot)
        l.run(6.0)
        l.tapProp("pillow")
        l.run(3.0)
        assertTrue(p.done)
    }

    @Test
    fun theKitchenTakesOnePinchPerJob() {
        val l = Levels.make("kitchen") as Kitchen
        fun play(seconds: Double) { var t = 0.0; while (t < seconds && !l.over) { if (l.owner.warning > 0 && l.husky.innocent <= 0) l.pinchDog(); l.step(Level.STEP); t += Level.STEP } }
        l.tapAt(l.props["dumplings"]!!.pos + P(MISS, 0.0))
        play(40.0)
        assertEquals(5, l.props["dumplings"]!!.count)
        l.tapAt(l.props["flour"]!!.pos + P(MISS, 0.0))
        play(60.0)
        assertTrue("framed", l.framed)
        assertEquals(0, l.strikes)
    }

    @Test
    fun anyFlagMeansTheNextFlag() {
        val l = Levels.make("yard") as Yard
        l.tapProp("g3")
        l.run(8.0)
        assertTrue("the sled first", l.sledOn)
        repeat(4) {
            l.tapProp("g2")
            l.run(6.0)
        }
        assertEquals(4, l.gate)
        l.tapAt(l.props["snowman"]!!.pos + P(-MISS, 0.0))
        l.run(8.0)
        assertTrue(l.props["snowman"]!!.knocked)
    }

    @Test
    fun theForestIsWalkedByPinchingTheWayHome() {
        val l = Levels.make("forest") as Forest
        var t = 0.0
        while (!l.over && t < 200.0) {
            if (!l.animal.busy && l.animal.path.isEmpty()) l.tapProp("exit")
            l.run(1.0)
            t += 1.0
        }
        assertTrue("home by pinching only the exit (${l.husky.at}, ${l.roe!!.at}, time ${l.time})", l.won)
    }

    @Test
    fun theLastCheckIsPassedByPinchingTheDog() {
        val l = Levels.make("home") as Home
        for (id in listOf("feathers", "paper", "prints", "cat")) l.pinchNear(id, 14.0)
        var t = 0.0
        while (!l.over && t < 120.0) {
            if (l.owner.warning > 0 && l.husky.innocent <= 0) l.pinchDog()
            l.step(Level.STEP)
            t += Level.STEP
        }
        assertTrue("won with ${l.strikes} strikes", l.won && l.stars() >= 2)
    }

    @Test
    fun aPinchLandingOnTheDogBesideAThingMeansTheThing() {
        val l = Levels.make("living")
        val p = l.props["pillow"]!!
        l.tapFloor(p.spot)
        l.run(6.0)
        l.pinchDog()
        l.run(4.0)
        assertTrue("the dog at the pillow, pinched, bites it", p.done)
        // In the yard: standing at a flag, a pinch on the dog is the next flag.
        val y = Levels.make("yard") as Yard
        y.tapProp("sled")
        y.run(8.0)
        y.tapProp("g1")
        y.run(8.0)
        assertEquals(1, y.gate)
        y.pinchDog()
        y.run(8.0)
        assertEquals(2, y.gate)
    }

    @Test
    fun theOneAlreadyHomeDoesNotBlockTheWayHome() {
        val l = Levels.make("forest") as Forest
        var t = 0.0
        val home = P(0.72, 0.3)
        while (t < 120.0 && l.roe!!.at.dist(home) > 0.1 && l.husky.at.dist(home) > 0.1) {
            if (!l.animal.busy && l.animal.path.isEmpty()) l.tapProp("exit")
            l.run(1.0)
            t += 1.0
        }
        l.run(1.0)
        val waiting = if (l.roe!!.at.dist(home) <= 0.1) l.roe!! else l.husky
        val out = if (waiting === l.husky) l.roe!! else l.husky
        assertTrue("the other one's turn", l.animal === out)
        // A pinch on the way home lands on the one standing on it.
        assertTrue(l.pinchedBeside(waiting))
        assertTrue("still the other one's turn, and on its way", l.animal === out && out.path.isNotEmpty())
    }

    @Test
    fun lookingInnocentDoesNotForgetWhereItWasGoing() {
        val l = Levels.make("kitchen") as Kitchen
        // Past the first look, then off to the pin, and over to the balcony with it.
        fun play(seconds: Double) { var t = 0.0; while (t < seconds && !l.over) { if (l.owner.warning > 0 && l.husky.innocent <= 0) l.pinchDog(); l.step(Level.STEP); t += Level.STEP } }
        l.tapProp("pin")
        var t = 0.0
        while (l.husky.carrying == null && t < 60.0) { play(0.5); t += 0.5 }
        assertEquals("pin", l.husky.carrying?.id)
        // One pinch on the balcony, however many times he looks round on the way.
        l.tapProp("balcony")
        play(60.0)
        assertTrue("hid it with a single pinch (strikes ${l.strikes})", l.props["pin"]!!.hidden)
        assertEquals(0, l.strikes)
    }

    @Test
    fun callingItADayEndsTheLevel() {
        val l = Levels.make("living")
        for (id in listOf("pillow", "tissue", "baozi", "remote", "sofagap")) l.pinchNear(id, 18.0)
        assertTrue("main goals met", l.mainAt >= 0)
        // Between frames, like the button: what it does must reach the next frame.
        l.wrapUp()
        assertTrue(l.step(Level.STEP).any { it is Ev.Won })
    }

    @Test
    fun whatAPinchSaysGetsThrough() {
        val l = Levels.make("living")
        l.tapProp("tv")
        assertTrue(l.step(Level.STEP).any { it is Ev.Said })
    }

    @Test
    fun aPinchWhileBusyWaitsItsTurn() {
        val l = Levels.make("living")
        l.tapProp("pillow")
        var t = 0.0
        while (l.husky.doing == null && t < 6.0) { l.step(Level.STEP); t += Level.STEP }
        l.tapProp("remote")
        l.run(10.0)
        assertTrue(l.props["pillow"]!!.done)
        assertEquals("remote", l.husky.carrying?.id)
    }

    @Test
    fun itTurnsRoundBeforeRunning() {
        val l = Levels.make("living")
        l.tapFloor(P(0.0, -0.1))
        l.step(Level.STEP)
        val start = l.husky.at
        l.husky.heading = headingOf(P(0.0, 1.0))
        l.tapFloor(start + P(0.0, -0.3))
        var moved = false
        var t = 0.0
        while (t < 2.0 && l.husky.path.isNotEmpty()) {
            val before = l.husky.at
            val wanted = headingOf(l.husky.path[0] - before)
            l.step(Level.STEP)
            if (l.husky.at != before) {
                moved = true
                assertTrue("running ${angleBetween(l.husky.heading, wanted)} off where it is going", kotlin.math.abs(angleBetween(l.husky.heading, wanted)) <= Level.TURN_FIRST + 0.01)
            }
            t += Level.STEP
        }
        assertTrue(moved)
    }

    private companion object {
        const val MISS = 0.1
    }

    @Test
    fun pathsGoAroundFurniture() {
        val l = Levels.make("living")
        val path = l.floor.path(P(-0.3, -0.2), P(0.3, -0.2))
        assertTrue(path.isNotEmpty())
        var cur = l.husky.at
        for (p in listOf(P(-0.3, -0.2)) + path) {
            assertTrue("leg $cur -> $p clear", l.floor.clear(cur, p) || cur == l.husky.at)
            cur = p
        }
        assertFalse(l.floor.walkable(P(0.0, -0.37)))
    }

    @Test
    fun theOwnerSeesOnlyAhead() {
        val walls = listOf(Block(0.0, -0.2, 0.1, 0.02))
        assertTrue(sees(P(0.0, 0.0), 0.0, 0.9, 1.0, P(0.0, -0.1), emptyList()))
        assertFalse("behind him", sees(P(0.0, 0.0), 0.0, 0.9, 1.0, P(0.0, 0.3), emptyList()))
        assertFalse("behind the sofa", sees(P(0.0, 0.0), 0.0, 0.9, 1.0, P(0.0, -0.4), walls))
    }

    @Test
    fun caughtThreeTimesLoses() {
        val l = Levels.make("kitchen")
        l.caught("1")
        l.step(3.5)
        l.caught("2")
        l.step(3.5)
        assertFalse(l.over)
        l.caught("3")
        assertTrue(l.over)
        assertFalse(l.won)
        assertEquals(0, l.stars())
    }

    @Test
    fun innocentKeepsTheDeedsProgress() {
        val l = Levels.make("kitchen") as Kitchen
        l.tapProp("dumplings")
        var k = 0
        while (l.husky.doing == null && k++ < 200) l.step(Level.STEP)
        l.step(0.6)
        l.beInnocent()
        val p = l.props["dumplings"]!!
        assertTrue(p.progress > 0.4)
        assertEquals(0, p.count)
    }

    @Test
    fun timeRunsOut() {
        val l = Levels.make("living")
        while (!l.over) l.step(1.0)
        assertFalse(l.won)
    }
}
