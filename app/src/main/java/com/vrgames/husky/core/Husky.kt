package com.vrgames.husky.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** What a thing on the floor does when the dog is sent to it. */
enum class Kind {
    /** Furniture: in the way, nothing to do. */
    SOLID,
    /** Bite it apart (a pillow): feathers everywhere. */
    BITE,
    /** Take it in the mouth and carry it; tap a drop place (or the floor) to put it down. */
    CARRY,
    /** Grab the end and run: a trail follows the dog (the toilet roll). */
    TRAIL,
    /** Eat it (needs [Prop.needs] done first, like the stool pushed to the table). */
    EAT,
    /** Push it to its [Prop.goal] (the stool). */
    PUSH,
    /** Knocked over by a dash (a vase, a pile of books). */
    KNOCK,
    /** A place to drop something carried (the sofa's gap, the balcony). */
    DROP,
    /** Dig here (the yard's bones, a snowbank). */
    DIG,
    /** Hide here (under the bed): the dog cannot be seen. */
    HIDE,
    /** A gate of the sled run, taken in order. */
    GATE,
    /** Clean it away before the owner sees it (the last level's evidence). */
    CLEAN,
    /** Jump up here (the roe deer only). */
    LEDGE,
    /** A door to leave by. */
    DOOR,
}

/**
 * A thing on the floor. [at] its middle, [spot] where the dog stands to deal with it, [block] its
 * footprint (if it is in the way), [tall] whether it hides what is behind it from the owner's eyes,
 * [seconds] how long the deed takes, [mischief] whether being seen doing it gets the dog caught.
 */
class Prop(
    val id: String,
    val kind: Kind,
    var at: P,
    val model: String,
    val size: Double,
    val spot: P = at,
    val block: Block? = null,
    val tall: Boolean = false,
    val seconds: Double = 1.5,
    val mischief: Boolean = true,
    val needs: String? = null,
    val goal: P? = null,
    val label: String = "",
    val yaw: Double = 0.0,
    val knockable: Boolean = false,
    /** How high above the floor it stands (the baozi on the tea table). */
    val lift: Double = 0.0,
) {
    var done = false
    var count = 0
    var hidden = false
    /** Where a carried or pushed prop is now (moves with the dog). */
    var pos = at
    var knocked = false
    /** Seconds of the deed already done (kept when the dog breaks off to look innocent). */
    var progress = 0.0
    /** Finished with for good, though not [done] in the ordinary way (the toilet roll put down). */
    var retired = false
    /** The top of the furniture it stands on, above its own foot (0 on the floor): its name and box clear it. */
    var base = 0.0
}

/** The box a hand's ray pinches, around a thing's foot: [w]×[h]×[d], its middle [cy] above the foot. */
class HitBox(val w: Double, val h: Double, val d: Double, val cy: Double)

/** How the things look: what the pinch boxes must match, or furniture swallows what stands on it. */
object Looks {
    /** Each AI model's height on the diorama (its file is 1 m tall). */
    val MODEL_H = mapOf(
        "sofa" to 0.15f, "teatable" to 0.065f, "stool" to 0.055f, "tvstand" to 0.15f, "kitchen" to 0.3f, "bed" to 0.13f,
        "bathtub" to 0.12f, "doghouse" to 0.17f, "sled" to 0.05f, "pine" to 0.48f, "birch" to 0.5f, "cottage" to 0.36f,
        "fence" to 0.1f, "plush" to 0.07f, "slippers" to 0.03f, "flour" to 0.07f, "baozi" to 0.035f,
    )
    /** The tops of the things made from shapes. */
    private val CODED_TOP = mapOf(
        "pillow" to 0.12, "dumplings" to 0.085, "lowtable" to 0.063, "shelf" to 0.16, "curtain" to 0.26, "closet" to 0.28,
        "vase" to 0.06, "plant" to 0.08, "flag" to 0.09, "snowman" to 0.1, "rock" to 0.08, "rocks" to 0.05, "snowbank" to 0.05,
    )
    /** Things up on furniture: their boxes start here, not on the floor. */
    private val CODED_BOTTOM = mapOf("pillow" to 0.05, "dumplings" to 0.055)
    private const val MIN_WIDE = 0.13
    private const val MIN_TALL = 0.07
    private const val MIN_TALL_UP = 0.045
    /** The dog's own pinch box (around its feet): [DOG_W] square, [DOG_H] tall. */
    const val DOG_W = 0.1
    const val DOG_H = 0.11
    const val TAG_H = 0.03
    const val TAG_GAP = 0.035
    private const val PLACE_TALL = 0.12

