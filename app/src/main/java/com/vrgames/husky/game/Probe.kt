package com.vrgames.husky.game

import android.os.Handler
import android.os.SystemClock
import android.util.Log
import com.pico.spatial.core.math.Vector3
import com.vrgames.husky.core.Bath
import com.vrgames.husky.core.Forest
import com.vrgames.husky.core.Home
import com.vrgames.husky.core.Kitchen
import com.vrgames.husky.core.Level
import com.vrgames.husky.core.Levels
import com.vrgames.husky.core.Living
import com.vrgames.husky.core.Looks
import com.vrgames.husky.core.P
import com.vrgames.husky.core.Yard

/**
 * The test hook's rough player: for each job of each level, a ray from the seat (the eyes or a low
 * hand) at the thing itself, its name, or the floor 10 cm off it, through the boxes really in the
 * scene; what it pinches goes through the same entry as a real pinch, then the job must get done.
 * Only the system's own ray and a real hand's waver are left out.
 */
object Probe {
    private enum class How { DOG, BODY, TAG, NEAR, FLOOR, WAIT }
    private class Job(val id: String, val how: How, val at: P? = null, val done: (Level) -> Boolean)

    private const val TAG = AutoPilot.TAG
    private const val TIMEOUT_MS = 120_000L
    private const val RETRY_MS = 25_000L
    private const val SPEED = 5.0
    private val EYES = Vector3(0f, 0.75f, 1.15f)
    private val HAND = Vector3(0.25f, 0.3f, 0.8f)

    private fun jobs(id: String): List<Job> = when (id) {
        "living" -> listOf(
            Job("dog", How.DOG) { Game.dogMark > 0 },
            Job("pillow", How.NEAR) { it.props["pillow"]!!.done },
            Job("tissue", How.TAG) { (it as Living).trail.length >= Living.TRAIL_ENOUGH && it.husky.trailing == null },
            Job("baozi", How.TAG) { it.props["stool"]!!.done },
            Job("teatable", How.BODY) { it.props["baozi"]!!.done },
            Job("remote", How.NEAR) { it.husky.carrying?.id == "remote" },
            Job("sofa", How.BODY) { it.props["remote"]!!.hidden },
            Job("plant", How.TAG) { it.props["plant"]!!.knocked },
        )
        "kitchen" -> listOf(
            Job("dumplings", How.NEAR) { it.props["dumplings"]!!.count >= 5 },
            Job("pin", How.TAG) { it.husky.carrying?.id == "pin" },
            Job("balcony", How.BODY) { it.props["pin"]!!.hidden },
            Job("flour", How.NEAR) { (it as Kitchen).framed },
        )
        "bath" -> listOf(
            Job("slippers", How.BODY) { it.husky.carrying?.id == "slippers" },
            Job("far", How.FLOOR, P(-0.45, 0.4)) { it.props["slippers"]!!.count > 0 },
            Job("plush", How.TAG) { it.husky.carrying?.id == "plush" },
            Job("far", How.FLOOR, P(0.7, 0.4)) { it.props["plush"]!!.count > 0 },
            Job("bed", How.NEAR) { it.husky.hiddenIn?.id == "bed" },
            Job("closet", How.BODY) { it.husky.hiddenIn?.id == "closet" },
            Job("curtain", How.TAG) { it.husky.hiddenIn?.id == "curtain" },
            Job("sofaback", How.NEAR) { it.husky.hiddenIn?.id == "sofaback" },
        )
        "yard" -> listOf(
            Job("m2", How.NEAR) { it.props["m2"]!!.done },
            Job("m5", How.BODY) { it.props["m5"]!!.done },
            Job("sled", How.TAG) { (it as Yard).sledOn },
            Job("g3", How.BODY) { (it as Yard).gate >= 1 },
            Job("g1", How.BODY) { (it as Yard).gate >= 2 },
            Job("g4", How.NEAR) { (it as Yard).gate >= 3 },
            Job("g2", How.BODY) { (it as Yard).gate >= 4 },
            Job("snowman", How.NEAR) { it.props["snowman"]!!.knocked },
            Job("siren", How.WAIT) { (it as Yard).howls >= 1 },
        )
        "forest" -> listOf(
            Job("rock", How.NEAR) { (it as Forest).dug },
            Job("rock", How.BODY) { (it as Forest).bridge },
            Job("exit", How.TAG) { l -> l.husky.at.dist(P(0.72, 0.3)) < 0.1 || l.roe!!.at.dist(P(0.72, 0.3)) < 0.1 },
            Job("exit", How.TAG) { it.won },
        )
        "home" -> listOf(
            Job("sofa", How.BODY) { it.husky.at.z < 0.0 },
            Job("feathers", How.NEAR) { it.props["feathers"]!!.done },
            Job("paper", How.BODY) { it.props["paper"]!!.done },
            Job("prints", How.TAG) { it.props["prints"]!!.done },
            Job("cat", How.NEAR) { (it as Home).catScene },
            Job("inspection", How.WAIT) { it.won },
        )
        else -> emptyList()
    }

