package com.vrgames.husky.core

/**
 * Plays a level as a careful player would, one command at a time: each [Step] is issued once the
 * animals are free (and, where it matters, the owner is not looking), and is done when its [done]
 * check passes. Used by the unit tests (every level can be won, in time, with three stars) and by
 * the test hook on the emulator.
 */
class Solver(private val l: Level) {
    class Step(val say: String, val ready: (Level) -> Boolean = { true }, val run: (Level) -> Unit, val done: (Level) -> Boolean, val atOnce: Boolean = false)

    private val steps: List<Step> = when (l) {
        is Living -> living(l)
        is Kitchen -> kitchen(l)
        is Bath -> bath(l)
        is Yard -> yard(l)
        is Forest -> forest(l)
        is Home -> home(l)
        else -> emptyList()
    }
    private var at = 0
    private var issued = false
    private var wait = 0.0
    val finished get() = at >= steps.size

    /** Call every slice: issues the next command when it is time. */
    fun update(dt: Double) {
        guard()
        if (l is Bath) bathMove(l)
        if (at >= steps.size) return
        if (wait > 0) { wait -= dt; return }
        val s = steps[at]
        if (!issued) {
            if ((s.atOnce || free()) && s.ready(l)) {
                s.run(l)
                issued = true
            }
            return
        }
        if (s.done(l)) {
            at++
            issued = false
            wait = 0.3
        } else if (free() && l.husky.doing == null && l.husky.path.isEmpty() && (l.roe?.path?.isEmpty() ?: true)) {
            // Interrupted (looking innocent, caught): issue it again.
            issued = false
        }
    }

    private fun free() = !l.husky.busy && l.husky.path.isEmpty() && (l.roe?.let { !it.busy && it.path.isEmpty() } ?: true)

    /** Breaks off mischief when the owner is about to turn (as the player should). */
    private fun guard() {
        val o = l.owner
        if (!o.present || l is Bath) return
        val risky = l.husky.doing?.mischief == true || l.husky.carrying?.mischief == true
        if (o.warning > 0 && (risky || l is Kitchen && l.dodges < 2) && l.husky.innocent <= 0) l.beInnocent()
        if (l is Home && o.warning > 0 && l.husky.innocent <= 0) l.beInnocent()
    }

    private fun quiet(l: Level) = l.owner.warning <= 0 && l.owner.looking <= 0

    private fun living(l: Living) = listOf(
        Step("pillow", run = { it.tapProp("pillow") }, done = { it.props["pillow"]!!.done }),
        Step("tissue", run = { it.tapProp("tissue") }, done = { it.husky.trailing != null }),
        // Steered short of 60 cm, so it keeps the roll all the way to the sofa.
        Step("trail1", run = { it.tapFloor(P(-0.38, 0.12)) }, done = { it.husky.at.dist(P(-0.38, 0.12)) < 0.06 }, atOnce = true),
        Step("trail2", run = { it.tapFloor(P(0.0, -0.22)) }, done = { it.husky.at.dist(P(0.0, -0.22)) < 0.06 }),
        Step("stool", run = { it.tapProp("stool") }, done = { it.props["stool"]!!.done }),
        Step("baozi", run = { it.tapProp("baozi") }, done = { it.props["baozi"]!!.done }),
        Step("remote", run = { it.tapProp("remote") }, done = { it.husky.carrying != null }),
        Step("hide", run = { it.tapProp("sofagap") }, done = { it.props["remote"]!!.hidden }),
        Step("line up", run = { it.tapFloor(P(0.2, 0.36)) }, done = { it.husky.at.dist(P(0.2, 0.36)) < 0.07 }),
        Step("dash", run = { it.dash(P(1.0, 0.0), 1.0) }, done = { it.props["plant"]!!.knocked }),
    )

    private fun kitchen(l: Kitchen) = buildList {
        add(Step("near the plate", run = { it.tapFloor(P(0.3, -0.05)) }, done = { it.husky.at.dist(P(0.3, -0.05)) < 0.05 }))
        add(Step("dodge 1", ready = { quiet(it) && l.nextTurn in 0.2..0.9 }, run = { it.tapProp("dumplings") }, done = { l.dodges >= 1 }))
        add(Step("dodge 2", ready = { quiet(it) && l.nextTurn in 0.2..0.9 }, run = { it.tapProp("dumplings") }, done = { l.dodges >= 2 }))
        repeat(5) { k ->
            add(Step("dumpling ${k + 1}", ready = { quiet(it) && l.nextTurn > 1.6 }, run = { it.tapProp("dumplings") }, done = { it.props["dumplings"]!!.count > k }))
        }
        add(Step("pin", ready = { quiet(it) && l.nextTurn > 2.2 }, run = { it.tapProp("pin") }, done = { it.husky.carrying != null }))
        add(Step("balcony", ready = { quiet(it) }, run = { it.tapProp("balcony") }, done = { it.props["pin"]!!.hidden }))
        add(Step("by the shelf", run = { it.tapFloor(P(-0.3, -0.25)) }, done = { it.husky.at.dist(P(-0.3, -0.25)) < 0.07 }))
        add(Step("frame the cat", ready = { quiet(it) && l.cat.at.dist(P(-0.6, -0.25)) < 0.12 }, run = { it.dash(P(-1.0, 0.0), 1.0) }, done = { l.framed }))
    }