    fun top(p: Prop) = MODEL_H[p.model]?.toDouble() ?: CODED_TOP[p.model] ?: (p.size * 0.6)

    /** Whether [p] floats its name (what can be dealt with). */
    fun hasTag(p: Prop) = p.kind != Kind.SOLID && p.label.isNotEmpty()
    /** Whether the name can be pinched too. (Not a flag's: long, and over the snow piles behind it.) */
    fun pinchTag(p: Prop) = hasTag(p) && p.kind != Kind.GATE
    /** How wide [p]'s pinchable name tag is. */
    fun tagWide(p: Prop) = p.label.length * TAG_H * 0.72 + TAG_H * 0.7 + 0.02
    /** How high above [p]'s foot the middle of its tag floats. */
    fun tagY(p: Prop) = max(top(p), p.base) + TAG_GAP

    /** The box to pinch [p] by, or null for what is only in the way and has no name. */
    fun hitBox(p: Prop): HitBox? {
        if (p.kind == Kind.SOLID && p.label.isEmpty()) return null
        val top = top(p)
        val b = p.block
        if (p.kind == Kind.SOLID && b != null) return HitBox(b.hx * 2, top, b.hz * 2, top / 2)
        val bottom = CODED_BOTTOM[p.model] ?: 0.0
        // A place with nothing standing on it (the gap in the sofa) is pinched by the air above it;
        // a flag is a stick, and must not stand in the way of what is behind it.
        // Small things up on furniture keep a low box: a tall one hid what is behind them (the baozi before the sofa gap).
        val tall = if (p.kind == Kind.DROP && p.model.isEmpty()) max(PLACE_TALL, p.base + 0.05) else max(top - bottom, if (p.lift > 0) MIN_TALL_UP else MIN_TALL)
        val wide = if (p.kind == Kind.GATE) p.size * 0.8 else max(p.size, MIN_WIDE)
        return HitBox(wide, tall, wide, bottom + tall / 2)
    }
}

enum class Task { MAIN, CHALLENGE, EXPERT }

/** A goal of a level: [check] reads the level's state; [tier] decides which stars it counts for. */
class Goal(val id: String, val tier: Task, val text: String, val check: (Level) -> Boolean) {
    var met = false
}

enum class Who { HUSKY, ROE }

/** An animal the player sends about. */
class Animal(val who: Who, var at: P) {
    var heading = 0.0
    var path: List<P> = emptyList()
    /** The deed being done: the prop, and seconds left. */
    var doing: Prop? = null
    var doingLeft = 0.0
    var carrying: Prop? = null
    var trailing: Prop? = null
    var dashVel: P? = null
    var dashLeft = 0.0
    var dashHits = 0
    var innocent = 0.0
    var frozen = 0.0
    var hiddenIn: Prop? = null
    /** The deed, or the walk, broken off to look innocent: taken up again when the coast is clear. */
    var resume: Prop? = null
    var resumeWalk: List<P> = emptyList()
    var resumeThen: (() -> Unit)? = null
    var afterArrive: (() -> Unit)? = null
    /** What it is doing, for the animations. */
    var anim = "idle"
    val busy get() = doing != null || dashVel != null || frozen > 0
}

/** The owner: where, which way he faces, and what he is about. */
class Owner(var at: P) {
    var heading = Math.PI
    var present = true
    var path: List<P> = emptyList()
    var anim = "idle"
    /** Turning to look at the room: warning seconds left, then looking seconds left. */
    var warning = 0.0
    var looking = 0.0
    var say: String? = null
}

/** Something that happened in a step, for the scene, the sounds and the notes. */
sealed interface Ev {
    data class Did(val prop: String, val kind: Kind) : Ev
    data class Knocked(val prop: String) : Ev
    data class Caught(val why: String) : Ev
    data class Warn(val seconds: Double) : Ev
    data class Said(val text: String) : Ev
    data class GoalMet(val id: String, val text: String) : Ev
    data class Sfx(val name: String, val at: P) : Ev
    data object Won : Ev
    data class Lost(val why: String) : Ev
}

