package com.vrgames.husky.game

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vrgames.husky.core.Ev
import com.vrgames.husky.core.Forest
import com.vrgames.husky.core.Kitchen
import com.vrgames.husky.core.Kind
import com.vrgames.husky.core.Level
import com.vrgames.husky.core.Levels
import com.vrgames.husky.core.Living
import com.vrgames.husky.core.P
import com.vrgames.husky.core.Solver
import com.vrgames.husky.core.Who
import com.vrgames.husky.core.Yard
import com.vrgames.husky.render.DioramaView
import com.vrgames.husky.render.Music
import com.vrgames.husky.render.Sfx
import com.vrgames.husky.render.Sound

/**
 * State the stage, its panels and both windows share (one process). [level] is the level being
 * played; the frame loop steps it and turns its events into notes, sounds and puffs. [progress]:
 * best stars per level and the album's photos (saved).
 */
object Game {
    private const val PREFS = "husky"
    private const val TAG = "HuskyGame"

    enum class Phase { CARD, INTRO, PLAYING, RESULT, DREAM }

    val lock = Any()
    private var prefs: android.content.SharedPreferences? = null
    val best = LinkedHashMap<String, Int>()
    val album = LinkedHashSet<String>()
    var level: Level? = null; private set
    var levelId by mutableStateOf(Levels.IDS[0]); private set
    var phase by mutableStateOf(Phase.CARD)
    var version by mutableIntStateOf(0); private set
    var note by mutableStateOf<String?>(null)
    var noteVersion by mutableIntStateOf(0); private set
    /** The next pinch on the floor or a thing sends the husky dashing there. */
    var dashMode by mutableStateOf(false)
    /** 狗狗视角 (v0.2): the room grown round the dog, the player right behind it; the button toggles it. */
    var dogView by mutableStateOf(false)

    fun toggleDogView() {
        dogView = !dogView
        version++
    }
    /** Seconds the ring at the dog's feet still shows that the pinch reached it. */
    var dogMark = 0.0; private set
    private const val DOG_MARK_SECONDS = 4.0
    private const val ANIMAL_NEAR = 0.1
    private var lastSecond = -1
    var menuOpen by mutableStateOf(false)
    var confirmRestart by mutableStateOf(false)
    var musicOn by mutableStateOf(true); private set
    var trophiesBefore = 0
    var newPhotos = ArrayList<String>()
    var music: Music? = null
    var sound: Sound? = null
    var view: DioramaView? = null
    var solver: Solver? = null
    var dreamTime = 0.0

    var window by mutableStateOf<String?>(null); private set
    private val windowCopies = HashMap<String, Any>()
    var closeWindow: (String) -> Unit = {}

    fun windowShown(id: String, copy: Any? = null) {
        if (copy != null) windowCopies[id] = copy
        window = id
    }