    /** The bath: throw both decoys early, then the hiding is a policy (see [bathMove]). */
    private fun bath(l: Bath) = listOf(
        Step("slippers", run = { it.tapProp("slippers") }, done = { it.husky.carrying != null }),
        Step("throw far", run = { it.tapFloor(P(-0.45, 0.4)) }, done = { it.props["slippers"]!!.count > 0 }),
        Step("plush", run = { it.tapProp("plush") }, done = { it.husky.carrying != null }),
        Step("throw plush", run = { it.tapFloor(P(0.7, 0.4)) }, done = { it.props["plush"]!!.count > 0 }),
    )

    /** Hide as far from him as possible; move on when he heads for my place; leave once the door opens. */
    private fun bathMove(l: Bath) {
        if (!finished || !free()) return
        val o = l.owner
        if (l.survived) {
            if (o.at.dist(P(-0.15, 0.42)) > 0.4 && !l.escaped) l.tapProp("door")
            return
        }
        val hid = l.husky.hiddenIn?.id
        // Stay put unless he is heading here (or this is next on his round and he is between spots).
        if (hid != null && l.target != hid && !(l.target == null && l.upcoming == hid)) return
        val spots = listOf("bed", "closet", "curtain", "sofaback").filter { it != hid && it != l.target && it != l.upcoming && it !in l.known }
        // The place farthest from him, preferring the one he just checked once he has walked off.
        val pick = l.lastChecked?.takeIf { it in spots && l.props[it]!!.spot.dist(o.at) > 0.45 } ?: spots.maxByOrNull { l.props[it]!!.spot.dist(o.at) } ?: return
        l.tapProp(pick)
    }

    private fun yard(l: Yard) = listOf(
        Step("sniff", run = { it.sniff() }, done = { true }),
        Step("m2", run = { it.tapProp("m2") }, done = { it.props["m2"]!!.done }),
        Step("m5", run = { it.tapProp("m5") }, done = { it.props["m5"]!!.done }),
        Step("howl 1", ready = { it.time - l.sirenAt < 1.5 }, run = { it.howl() }, done = { l.howls >= 1 }),
        Step("m7", run = { it.tapProp("m7") }, done = { it.props["m7"]!!.done }),
        Step("sled", run = { it.tapProp("sled") }, done = { l.sledOn }),
        Step("g1", run = { it.tapProp("g1") }, done = { l.gate >= 1 }),
        Step("g2", run = { it.tapProp("g2") }, done = { l.gate >= 2 }),
        Step("g3", run = { it.tapProp("g3") }, done = { l.gate >= 3 }),
        Step("g4", run = { it.tapProp("g4") }, done = { l.gate >= 4 }),
        Step("howl 2", ready = { it.time - l.sirenAt < 1.5 }, run = { it.howl() }, done = { l.howls >= 2 }),
        Step("line up", run = { it.tapFloor(P(0.45, 0.1)) }, done = { it.husky.at.dist(P(0.45, 0.1)) < 0.07 }),
        Step("snowman", run = { it.dash(P(1.0, 0.0), 1.0) }, done = { it.props["snowman"]!!.knocked }),
        Step("howl 3", ready = { it.time - l.sirenAt < 1.5 }, run = { it.howl() }, done = { l.howls >= 3 }),
    )

    private fun forest(l: Forest) = listOf(
        Step("dig", run = { it.tapProp("bank") }, done = { l.dug }),
        Step("to roe", run = { it.switch() }, done = { it.active == Who.ROE }),
        Step("jump", run = { it.tapProp("rock") }, done = { l.bridge }),
        Step("roe top corridor", run = { it.tapFloor(P(0.2, -0.46)) }, done = { it.roe!!.at.dist(P(0.2, -0.46)) < 0.07 }),
        Step("roe along the top", run = { it.tapFloor(P(0.66, -0.46)) }, done = { it.roe!!.at.dist(P(0.66, -0.46)) < 0.07 }),
        Step("roe home", run = { it.tapProp("exit") }, done = { it.roe!!.at.dist(P(0.72, 0.3)) < 0.1 }),
        Step("to husky", run = { it.switch() }, done = { it.active == Who.HUSKY }),
        Step("husky home", run = { it.tapProp("exit") }, done = { it.husky.at.dist(P(0.72, 0.3)) < 0.1 }),
    )

    private fun home(l: Home) = listOf(
        Step("feathers", run = { it.tapProp("feathers") }, done = { it.props["feathers"]!!.done }),
        Step("paper", run = { it.tapProp("paper") }, done = { it.props["paper"]!!.done }),
        Step("prints", run = { it.tapProp("prints") }, done = { it.props["prints"]!!.done }),
        Step("cat", run = { it.tapProp("cat") }, done = { l.catScene }),
        Step("sit tight", run = { it.tapFloor(P(0.0, 0.25)) }, done = { l.phase == 2 }),
    )
}