/**
 * One level: its floor, things, animals, the owner, the goals, the clock. The game advances it
 * with [step] in slices of at most [STEP] seconds; the player's commands are [tapFloor], [tapProp],
 * [dash], [beInnocent], [howl], [sniff] and [switch]. Levels override the owner's behavior and any
 * rules of their own.
 */
abstract class Level(val id: String, val title: String, val intro: String, val w: Double, val d: Double, val limit: Double) {
    val props = LinkedHashMap<String, Prop>()
    val goals = ArrayList<Goal>()
    lateinit var floor: Floor
    lateinit var husky: Animal
    var roe: Animal? = null
    var active = Who.HUSKY
    val owner = Owner(P(0.0, -d / 2 + 0.1))
    var time = 0.0
    var strikes = 0
    var over = false
    var won = false
    var hints = 0
    val dice = Dice(id.hashCode().toLong())
    protected val events = ArrayList<Ev>()

    val animal get() = if (active == Who.ROE) roe ?: husky else husky
    val left get() = max(0.0, limit - time)

    fun prop(p: Prop) = p.also { props[it.id] = it }
    fun goal(id: String, tier: Task, text: String, check: (Level) -> Boolean) = goals.add(Goal(id, tier, text, check))

    /** Builds the floor from the props' footprints (call after adding them). */
    fun build(start: P) {
        floor = Floor(w, d, props.values.mapNotNull { it.block })
        // What stands on furniture (the pillow and the gap on the sofa): v0.2.2's names sank into it.
        for (p in props.values) if (p.kind != Kind.SOLID) p.base = props.values.filter { q -> q !== p && q.kind == Kind.SOLID && q.block?.has(p.pos) == true }
            .maxOfOrNull { Looks.top(it) - p.lift }?.coerceAtLeast(0.0) ?: 0.0
        husky = Animal(Who.HUSKY, floor.nearestOpen(start))
    }

    private val walls get() = props.values.filter { it.tall }.mapNotNull { it.block }

    // ---- Commands ----

    fun tapFloor(p: P) {
        val a = animal
        if (over || a.busy) return
        pending = null
        val c = a.carrying
        if (c != null) {
            // Carrying: walk there and put it down.
            go(a, p) { drop(a, c, p) }
            return
        }
        go(a, p) { arrived(a) }
    }

    /** The animal got where the floor pinch sent it. */
    protected open fun arrived(a: Animal) {}

    /**
     * The player's pinch on the floor at [p]. A hand's ray wavers more than these things are wide, so
     * a near miss counts as the thing meant.
     */
    fun tapAt(p: P, eye: Vec3? = null) {
        val a = animal
        if (over) return
        if (a.busy) {
            queued = { tapAt(p, eye) }
            return
        }
        val t = aimedAt(p, eye)
        if (t != null) tapProp(t.id) else tapFloor(p)
    }

    /** What a pinch at [p] means: the nearest thing there that can be dealt with now, if any. */
    fun aimedAt(p: P, eye: Vec3? = null): Prop? {
        val a = animal
        val wanted = props.values.filter { q ->
            q !== a.carrying && magnetic(q) && (if (a.carrying != null) q.kind == Kind.DROP else q.kind != Kind.SOLID && meansSomething(a, q))
        }
        eye?.let { e -> aimedAlong(wanted, e, p)?.let { return it } }
        return wanted.filter { q -> q.pos.dist(p) <= max(MAGNET, q.size / 2 + 0.05) }.minByOrNull { it.pos.dist(p) }
    }

    /**
     * A pinch that reached the floor at [p]: the thing the line from the player's [eye] to there passes close by.
     * A ray from a hand in the lap is shallow and grazes past a small or thin thing to land well behind it (a flag,
     * the gap in the sofa): v0.2.2 read that as the floor, and the dog ran off to it.
     */
    private fun aimedAlong(wanted: List<Prop>, eye: Vec3, p: P): Prop? {
        val end = Vec3(p.x, 0.0, p.z)
        val d = end - eye
        val len2 = d.dot(d).coerceAtLeast(1e-9)
        return wanted.mapNotNull { q ->
            val b = Looks.hitBox(q) ?: return@mapNotNull null
            val c = Vec3(q.pos.x, q.lift + b.cy, q.pos.z)
            val t = ((c - eye).dot(d) / len2).coerceIn(0.0, 1.0)
            val miss = (eye + d * t).distanceTo(c)
            val reach = ALONG + max(b.w, b.d) / 2
            if (miss <= reach) q to miss / reach else null
        }.minByOrNull { it.second }?.first
    }