    fun windowGone(id: String, copy: Any? = null) {
        if (copy != null && windowCopies[id] !== copy) return
        windowCopies.remove(id)
        if (window == id) window = null
    }

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { p ->
            musicOn = p.getBoolean("music", true)
            p.getString("best", null)?.split(",")?.forEach { e -> e.split(":").takeIf { it.size == 2 }?.let { (k, v) -> if (k in Levels.IDS) v.toIntOrNull()?.let { best[k] = it.coerceIn(0, 3) } } }
            p.getString("album", null)?.split(",")?.filter { it in PHOTOS.keys }?.let { album += it }
        }
        AutoPilot.register(context)
    }

    val stars get() = Levels.IDS.sumOf { best[it] ?: 0 }
    val maxStars get() = Levels.IDS.size * 3
    val bronze get() = Levels.IDS.all { (best[it] ?: 0) > 0 }
    val silver get() = bronze && stars * 2 >= maxStars
    val gold get() = bronze && stars == maxStars
    val trophies get() = if (gold) 3 else if (silver) 2 else if (bronze) 1 else 0
    fun open(id: String) = Levels.IDS.indexOf(id) <= Levels.IDS.indexOfFirst { (best[it] ?: 0) == 0 }.let { if (it < 0) Levels.IDS.size else it }

    fun say(text: String?) {
        note = text
        noteVersion++
    }

    fun changed() {
        version++
    }

    /** From the card: the first level not cleared (or the last). */
    fun start() = intro(Levels.IDS.firstOrNull { (best[it] ?: 0) == 0 } ?: Levels.IDS.last())

    fun intro(id: String) {
        if (!open(id)) {
            say("这一关还没开放：先过前面的。")
            return
        }
        levelId = id
        synchronized(lock) { level = Levels.make(id) }
        solver = null
        dashMode = false
        phase = Phase.INTRO
        version++
    }

    fun begin() {
        if (level == null) intro(levelId)
        phase = Phase.PLAYING
        say(level?.intro?.substringBefore("！")?.plus("！"))
        sound?.play(Sfx.START)
        version++
    }

    fun retry() = intro(levelId)

    fun next() {
        val i = Levels.IDS.indexOf(levelId)
        intro(Levels.IDS.getOrElse(i + 1) { Levels.IDS.last() })
    }

    // ---- Commands from the stage ----

    fun tapTile(p: P, eye: com.vrgames.husky.core.Vec3? = null) = act { l ->
        dogMark = 0.0
        val roe = l.roe
        when {
            dashMode && l.active == Who.HUSKY -> {
                l.dash(p - l.husky.at, 1.0)
                dashMode = false
            }
            // He is about to look: a pinch by the dog is for the dog, whatever else is near.
            l.owner.present && l.owner.warning > 0 && l.husky.at.dist(p) < ANIMAL_NEAR -> tapAnimal("husky")
            // A thing near the pinch comes first; otherwise a near miss on an animal is a pinch on it.
            l.aimedAt(p, eye) != null -> l.tapAt(p, eye)
            roe != null && roe.at.dist(p) < ANIMAL_NEAR -> tapAnimal("roe")
            l.husky.at.dist(p) < ANIMAL_NEAR -> tapAnimal("husky")
            else -> l.tapAt(p)
        }
    }

    fun tapProp(id: String) = act { l ->
        dogMark = 0.0
        val p = l.props[id] ?: return@act
        // The dash is for knocking things over: pinch the dog, then a pillow, and it bites the pillow.
        if (dashMode && l.active == Who.HUSKY && p.kind == Kind.KNOCK) {
            l.dash(p.pos - l.husky.at, 1.0)
        } else l.tapProp(id)
        dashMode = false
    }

    fun tapAnimal(who: String) = act { l ->
        when {
            who == "roe" && l.active != Who.ROE -> if (!l.pinchedBeside(l.roe!!)) { l.switch(); dashMode = false; say("现在操作傻狍子。") }
            who == "husky" && l.active != Who.HUSKY -> if (!l.pinchedBeside(l.husky)) { l.switch(); say("现在操作二哈。") }
            who == "husky" -> {
                dogMark = DOG_MARK_SECONDS
                l.pinchDog()
            }
            who == "roe" -> l.pinchDog()
        }
    }

    /** The 「冲刺」 button: the next pinch on the floor is where the dog charges. */
    fun dashButton() = act { l ->
        if (l.active != Who.HUSKY) return@act
        dashMode = !dashMode
        if (dashMode) say("冲刺：捏一下要冲向的地方（或者捏住二哈往后拉，松手）。")
    }

    /** A slingshot pull on the husky: [pull] is how far the hand went (diorama frame). */
    fun pulled(pull: P) = act { l ->
        val len = pull.len()
        if (len < 0.03) return@act
        l.dash(pull * -1.0, (len / 0.2).coerceAtMost(1.0))
        dashMode = false
    }

    fun innocent() = act { it.beInnocent() }
    fun howl() = act { it.howl() }
    fun sniff() = act { it.sniff() }
    fun switch() = act { it.switch(); dashMode = false }
    fun wrapUp() = act { it.wrapUp() }
    fun hint() = act { say(it.hint()) }

    private inline fun act(f: (Level) -> Unit) {
        val l = level ?: return
        if (phase != Phase.PLAYING) return
        synchronized(lock) { f(l) }
        version++
    }

    // ---- The frame ----

    /** Steps the level and handles its events (main thread). */
    fun tick(dt0: Double) {
        if (dogMark > 0) dogMark -= dt0
        val dt = dt0 * timeScale
        if (phase == Phase.DREAM) {
            dreamTime += dt
            return
        }
        val l = level ?: return
        if (phase != Phase.PLAYING) return
        val evs = synchronized(lock) {
            solver?.update(dt)
            l.step(dt)
        }
        deedSounds(l, dt)
        var changed = false
        for (e in evs) {
            changed = true
            when (e) {
                is Ev.Said -> say(e.text)
                is Ev.Caught -> { say(e.why); sound?.play(Sfx.CAUGHT) }
                is Ev.Warn -> sound?.play(Sfx.WARN)
                is Ev.GoalMet -> { sound?.play(Sfx.GOAL); if (note == null) say("完成：${e.text}") }
                is Ev.Did -> {
                    val at = l.props[e.prop]?.pos ?: l.husky.at
                    sound?.play(
                        when (e.kind) {
                            Kind.BITE -> Sfx.TEAR
                            Kind.EAT -> Sfx.CHOMP
                            Kind.CARRY, Kind.TRAIL -> Sfx.GRAB
                            Kind.PUSH -> Sfx.SCRAPE
                            Kind.DIG -> Sfx.DIG
                            Kind.CLEAN -> Sfx.SWEEP
                            else -> Sfx.DID
                        },
                        at,
                    )
                    view?.deedDone(l.props[e.prop])
                    if (e.prop == "pillow") { view?.puff(l.props["pillow"]!!.at, "feathers"); photo("p1_sideeye") }
                    if (l is Living && l.trail.length > 0.6) photo("p2_paper")
                }
                is Ev.Knocked -> {
                    // What fell decides what is heard and seen: glass and water, a heavy thump, a cloud of flour or snow.
                    val at = l.props[e.prop]?.pos ?: P(0.0, 0.0)
                    val model = l.props[e.prop]?.model
                    when (model) {
                        "vase" -> { sound?.play(Sfx.SHATTER, at); sound?.play(Sfx.SPLASH, at); view?.puff(at, "shards"); view?.puff(at, "water") }
                        "books", "snowman", "plant" -> { sound?.play(Sfx.THUD, at); view?.puff(at, if (model == "snowman") "snow" else if (model == "plant") "soil" else "stars") }
                        "flour" -> { sound?.play(Sfx.THUD, at); view?.puff(at, "flour") }
                        else -> { sound?.play(Sfx.CRASH, at); view?.puff(at, if (l is Yard) "snow" else "stars") }
                    }
                }
                is Ev.Sfx -> sfx(e.name, e.at)
                Ev.Won -> finish(true)
                is Ev.Lost -> { say(e.why); finish(false) }
            }
        }
        if (l is Kitchen && l.framed) photo("p3_flourcat")
        if (l.id == "bath" && l.husky.hiddenIn != null && l.time > 30) photo("p4_bath")
        if (l is Yard && l.howls > 0) photo("p5_howl")
        if (l is Forest && l.freezes > 0) photo("p7_flare")
        if (l is Forest && l.dug) photo("p6_snow")
        // The clock on the panel, and the buttons of the moment (he has stopped looking).
        val second = l.time.toInt()
        if (changed || second != lastSecond) version++
        lastSecond = second
    }

    private var deedClock = 0.0

    /** While a deed goes on, its sound comes again and again: tearing, chewing, scraping, digging, sweeping (v0.2). */
    private fun deedSounds(l: com.vrgames.husky.core.Level, dt: Double) {
        val p = l.husky.doing ?: run { deedClock = 0.0; return }
        val (sfx, every) = when (p.kind) {
            Kind.BITE -> Sfx.TEAR to 0.55
            Kind.EAT -> Sfx.CHOMP to 0.35
            Kind.PUSH -> Sfx.SCRAPE to 0.6
            Kind.DIG -> Sfx.DIG to 0.45
            Kind.CLEAN -> Sfx.SWEEP to 0.5
            else -> return
        }
        deedClock -= dt
        if (deedClock <= 0) {
            deedClock = every
            sound?.play(sfx, p.pos)
        }
    }

    private fun sfx(name: String, at: P) {
        val s = when (name) {
            "whoosh" -> Sfx.WHOOSH
            "howl" -> Sfx.HOWL
            "crash" -> Sfx.CRASH
            "drop" -> Sfx.DROP
            "door" -> Sfx.DOOR
            "feathers" -> Sfx.POP
            "bone" -> Sfx.GOAL
            "siren" -> Sfx.SIREN
            "sniff" -> Sfx.SNIFF
            "crack" -> Sfx.CRACK
            "log" -> Sfx.CRASH
            "caught" -> Sfx.CAUGHT
            else -> null
        } ?: return
        sound?.play(s, at)
    }

    private fun photo(id: String) {
        if (album.add(id)) {
            newPhotos += id
            save()
            sound?.play(Sfx.CAMERA)
            say("咔嚓！相册里多了一张：${PHOTOS[id]}")
        }
    }

    private fun finish(won: Boolean) {
        val l = level ?: return
        trophiesBefore = trophies
        if (won) {
            val s = l.stars()
            if (s > (best[l.id] ?: 0)) best[l.id] = s
            if (silver) photo("p8_family")
        }
        save()
        phase = Phase.RESULT
        sound?.play(if (won) Sfx.WIN else Sfx.LOSE)
        if (trophies > trophiesBefore) sound?.play(Sfx.GOAL)
        Log.i(TAG, "finish ${l.id} won=$won stars=${l.stars()} time=${"%.1f".format(l.time)} strikes=${l.strikes}")
        version++
    }

    /** The gold trophy's ending: the husky's dream on the aurora snowfield. */
    fun dream() {
        if (!gold && !AutoPilot.debug) return
        dreamTime = 0.0
        phase = Phase.DREAM
        say("二哈睡着了，梦见自己成了雪橇犬之王……")
        version++
    }

    fun toCard() {
        phase = Phase.CARD
        version++
    }

    private fun save() {
        prefs?.edit()?.putString("best", best.entries.joinToString(",") { "${it.key}:${it.value}" })
            ?.putString("album", album.joinToString(","))?.apply()
    }

    fun restart() {
        best.clear()
        album.clear()
        save()
        level = null
        phase = Phase.CARD
        version++
    }

    fun toggleMusic() {
        musicOn = !musicOn
        music?.muted = !musicOn
        prefs?.edit()?.putBoolean("music", musicOn)?.apply()
    }

    fun grant(stars: Int) {
        for (id in Levels.IDS) best[id] = stars
        save()
        version++
    }

    /** Debug: the level plays itself (the unit tests' solver). */
    fun autoplay() {
        val l = level ?: return
        solver = Solver(l)
    }

    val PHOTOS = linkedMapOf(
        "p1_sideeye" to "满地羽毛，一脸无辜", "p2_paper" to "卫生纸拉满屋", "p3_flourcat" to "面粉扣了猫一身",
        "p4_bath" to "床底下的两只蓝眼睛", "p5_howl" to "和警笛一起嚎", "p6_snow" to "傻狍子把头埋进雪里",
        "p7_flare" to "屁股炸毛的傻狍子", "p8_family" to "全家福（银奖杯）",
    )

    // Debug hooks (AutoPilot).
    /** What the last pinch reached, on the panel: an answer to 「did that register?」. */
    var lastPinch by mutableStateOf("")

    /** Remembers what entity [id] (a tile, a thing, an animal) is, for the panel. */
    fun pinched(id: String) {
        val f = id.split(":")
        lastPinch = when (f[0]) {
            "tile" -> "地板"
            "prop" -> level?.props?.get(f[1])?.label ?: ""
            "husky" -> "二哈"
            "roe" -> "傻狍子"
            else -> ""
        }
    }

    /** The test hook: what a pinch on an entity does, and how fast the game runs. */
    var debugTap: ((String) -> Unit)? = null
    var timeScale = 1.0
    var debugOpen by mutableStateOf<String?>(null)
    var debugView by mutableStateOf<String?>(null)
    var faceRequest by mutableIntStateOf(0)
}