    private lateinit var main: Handler
    private var levels = emptyList<String>()
    private var levelAt = 0
    private var list = emptyList<Job>()
    private var jobAt = 0
    private var shots = 0
    private var startedAt = 0L
    private var firedAt = 0L
    private var fired = false
    private var dogPinched = 0L
    private var ok = 0
    private var failed = 0
    private var wrongHits = 0

    fun start(handler: Handler, only: String?) {
        main = handler
        levels = only?.split(",") ?: Levels.IDS
        levelAt = 0
        ok = 0
        failed = 0
        wrongHits = 0
        Game.grant(3)
        Game.timeScale = SPEED
        nextLevel()
    }

    private fun nextLevel() {
        if (levelAt >= levels.size) {
            Game.timeScale = 1.0
            Log.i(TAG, "PROBE-DONE ok=$ok failed=$failed wrongHits=$wrongHits")
            return
        }
        val id = levels[levelAt++]
        list = jobs(id)
        jobAt = 0
        Game.intro(id)
        Log.i(TAG, "PROBE level $id: ${list.size} jobs")
        main.postDelayed({
            Game.begin()
            main.postDelayed({ beginJob() }, 3000)
        }, 1500)
    }

    private fun beginJob() {
        fired = false
        startedAt = SystemClock.uptimeMillis()
        poll()
    }

    private fun poll() {
        val l = Game.level ?: return
        val now = SystemClock.uptimeMillis()
        if (jobAt >= list.size) {
            Log.i(TAG, "PROBE level ${l.id} end: strikes=${l.strikes} won=${l.won} over=${l.over} time=${"%.0f".format(l.time)} goals=${l.goals.filter { it.met }.map { it.id }}")
            main.postDelayed(::nextLevel, 1000)
            return
        }
        val j = list[jobAt]
        when {
            j.done(l) -> {
                ok++
                Log.i(TAG, "PROBE ok ${l.id}/${j.id} ${j.how} game=${"%.0f".format(l.time)} strikes=${l.strikes}")
                jobAt++
                main.postDelayed(::beginJob, 400)
                return
            }
            l.over || now - startedAt > TIMEOUT_MS -> {
                failed++
                Log.i(TAG, "PROBE FAIL ${l.id}/${j.id} ${j.how}: over=${l.over} strikes=${l.strikes} dog=${l.husky.at} doing=${l.husky.doing?.id} carrying=${l.husky.carrying?.id}")
                jobAt++
                if (l.over) jobAt = list.size
                main.postDelayed(::beginJob, 400)
                return
            }
        }
        // As a player would: pinch the dog when he is about to look, or when the siren sounds.
        val siren = (l as? Yard)?.let { it.time - it.sirenAt in 0.0..Yard.HOWL_WINDOW } == true
        val warned = l.owner.present && l !is Bath && l.owner.warning > 0 && l.husky.innocent <= 0
        if ((warned || siren) && now - dogPinched > 1000) {
            dogPinched = now
            val a = l.husky.at
            val hit = Game.view?.rayHit(EYES, Vector3(a.x.toFloat(), 0.08f, a.z.toFloat()))
            if (hit != null) Game.debugTap?.invoke(hit)
            Log.i(TAG, "PROBE dog pinched (${if (siren) "siren" else "warning"}): ray hit $hit")
        }
        val free = l.animal.path.isEmpty() && !l.animal.busy && l.animal.innocent <= 0
        if (j.how != How.WAIT && free && (!fired || now - firedAt > RETRY_MS)) {
            fire(l, j)
            fired = true
            firedAt = now
        }
        main.postDelayed(::poll, 250)
    }

    private fun fire(l: Level, j: Job) {
        val p = l.props[j.id]
        val to = when (j.how) {
            How.DOG -> Vector3(l.husky.at.x.toFloat(), 0.08f, l.husky.at.z.toFloat())
            How.FLOOR -> j.at!!.let { Vector3(it.x.toFloat(), 0.006f, it.z.toFloat()) }
            How.NEAR -> {
                // 10 cm off, on the side of the room's middle so it stays on the floor.
                val x = p!!.pos.x + if (p.pos.x > 0) -0.08 else 0.08
                val z = p.pos.z + if (p.pos.z > 0) -0.06 else 0.06
                Vector3(x.toFloat(), 0.006f, z.toFloat())
            }
            How.TAG -> Vector3(p!!.pos.x.toFloat(), (p.lift + Looks.tagY(p)).toFloat(), p.pos.z.toFloat())
            else -> {
                val b = Looks.hitBox(p!!)!!
                Vector3(p.pos.x.toFloat(), (p.lift + b.cy).toFloat(), p.pos.z.toFloat())
            }
        }
        val from = if (shots++ % 2 == 0) EYES else HAND
        val hit = Game.view?.rayHit(from, to)
        val meant = when (j.how) {
            How.DOG -> hit == "husky"
            How.NEAR, How.FLOOR -> hit?.startsWith("tile:") == true || hit == "prop:${j.id}"
            else -> hit == "prop:${j.id}"
        }
        if (!meant) wrongHits++
        Log.i(TAG, "PROBE aim ${l.id}/${j.id} ${j.how} from ${if (from === EYES) "eyes" else "hand"}: ray hit $hit${if (meant) "" else "  (not what was aimed at)"}")
        if (hit != null) Game.debugTap?.invoke(hit)
    }
}