    /** A pinch made while the animal was busy: done as soon as it is free, or it feels ignored. */
    private var queued: (() -> Unit)? = null
    protected val commandWaiting get() = queued != null

    /** Whether a pinch on [p] leads anywhere now: to it, or to what it needs first. */
    private fun meansSomething(a: Animal, p: Prop) = why(a, p) == null || unmet(p)?.let { why(a, it) == null } == true

    /** What must be done before [p] and has not been (the stool before the baozi). */
    private fun unmet(p: Prop) = p.needs?.let { props[it] }?.takeIf { !it.done }

    /** The thing wanted once its need is done: pinching the baozi pushes the stool, then eats. */
    private var pending: Prop? = null

    /** Whether a near miss may be read as [p]. */
    protected open fun magnetic(p: Prop) = true

    /** The thing really meant when the player pinches [p] (a flag before the sled is on: the sled). */
    protected open fun redirect(p: Prop) = p

    /** Hands the controls to the animal that can deal with [p]. */
    protected open fun choose(p: Prop) {}

    /** The player pinches the dog: not a command of its own, so whatever is urgent now. */
    open fun pinchDog() {
        val a = husky
        if (over) return
        when {
            owner.present && (owner.warning > 0 || owner.looking > 0) -> beInnocent()
            a.trailing != null -> trailRun(a)
            else -> {
                // An animal stands right in front of what it was sent to, so a pinch aimed at that
                // thing often lands on the animal: with something to do at its feet, it does that.
                val at = aimedAt(animal.at)
                if (at != null) tapProp(at.id) else say("捏地板或东西，二哈就过去。指到东西附近也算。")
            }
        }
    }

    /**
     * A pinch that landed on the animal not being played, [other]: it may be standing on what the one
     * being played was sent to. True if that is what it means (else it means handing over to [other]).
     */
    fun pinchedBeside(other: Animal): Boolean {
        val t = aimedAt(other.at) ?: return false
        tapProp(t.id)
        return true
    }

    /** A dog with the toilet roll, told to run: off across the room with it. */
    protected open fun trailRun(a: Animal) {}

    fun tapProp(id: String) {
        val asked = props[id] ?: return
        if (over) return
        val p = redirect(asked)
        choose(p)
        val a = animal
        if (a.busy) {
            queued = { tapProp(id) }
            return
        }
        pending = null
        val need = unmet(p)
        if (need != null && why(a, need) == null) {
            say("${p.label}够不着：${needHint(p)}")
            tapProp(need.id)
            pending = p
            return
        }
        val c = a.carrying
        if (a.trailing === p) {
            trailRun(a)
            return
        }
        if (p.kind == Kind.SOLID) {
            tapFurniture(a, p)
            return
        }
        if (c != null && p.kind == Kind.DROP) {
            go(a, p.spot) { drop(a, c, p.at, p) }
            return
        }
        // Carrying something, a pinch that lands on a thing right beside a place to put it down means that place
        // (device v0.2.2: with the remote in its mouth, the pinch at the sofa gap caught the pillow beside it).
        if (c != null) dropBeside(p)?.let { d ->
            go(a, d.spot) { drop(a, c, d.at, d) }
            return
        }
        if (!can(a, p)) return
        if (p.kind == Kind.KNOCK) {
            knock(a, p)
            return
        }
        go(a, p.spot) { start(a, p) }
    }

    /** An open place to put something down within [MAGNET] of [p], the nearest one. */
    fun dropBeside(p: Prop): Prop? = props.values.filter { it.kind == Kind.DROP && !it.done && it.pos.dist(p.pos) <= MAGNET }.minByOrNull { it.pos.dist(p.pos) }

    /**
     * Pointing at furniture means what is on it (the pillow on the sofa, the baozi on the table); with
     * nothing there to do, the dog still goes over: pointing at the sofa must not look broken.
     */
    private fun tapFurniture(a: Animal, f: Prop) {
        val b = f.block
        val on = if (b == null) emptyList() else props.values.filter { q -> q !== f && q.kind != Kind.SOLID && q !== a.carrying && b.has(q.pos, ON_FURNITURE) }
        // What stands on it comes before what is only beside it.
        val meant = on.filter { if (a.carrying != null) it.kind == Kind.DROP else it.kind != Kind.DROP && meansSomething(a, it) }
            .sortedWith(compareBy<Prop> { -it.lift }.thenBy { it.pos.dist(a.at) }).firstOrNull()
        if (meant != null) {
            tapProp(meant.id)
            return
        }
        val reason = on.firstOrNull { !it.done && it.kind != Kind.DROP }?.let { why(a, it) } ?: why(a, f)
        reason?.let { say(it) }
        go(a, if (b != null) beside(b, a.at) else floor.nearestOpen(f.pos)) {}
    }

    /** Knocks [p] over: the dog lines up and charges (no aiming needed). */
    protected open fun knock(a: Animal, p: Prop) = charge(a, p)

    protected fun charge(a: Animal, p: Prop) {
        val stand = runUp(a, p)
        if (stand == null) {
            say("那里过不去。")
            return
        }
        go(a, stand) { dash(p.pos - a.at, 1.0) }
    }

    /** Where to charge [p] from: clear ground in front of it, the nearest to the dog. */
    protected fun runUp(a: Animal, p: Prop): P? = (0 until 8).map { k ->
        val h = k * Math.PI / 4
        p.pos + P(cos(h), sin(h)) * RUN_UP
    }.filter { floor.walkable(it) && floor.clear(it, p.pos) }.minByOrNull { it.dist(a.at) }

    /** Where the dog stands to look at furniture: the side of [b] it is on, level with it if it can. */
    private fun beside(b: Block, from: P) = P(
        from.x.coerceIn(b.cx - b.hx, b.cx + b.hx),
        if (from.z >= b.cz) b.cz + b.hz + FURNITURE_GAP else b.cz - b.hz - FURNITURE_GAP,
    )

    /** Whether [a] may deal with [p] now; says why not if not. */
    private fun can(a: Animal, p: Prop): Boolean {
        val w = why(a, p)
        if (w != null) say(w)
        return w == null
    }

    /** Why [a] can't deal with [p] now, or null if it can. (A dropped toilet roll can always be taken up again.) */
    protected open fun why(a: Animal, p: Prop): String? {
        val n = p.needs?.let { props[it] }
        return when {
            p.kind == Kind.SOLID -> "${p.label}：没什么好玩的。"
            p.kind == Kind.DROP && a.carrying == null -> "先叼点东西来，才能放到${p.label}。"
            p.kind == Kind.KNOCK && a.who != Who.HUSKY -> "傻狍子撞不动，换二哈来。"
            p.done && p.kind != Kind.DROP && p.kind != Kind.HIDE && p.kind != Kind.TRAIL -> "${p.label}已经搞定了。"
            n != null && !n.done -> "${p.label}够不着：${needHint(p)}"
            p.kind == Kind.LEDGE && a.who != Who.ROE -> "二哈跳不上去，傻狍子可以。"
            p.kind == Kind.DIG && a.who != Who.HUSKY -> "傻狍子不会刨，换二哈来。"
            else -> null
        }
    }

    protected open fun needHint(p: Prop) = "先想办法垫个脚。"

    fun dash(dir: P, power: Double) {
        val a = animal
        if (over || a.busy || a.who != Who.HUSKY) return
        a.path = emptyList()
        a.trailing = null
        a.dashVel = dir.norm() * DASH_SPEED
        a.dashLeft = (0.15 + 0.45 * power.coerceIn(0.0, 1.0)) / DASH_SPEED
        a.dashHits = 0
        a.heading = headingOf(dir)
        a.anim = "dash"
        emit(Ev.Sfx("whoosh", a.at))
    }

    /** Sits up straight with the innocent face (cancels the deed in progress, keeps its progress). */
    fun beInnocent() {
        val a = husky
        if (over) return
        a.doing?.let { p ->
            p.progress = p.seconds - a.doingLeft
            a.doing = null
            a.resume = p
        }
        if (a.path.isNotEmpty()) {
            a.resumeWalk = a.path
            a.resumeThen = a.afterArrive
            a.afterArrive = null
        }
        a.path = emptyList()
        a.dashVel = null
        a.innocent = INNOCENT_SECONDS
        a.anim = "innocent"
    }

    open fun howl() {
        if (over) return
        husky.anim = "howl"
        emit(Ev.Sfx("howl", husky.at))
    }

    open fun sniff() {}

    fun switch() {
        if (roe == null || over) return
        active = if (active == Who.HUSKY) Who.ROE else Who.HUSKY
    }

    // ---- Moving and doing ----

    protected fun go(a: Animal, to: P, then: () -> Unit) {
        val path = floor.path(a.at, to)
        if (path.isEmpty() && a.at.dist(to) > 0.05) {
            say(noWay())
            return
        }
        a.hiddenIn = null
        a.resume = null
        a.resumeWalk = emptyList()
        a.resumeThen = null
        a.path = path
        a.afterArrive = then
        // Already standing there: no walk will ever arrive, so do it now.
        if (path.isEmpty()) {
            a.afterArrive = null
            then()
        }
    }

    protected open fun noWay() = "那里过不去。"

    protected fun start(a: Animal, p: Prop) {
        if (p.kind != Kind.TRAIL) a.trailing = null
        // Taking up something else: what it had in its mouth stays where it stands.
        a.carrying?.let { if (it !== p) drop(a, it, a.at) }
        a.heading = headingOf(p.at - a.at)
        a.doing = p
        a.doingLeft = max(0.2, p.seconds - p.progress)
        a.anim = animFor(p, a)
    }

    protected open fun animFor(p: Prop, a: Animal) = when (p.kind) {
        Kind.BITE, Kind.TRAIL, Kind.CARRY -> "tug"
        Kind.EAT -> "tug"
        Kind.PUSH -> "walk"
        Kind.DIG -> "dig"
        Kind.CLEAN -> "dig"
        Kind.LEDGE -> "jump"
        Kind.HIDE -> "sit"
        else -> "idle"
    }

    private fun drop(a: Animal, c: Prop, at: P, place: Prop? = null) {
        a.carrying = null
        c.pos = at
        c.hidden = place?.id?.let { hidesIn(it) } ?: false
        onDropped(c, place)
        emit(Ev.Sfx("drop", at))
    }

    protected open fun hidesIn(place: String) = false
    protected open fun onDropped(c: Prop, place: Prop?) {}

    /** The deed on [p] is finished. Levels add their own effects. */
    protected open fun finish(a: Animal, p: Prop) {
        when (p.kind) {
            Kind.CARRY -> { a.carrying = p; p.hidden = false }
            Kind.TRAIL -> { a.trailing = p; p.done = true }
            Kind.PUSH -> { p.goal?.let { p.pos = it; p.at = it }; p.done = true }
            Kind.HIDE -> { a.hiddenIn = p; a.anim = "sit" }
            Kind.GATE -> Unit
            else -> { p.done = true; p.count++ }
        }
        emit(Ev.Did(p.id, p.kind))
    }

    // ---- The clock ----

    /** What happened, including what the commands since the last step made happen (a note, 「收工」). */
    fun step(dt0: Double): List<Ev> {
        var left = dt0
        while (left > 1e-9 && !over) {
            val dt = min(left, STEP)
            tick(dt)
            left -= dt
        }
        return events.toList().also { events.clear() }
    }

    private fun tick(dt: Double) {
        time += dt
        for (a in listOfNotNull(husky, roe)) moveAnimal(a, dt)
        queued?.let { q ->
            if (!animal.busy) {
                queued = null
                q()
            }
        }
        ownerTick(dt)
        rules(dt)
        // Caught: the owner looks and sees mischief.
        val a = husky
        if (owner.present && looks() && a.innocent <= 0 && a.hiddenIn == null) {
            val bad = a.doing?.mischief == true || a.dashVel != null || a.trailing != null || a.carrying?.mischief == true
            if (bad && sees(owner.at, owner.heading, eyeHalf, eyeRange, a.at, walls)) caught("主人看见了！")
        }
        for (g in goals) if (!g.met && g.check(this)) {
            g.met = true
            emit(Ev.GoalMet(g.id, g.text))
        }
        val main = goals.filter { it.tier == Task.MAIN }.all { it.met } && extraWin()
        if (main && mainAt < 0) {
            mainAt = time
            if (!endsOnMain && goals.any { !it.met }) say("主线完成！还可以挑战更难的任务，或者点「收工」。")
        }
        when {
            main && (endsOnMain || goals.all { it.met }) -> win()
            time >= limit -> if (main) win() else lose(timeOut())
        }
    }

    protected open fun extraWin() = true

    /** When the main goals were all met (-1 until then). */
    var mainAt = -1.0; private set
    /** Whether the level ends as soon as the main goals are met (its story ends there). */
    protected open val endsOnMain = false

    /** The player calls it a day once the main goals are met. */
    fun wrapUp() {
        if (!over && mainAt >= 0) win()
    }

    private fun win() {
        over = true
        won = true
        emit(Ev.Won)
    }
    protected open fun timeOut() = "时间到了，主人回来了！"

    /** How far and how wide the owner sees (the fan drawn on the floor is the same). */
    open val eyeRange = EYE_RANGE
    open val eyeHalf = EYE_HALF

    /** Whether the owner is looking at the room now. */
    open fun looks() = owner.looking > 0

    private fun moveAnimal(a: Animal, dt: Double) {
        if (a.innocent > 0) {
            a.innocent -= dt
            if (a.innocent <= 0) a.anim = "idle"
            return
        }
        // Back to what it was at, once he has stopped looking.
        if (owner.warning <= 0 && owner.looking <= 0 && !a.busy) {
            a.resume?.let { r ->
                a.resume = null
                start(a, r)
            }
            if (a.resumeWalk.isNotEmpty()) {
                a.path = a.resumeWalk
                a.afterArrive = a.resumeThen
                a.resumeWalk = emptyList()
                a.resumeThen = null
            }
        }
        if (a.frozen > 0) {
            a.frozen -= dt
            if (a.frozen <= 0) a.anim = "idle"
            return
        }
        val dv = a.dashVel
        if (dv != null) {
            val next = a.at + dv * dt
            for (p in props.values) if (p.knockable && !p.knocked && p.pos.dist(next) < p.size * 0.6 + 0.04) {
                p.knocked = true
                p.done = true
                a.dashHits++
                emit(Ev.Knocked(p.id))
                emit(Ev.Sfx("crash", p.pos))
                onKnocked(p, a)
            }
            a.dashLeft -= dt
            if (!floor.walkable(next) || a.dashLeft <= 0) {
                a.dashVel = null
                a.anim = "idle"
                onDashEnd(a)
            } else a.at = next
            return
        }
        val d = a.doing
        if (d != null) {
            a.doingLeft -= dt
            if (a.doingLeft <= 0) {
                a.doing = null
                a.anim = "idle"
                d.progress = 0.0
                finish(a, d)
                val wanted = pending
                if (wanted != null && unmet(wanted) == null && !a.busy) {
                    pending = null
                    tapProp(wanted.id)
                }
            }
            return
        }
        if (a.path.isNotEmpty()) {
            val target = a.path[0]
            val delta = target - a.at
            val speed = if (a.who == Who.ROE) ROE_SPEED else RUN_SPEED
            val stepLen = speed * dt
            val want = headingOf(delta)
            a.heading = turnToward(a.heading, want, dt * TURN_RATE)
            a.anim = if (a.carrying != null || a.trailing != null) "walk" else "run"
            // It turns round first, then runs: never off backward or sideways.
            if (abs(angleBetween(a.heading, want)) > TURN_FIRST) return
            if (delta.len() <= stepLen) {
                a.at = target
                a.path = a.path.drop(1)
                if (a.path.isEmpty()) {
                    a.anim = "idle"
                    val then = a.afterArrive
                    a.afterArrive = null
                    then?.invoke()
                }
            } else {
                a.at = a.at + delta.norm() * stepLen
                stepped(a)
            }
            // In the mouth: past the tip of the nose, not inside the head (v0.2).
            a.carrying?.let { it.pos = a.at + dirOf(a.heading) * CARRY_AHEAD }
        }
    }

    protected open fun stepped(a: Animal) {}
    protected open fun onKnocked(p: Prop, a: Animal) {}
    protected open fun onDashEnd(a: Animal) {}

    /** The owner's own behavior (each level). */
    protected abstract fun ownerTick(dt: Double)

    /** The level's own rules each slice (the cat, the siren, the ice). */
    protected open fun rules(dt: Double) {}

    /**
     * A turn-around schedule for an owner busy at something: back turned for a random while, a
     * warning (his humming stops), then looking at the room for [lookFor] seconds.
     */
    var nextTurn = 4.0; protected set
    protected fun busyOwner(dt: Double, workHeading: Double, minBack: Double, maxBack: Double, lookFor: Double, warn: Double = WARN_SECONDS) {
        val o = owner
        when {
            o.looking > 0 -> {
                o.looking -= dt
                o.heading = turnToward(o.heading, headingOf(husky.at - o.at), dt * 6)
                o.anim = "look"
                if (o.looking <= 0) {
                    nextTurn = dice.range(minBack, maxBack)
                    o.anim = "idle"
                }
            }
            o.warning > 0 -> {
                o.warning -= dt
                if (o.warning <= 0) o.looking = lookFor
            }
            else -> {
                o.heading = turnToward(o.heading, workHeading, dt * 6)
                nextTurn -= dt
                if (nextTurn <= 0) {
                    o.warning = warn
                    emit(Ev.Warn(warn))
                }
            }
        }
    }

    private var lastCaught = -10.0

    fun caught(why: String) {
        // One telling-off at a time: nothing counts again for a few seconds.
        if (time - lastCaught < 3.0) return
        lastCaught = time
        strikes++
        val a = husky
        a.doing?.progress = 0.0
        a.doing = null
        a.resume = null
        a.resumeWalk = emptyList()
        a.resumeThen = null
        queued = null
        a.dashVel = null
        a.trailing = null
        a.carrying?.let { it.pos = it.at; a.carrying = null }
        a.path = emptyList()
        a.anim = "caught"
        a.frozen = 1.2
        emit(Ev.Caught(why))
        emit(Ev.Sfx("caught", a.at))
        if (strikes >= MAX_STRIKES) lose("被抓了 $MAX_STRIKES 次，二哈被关进了阳台！")
    }

    protected fun lose(why: String) {
        if (over) return
        over = true
        won = false
        emit(Ev.Lost(why))
    }

    protected fun emit(e: Ev) = events.add(e)
    protected fun say(t: String) = emit(Ev.Said(t))

    /** Stars: one for the main goals, two also for every challenge goal, three also for the expert ones (no hint). */
    fun stars(): Int {
        if (!won) return 0
        val ch = goals.filter { it.tier == Task.CHALLENGE }.all { it.met }
        val ex = goals.filter { it.tier == Task.EXPERT }.all { it.met }
        return if (ch && ex && hints == 0) 3 else if (ch) 2 else 1
    }

    /** A hint: the first main goal not met yet. */
    fun hint(): String {
        hints++
        return goals.firstOrNull { it.tier == Task.MAIN && !it.met }?.let { "提示：${it.text}。${hintFor(it.id)}" } ?: "主线都完成了！"
    }

    protected open fun hintFor(goal: String) = ""

    companion object {
        const val STEP = 0.05
        /** A carried thing hangs this far ahead of the dog's middle (its nose is about 0.077 ahead). */
        const val CARRY_AHEAD = 0.095
        const val RUN_SPEED = 0.32
        const val ROE_SPEED = 0.28
        const val DASH_SPEED = 0.9
        /** A little over the warning and his look together. */
        const val INNOCENT_SECONDS = 4.0
        const val WARN_SECONDS = 1.5
        const val EYE_HALF = 0.95
        const val EYE_RANGE = 1.6
        const val MAX_STRIKES = 3
        const val FURNITURE_GAP = 0.07
        const val TURN_RATE = 14.0
        const val TURN_FIRST = 0.5
        /** How near a pinch on the floor counts as the thing (a little over the ray's waver at the far wall). */
        const val MAGNET = 0.16
        /** How near the line of sight to a pinch on the floor a thing must be to be what was meant (beyond its box). */
        const val ALONG = 0.04
        const val ON_FURNITURE = 0.06
        const val RUN_UP = 0.22
    }
}

/** Shorthand for a footprint around [at]. */
fun footprint(at: P, hx: Double, hz: Double) = Block(at.x, at.z, hx, hz)
