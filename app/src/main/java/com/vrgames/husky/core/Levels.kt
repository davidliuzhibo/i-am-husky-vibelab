package com.vrgames.husky.core

import kotlin.math.max

/**
 * The six levels. The diorama is [Level.w]×[Level.d] meters (x right, z toward the player); the
 * husky is about 15 cm tall on it, the owner 36 cm. Every level is deterministic (its own dice).
 */
object Levels {
    val IDS = listOf("living", "kitchen", "bath", "yard", "forest", "home")
    fun make(id: String): Level = when (id) {
        "living" -> Living()
        "kitchen" -> Kitchen()
        "bath" -> Bath()
        "yard" -> Yard()
        "forest" -> Forest()
        else -> Home()
    }
    val TITLES = mapOf("living" to "客厅", "kitchen" to "厨房", "bath" to "洗澡日", "yard" to "东北小院", "forest" to "林子 · 傻狍子", "home" to "回家")
}

/** The toilet roll's paper trail: where it lies, and how long it is. */
class Trail {
    val points = ArrayList<P>()
    val length get() = points.zipWithNext().sumOf { (a, b) -> a.dist(b) }
    fun add(p: P) {
        if (points.isEmpty() || points.last().dist(p) > 0.02) points += p
    }
}

// ---------------------------------------------------------------- 1 · 客厅

class Living : Level("living", "客厅", "主人上班去了！三分钟后他就回来：拆沙发上的靠垫、把卫生纸拉满屋、偷吃茶几上的包子、把遥控器藏进沙发缝。", 1.6, 1.0, 180.0) {
    val trail = Trail()
    var bestDash = 0
    private var returning = false

    init {
        prop(Prop("sofa", Kind.SOLID, P(0.0, -0.37), "sofa", 0.3, block = footprint(P(0.0, -0.37), 0.15, 0.08), tall = true, label = "沙发"))
        prop(Prop("pillow", Kind.BITE, P(-0.07, -0.33), "pillow", 0.08, spot = P(-0.07, -0.22), seconds = 2.0, label = "靠垫"))
        prop(Prop("sofagap", Kind.DROP, P(0.08, -0.3), "", 0.06, spot = P(0.08, -0.22), mischief = false, label = "沙发缝"))
        prop(Prop("teatable", Kind.SOLID, P(0.0, -0.02), "teatable", 0.24, block = footprint(P(0.0, -0.02), 0.12, 0.07), label = "茶几"))
        prop(Prop("stool", Kind.PUSH, P(0.48, 0.22), "stool", 0.07, spot = P(0.52, 0.27), seconds = 2.0, goal = P(0.17, 0.02), mischief = false, label = "小凳子"))
        prop(Prop("baozi", Kind.EAT, P(0.06, -0.02), "baozi", 0.06, spot = P(0.2, 0.06), seconds = 2.0, needs = "stool", label = "包子", lift = 0.065))
        prop(Prop("remote", Kind.CARRY, P(-0.06, 0.06), "remote", 0.04, spot = P(-0.06, 0.12), seconds = 0.8, label = "遥控器"))
        prop(Prop("tissue", Kind.TRAIL, P(-0.7, -0.38), "tissue", 0.05, spot = P(-0.62, -0.32), seconds = 1.2, label = "卫生纸"))
        prop(Prop("tv", Kind.SOLID, P(-0.7, 0.08), "tvstand", 0.3, block = footprint(P(-0.7, 0.08), 0.07, 0.16), yaw = 90.0, label = "电视柜"))
        prop(Prop("vase", Kind.KNOCK, P(0.36, 0.36), "vase", 0.06, knockable = true, label = "花瓶"))
        prop(Prop("books", Kind.KNOCK, P(0.5, 0.36), "books", 0.06, knockable = true, label = "一摞书"))
        prop(Prop("plant", Kind.KNOCK, P(0.64, 0.36), "plant", 0.07, knockable = true, label = "盆栽"))
        build(P(0.0, 0.3))
        owner.present = false
        owner.at = P(0.75, -0.45)
        goal("pillow", Task.MAIN, "拆沙发上的靠垫") { props["pillow"]!!.done }
        goal("trail", Task.MAIN, "卫生纸拉出 60 厘米") { trail.length >= TRAIL_ENOUGH }
        goal("baozi", Task.MAIN, "偷吃茶几上的包子") { props["baozi"]!!.done }
        goal("remote", Task.MAIN, "把遥控器藏进沙发缝") { props["remote"]!!.hidden }
        goal("fast", Task.CHALLENGE, "两分钟内完成主线") { mainAt in 0.0..120.0 }
        goal("combo", Task.EXPERT, "一次冲刺连撞三样东西") { bestDash >= 3 }
        goal("long", Task.EXPERT, "卫生纸一路拉到沙发前") { trail.points.lastOrNull()?.let { it.dist(P(0.0, -0.25)) < 0.15 } == true && trail.length > 0.9 }
    }

    private fun mainDone() = goals.filter { it.tier == Task.MAIN }.all { it.met }

    override fun needHint(p: Prop) = "茶几太高，先把小凳子推过去。"
    override fun hidesIn(place: String) = place == "sofagap"

    override fun stepped(a: Animal) {
        if (a.trailing != null) trail.add(a.at)
    }

    override fun finish(a: Animal, p: Prop) {
        super.finish(a, p)
        if (p.kind == Kind.TRAIL) {
            trail.add(p.at)
            say("叼住了卫生纸，拖着跑起来了！")
            trailRun(a)
        }
        if (p.kind == Kind.BITE) emit(Ev.Sfx("feathers", p.at))
    }

    /** Far across the room. */
    override fun trailRun(a: Animal) {
        val to = listOf(P(0.0, 0.3), P(0.3, 0.25), P(-0.4, 0.3)).maxByOrNull { it.dist(a.at) }!!
        go(a, to) { arrived(a) }
    }

    /** Paper enough, and the run over: the job is done and the roll is put down. */
    override fun arrived(a: Animal) {
        val roll = a.trailing ?: return
        if (trail.length < TRAIL_ENOUGH) return
        a.trailing = null
        roll.pos = a.at
        roll.retired = true
        say("卫生纸拉够长了，二哈松口放下了。")
        emit(Ev.Sfx("drop", a.at))
    }

    override fun why(a: Animal, p: Prop): String? = if (p.retired) "${p.label}已经搞定了。" else super.why(a, p)

    override fun onDashEnd(a: Animal) {
        bestDash = max(bestDash, a.dashHits)
        if (a.dashHits >= 3) say("连撞 ${a.dashHits} 样！")
    }

    override fun ownerTick(dt: Double) {
        // Away at work; comes home at the end.
        if (!returning && time > limit - 12) {
            returning = true
            say("楼道里有脚步声……主人快回来了！")
            emit(Ev.Sfx("door", owner.at))
        }
    }

    override fun hintFor(goal: String) = when (goal) {
        "trail" -> "卫生纸在左边卫生间门口，捏一下它：二哈叼住就会拖着跑。"
        "baozi" -> "捏包子：二哈会先把小凳子推到茶几边，再踩上去吃。"
        "remote" -> "捏遥控器叼起来，再捏沙发上的缝。"
        else -> ""
    }

    companion object {
        const val TRAIL_ENOUGH = 0.6
    }
}

// ---------------------------------------------------------------- 2 · 厨房

class Kitchen : Level("kitchen", "厨房", "主人背对着你在包饺子，时不时回头看一眼。趁他转身：偷 5 个饺子、把擀面杖藏去阳台、让猫背打翻面粉的锅。他回头前会停下哼歌，赶紧点「装无辜」！", 1.6, 1.0, 200.0) {
    val cat = Animal(Who.HUSKY, P(-0.3, 0.2))
    private val catRoute = listOf(P(-0.55, -0.18), P(-0.3, 0.22), P(0.2, 0.3), P(-0.1, 0.0))
    private var catLeg = 0
    private var catWait = 0.0
    var dodges = 0
    var framed = false
    /** The dog is waiting by the flour for the cat to come. */
    private var lying = false

    init {
        prop(Prop("counter", Kind.SOLID, P(0.0, -0.42), "kitchen", 0.6, block = footprint(P(0.0, -0.42), 0.3, 0.07), tall = true, label = "灶台"))
        prop(Prop("lowtable", Kind.SOLID, P(0.3, -0.13), "lowtable", 0.14, block = footprint(P(0.3, -0.13), 0.07, 0.05), label = "小桌"))
        prop(Prop("dumplings", Kind.EAT, P(0.3, -0.13), "dumplings", 0.08, spot = P(0.3, -0.05), seconds = 1.3, label = "饺子"))
        prop(Prop("pin", Kind.CARRY, P(-0.2, -0.33), "pin", 0.1, spot = P(-0.2, -0.25), seconds = 1.6, label = "擀面杖"))
        prop(Prop("balcony", Kind.DROP, P(0.7, 0.32), "", 0.1, spot = P(0.62, 0.3), mischief = false, label = "阳台"))
        prop(Prop("shelf", Kind.SOLID, P(-0.66, -0.33), "shelf", 0.14, block = footprint(P(-0.66, -0.33), 0.07, 0.05), label = "架子"))
        prop(Prop("flour", Kind.KNOCK, P(-0.6, -0.25), "flour", 0.07, knockable = true, label = "面粉"))
        build(P(0.0, 0.3))
        owner.at = P(0.0, -0.32)
        owner.heading = 0.0
        goal("dumplings", Task.MAIN, "偷吃 5 个饺子") { props["dumplings"]!!.count >= 5 }
        goal("pin", Task.MAIN, "把擀面杖藏到阳台") { props["pin"]!!.let { it.hidden || it.pos.dist(P(0.7, 0.32)) < 0.12 } }
        goal("cat", Task.MAIN, "打翻面粉，让猫背锅") { framed }
        goal("careful", Task.CHALLENGE, "被抓不超过 1 次") { mainDone() && strikes <= 1 }
        goal("clean", Task.EXPERT, "一次都没被抓") { mainDone() && strikes == 0 }
        goal("dodge", Task.EXPERT, "两次在主人回头时装无辜躲过") { dodges >= 2 }
    }

    private fun mainDone() = goals.filter { it.tier == Task.MAIN }.all { it.met }

    override fun hidesIn(place: String) = place == "balcony"

    override fun finish(a: Animal, p: Prop) {
        super.finish(a, p)
        if (p.id == "dumplings") {
            p.done = p.count >= 5
            // It keeps eating by itself (「装无辜」 breaks off and it goes on afterward).
            if (p.count < 5) {
                say("第 ${p.count} 个！还要 ${5 - p.count} 个。")
                if (!commandWaiting) start(a, p)
            }
        }
    }

    override fun why(a: Animal, p: Prop): String? {
        if (p.id == "dumplings" && p.count < 5) return null
        return super.why(a, p)
    }

    /** The flour must fall on the cat: with the cat elsewhere the dog lies in wait beside it. */
    override fun knock(a: Animal, p: Prop) {
        if (cat.at.dist(p.at) < CAT_NEAR && owner.looking <= 0 && owner.warning <= 0) {
            charge(a, p)
            return
        }
        lying = true
        val stand = runUp(a, p) ?: return
        go(a, stand) { say("二哈趴在面粉旁边，等猫溜达过来……") }
    }

    override fun onKnocked(p: Prop, a: Animal) {
        if (p.id == "flour") {
            if (cat.at.dist(p.at) < 0.2) {
                framed = true
                say("面粉扣了猫一身！主人：“死猫，又是你！”")
            } else {
                say("面粉晃了晃，没倒。猫不在旁边，二哈可不想背锅：等猫走近再撞。")
                p.knocked = false
                p.done = false
            }
        }
    }

    override fun ownerTick(dt: Double) {
        val wasLooking = owner.looking > 0
        busyOwner(dt, 0.0, 3.0, 6.5, 2.0)
        if (!wasLooking && owner.looking > 0 && husky.innocent > 0) {
            dodges++
            say("主人：“嗯？”……二哈一脸无辜。")
        }
        owner.anim = if (owner.looking > 0) "look" else "idle"
    }

    override fun rules(dt: Double) {
        val flour = props["flour"]!!
        if (lying) {
            val a = husky
            when {
                flour.knocked -> lying = false
                a.path.isNotEmpty() || a.busy -> Unit
                a.at.dist(flour.pos) > 0.3 -> lying = false
                cat.at.dist(flour.pos) < CAT_NEAR && owner.looking <= 0 && owner.warning <= 0 -> {
                    lying = false
                    charge(a, flour)
                }
            }
        }
        // The cat strolls its round, pausing now and then.
        if (catWait > 0) { catWait -= dt; return }
        val t = catRoute[catLeg]
        val d = t - cat.at
        if (d.len() < 0.01) {
            catLeg = (catLeg + 1) % catRoute.size
            catWait = 2.5
        } else {
            cat.at = cat.at + d.norm() * minOf(d.len(), 0.12 * dt)
            cat.heading = headingOf(d)
        }
        cat.anim = if (catWait > 0) "idle" else "walk"
    }

    override fun hintFor(goal: String) = when (goal) {
        "dumplings" -> "捏饺子，二哈会一个接一个吃。主人停下哼歌就是要回头了：马上点「装无辜」或捏二哈，他转回去二哈会接着吃。"
        "cat" -> "捏面粉：二哈会趴在旁边等猫，猫一走近就撞过去。"
        else -> "叼起擀面杖，再捏右下角的阳台。"
    }

    companion object {
        const val CAT_NEAR = 0.2
    }
}

// ---------------------------------------------------------------- 3 · 洗澡日

class Bath : Level("bath", "洗澡日", "主人拿着毛巾满屋找你，要给你洗澡！躲进藏身处，用拖鞋和玩偶把他引开，坚持 2 分 30 秒，然后从门缝溜出去。同一个地方他找过一次，就会记住。", 1.6, 1.0, 230.0) {
    private val round = listOf("bed", "closet", "curtain", "sofaback")
    private var roundAt = 0
    /** Where he has found the dog before: he looks there first. */
    val known = LinkedHashSet<String>()
    var target: String? = null; private set
    /** The spot he checked last (he will not be back for a while), and the one his round goes to next. */
    var lastChecked: String? = null; private set
    val upcoming get() = round[roundAt % round.size]
    private var checking = 0.0
    private var lure: P? = null
    var lures = 0
    var survived = false
    var escaped = false

    init {
        prop(Prop("bed", Kind.HIDE, P(-0.45, -0.3), "bed", 0.4, spot = P(-0.45, -0.16), seconds = 0.8, mischief = false, label = "床底下"))
        prop(Prop("curtain", Kind.HIDE, P(0.7, -0.4), "curtain", 0.15, spot = P(0.66, -0.34), seconds = 0.8, mischief = false, label = "窗帘后面"))
        prop(Prop("closet", Kind.HIDE, P(0.2, -0.42), "closet", 0.2, spot = P(0.2, -0.32), seconds = 0.8, mischief = false, label = "衣柜里"))
        prop(Prop("sofaback", Kind.HIDE, P(0.55, 0.22), "sofa", 0.3, spot = P(0.55, 0.32), seconds = 0.8, mischief = false, label = "沙发后面"))
        prop(Prop("bedframe", Kind.SOLID, P(-0.45, -0.32), "", 0.4, block = footprint(P(-0.45, -0.32), 0.2, 0.12), tall = true))
        prop(Prop("sofa", Kind.SOLID, P(0.55, 0.2), "", 0.3, block = footprint(P(0.55, 0.2), 0.15, 0.06), tall = true))
        prop(Prop("tub", Kind.SOLID, P(-0.66, 0.3), "bathtub", 0.25, block = footprint(P(-0.66, 0.3), 0.12, 0.08), label = "浴缸"))
        prop(Prop("slippers", Kind.CARRY, P(-0.1, 0.1), "slippers", 0.07, spot = P(-0.1, 0.17), seconds = 0.6, mischief = false, label = "拖鞋"))
        prop(Prop("plush", Kind.CARRY, P(0.3, 0.05), "plush", 0.07, spot = P(0.3, 0.12), seconds = 0.6, mischief = false, label = "玩偶熊"))
        prop(Prop("door", Kind.DOOR, P(-0.15, 0.47), "", 0.1, spot = P(-0.15, 0.42), seconds = 0.5, mischief = false, label = "门缝"))
        build(P(0.0, 0.0))
        owner.at = P(-0.66, 0.15)
        goal("survive", Task.MAIN, "躲满 2 分 30 秒") { survived }
        goal("escape", Task.MAIN, "从门缝溜出去") { escaped }
        goal("careful", Task.CHALLENGE, "被抓不超过 1 次") { escaped && strikes <= 1 }
        goal("lures", Task.EXPERT, "用拖鞋和玩偶各引开他一次") { lures >= 2 }
        goal("clean", Task.EXPERT, "一次都没被抓") { escaped && strikes == 0 }
    }

    override fun extraWin() = escaped
    override val endsOnMain = true
    override val eyeRange = 0.36
    override val eyeHalf = 0.55

    override fun timeOut() = "主人找到你了，还是被抱去洗了澡……"

    override fun why(a: Animal, p: Prop): String? {
        if (p.kind == Kind.DOOR && !survived) return "门还关着：再坚持 ${(SURVIVE - time).toInt().coerceAtLeast(1)} 秒，门缝就开了。"
        return super.why(a, p)
    }

    override fun finish(a: Animal, p: Prop) {
        super.finish(a, p)
        when (p.kind) {
            Kind.HIDE -> if (p.id in known) say("他在这儿找到过你……他会先来这儿！")
            Kind.DOOR -> {
                escaped = true
                say("溜出去了！今天不洗澡！")
            }
            else -> Unit
        }
    }

    override fun onDropped(c: Prop, place: Prop?) {
        // A thing dropped where the owner can hear it: he goes to look.
        lure = c.pos
        if (c.count == 0) lures++
        c.count++
        say("“咚！”主人：“二哈？在那儿吗？”")
    }

    override fun ownerTick(dt: Double) {
        val o = owner
        if (time >= SURVIVE && !survived) {
            survived = true
            say("门缝开了！快溜出去！")
            emit(Ev.Sfx("door", P(-0.15, 0.47)))
        }
        if (checking > 0) {
            checking -= dt
            o.anim = "search"
            if (checking <= 0) {
                val t = target
                if (t != null && husky.hiddenIn?.id == t) {
                    known += t
                    caught("主人在${props[t]!!.label}找到了你！")
                }
                if (t != null) lastChecked = t
                target = null
            }
            return
        }
        if (o.path.isEmpty()) {
            val l = lure
            val next: P
            if (l != null) {
                lure = null
                target = null
                next = l
            } else {
                // If the dog hides where he found it before, he goes straight there; else his round.
                val hid = husky.hiddenIn?.id
                val pick = if (hid != null && hid in known) hid else round[roundAt++ % round.size]
                target = pick
                next = props[pick]!!.spot
            }
            o.path = floor.path(o.at, next)
            if (o.path.isEmpty()) checking = 1.0
        }
        val goal = o.path.firstOrNull() ?: return
        val d = goal - o.at
        o.heading = turnToward(o.heading, headingOf(d), dt * 5)
        o.anim = "walk"
        val step = OWNER_SPEED * dt
        if (d.len() <= step) {
            o.at = goal
            o.path = o.path.drop(1)
            if (o.path.isEmpty()) checking = 1.6
        } else o.at = o.at + d.norm() * step
        // Out in the open in front of him: caught.
        if (husky.hiddenIn == null && husky.innocent <= 0 && sees(o.at, o.heading, eyeHalf, eyeRange, husky.at, emptyList()) && husky.frozen <= 0) caught("被主人看见了！")
    }

    override fun looks() = false

    override fun hintFor(goal: String) = if (goal == "escape") "门缝在下边中间。" else "叼起拖鞋扔到远处，主人会去那边看；你躲去另一头。"

    companion object {
        const val SURVIVE = 150.0
        const val OWNER_SPEED = 0.16
    }
}

// ---------------------------------------------------------------- 4 · 东北小院

class Yard : Level("yard", "东北小院", "下雪了！刨出雪里埋的 3 根骨头（闻一闻就知道在哪）、远处警笛响时跟着嚎、拉着雪橇依次穿过 4 面小旗，最后撞翻雪人。", 1.6, 1.0, 220.0) {
    private val bones = setOf("m2", "m5", "m7")
    var found = 0
    var howls = 0
    var sniffs = 0
    var sirenAt = -100.0
    private val sirens = listOf(35.0, 90.0, 150.0, 195.0)
    var sledOn = false
    var gate = 0
    var sledStart = 0.0
    var sledTime = 0.0
    var scent: P? = null
    var scentLeft = 0.0

    init {
        prop(Prop("cottage", Kind.SOLID, P(0.0, -0.42), "cottage", 0.6, block = footprint(P(0.0, -0.42), 0.3, 0.07), tall = true, label = "屋子"))
        prop(Prop("doghouse", Kind.SOLID, P(-0.62, -0.25), "doghouse", 0.16, block = footprint(P(-0.62, -0.25), 0.07, 0.06), label = "狗窝"))
        val mounds = listOf(P(-0.5, 0.05), P(-0.28, 0.3), P(-0.05, -0.15), P(0.15, 0.18), P(0.36, -0.12), P(0.6, 0.22), P(0.68, -0.2))
        mounds.forEachIndexed { k, p -> prop(Prop("m${k + 1}", Kind.DIG, p, "mound", 0.07, spot = p + P(0.0, 0.06), seconds = 1.8, mischief = false, label = "雪堆")) }
        prop(Prop("sled", Kind.CARRY, P(-0.3, -0.18), "sled", 0.12, spot = P(-0.3, -0.1), seconds = 0.8, mischief = false, label = "雪橇"))
        val gates = listOf(P(0.0, 0.05), P(0.45, 0.05), P(0.45, 0.38), P(-0.1, 0.38))
        gates.forEachIndexed { k, p -> prop(Prop("g${k + 1}", Kind.GATE, p, "flag", 0.06, spot = p, seconds = 0.1, mischief = false, label = "第 ${k + 1} 面小旗")) }
        prop(Prop("snowman", Kind.KNOCK, P(0.72, 0.1), "snowman", 0.1, knockable = true, label = "雪人"))
        prop(Prop("fenceL", Kind.SOLID, P(-0.79, 0.1), "fence", 0.3, block = footprint(P(-0.79, 0.1), 0.01, 0.35), yaw = 90.0))
        build(P(-0.5, -0.1))
        owner.present = false
        goal("bones", Task.MAIN, "刨出 3 根骨头") { found >= 3 }
        goal("howl", Task.MAIN, "跟着警笛嚎两次") { howls >= 2 }
        goal("sled", Task.MAIN, "拉雪橇依次穿过 4 面小旗") { gate >= 4 }
        goal("snowman", Task.MAIN, "撞翻雪人") { props["snowman"]!!.knocked }
        goal("fastsled", Task.CHALLENGE, "雪橇 30 秒内跑完") { gate >= 4 && sledTime <= 30 }
        goal("nose", Task.EXPERT, "只闻一次就找齐骨头") { found >= 3 && sniffs <= 1 }
        goal("choir", Task.EXPERT, "跟着警笛嚎三次") { howls >= 3 }
    }

    override fun sniff() {
        if (over) return
        sniffs++
        val left = props.values.filter { it.id in bones && !it.done }
        val near = left.minByOrNull { it.at.dist(husky.at) }
        scent = near?.at
        scentLeft = 3.0
        husky.anim = "sit"
        say(if (near == null) "骨头都找到了。" else "闻到了！气味飘向那边……")
        emit(Ev.Sfx("sniff", husky.at))
    }

    override fun howl() {
        super.howl()
        if (time - sirenAt in 0.0..HOWL_WINDOW) {
            howls++
            sirenAt = -100.0
            say("嗷呜——！和警笛一个调！（第 $howls 次）")
        } else say("嗷呜？远处没有警笛……")
    }

    override fun finish(a: Animal, p: Prop) {
        when {
            p.kind == Kind.DIG -> {
                p.done = true
                if (p.id in bones) {
                    found++
                    say("刨出一根大骨头！（$found / 3）")
                    emit(Ev.Sfx("bone", p.at))
                } else say("这堆雪里什么也没有。")
                emit(Ev.Did(p.id, p.kind))
            }
            p.id == "sled" -> {
                sledOn = true
                gate = 0
                sledStart = time
                a.carrying = null
                say("套上雪橇了！捏小旗，二哈就拉着雪橇跑向下一面。")
                emit(Ev.Did(p.id, p.kind))
            }
            p.kind == Kind.GATE -> {
                val want = "g${gate + 1}"
                if (!sledOn) say("先套上雪橇。")
                else if (p.id != want) say("顺序不对：先去第 ${gate + 1} 面小旗。")
                else {
                    gate++
                    p.done = true
                    emit(Ev.Did(p.id, p.kind))
                    if (gate >= 4) {
                        sledTime = time - sledStart
                        sledOn = false
                        say("雪橇跑完了！用时 ${sledTime.toInt()} 秒。现在去撞雪人！")
                    }
                }
            }
            else -> super.finish(a, p)
        }
    }

    override fun why(a: Animal, p: Prop): String? = when {
        p.kind == Kind.GATE -> if (gate >= 4) "小旗都跑完了。" else null
        p.id == "sled" && gate >= 4 -> "雪橇已经跑完了。"
        else -> super.why(a, p)
    }

    /** Any flag means the next flag, and before the sled is on, the sled. */
    override fun redirect(p: Prop): Prop = when {
        gate >= 4 -> p
        p.kind == Kind.GATE && !sledOn -> props["sled"]!!
        (p.kind == Kind.GATE || p.id == "sled") && sledOn -> props["g${gate + 1}"]!!
        else -> p
    }

    /** Walking near a flag is only passing it, until the sled is on. */
    override fun magnetic(p: Prop) = p.kind != Kind.GATE || sledOn

    override fun pinchDog() {
        if (!over && time - sirenAt in 0.0..HOWL_WINDOW) howl() else super.pinchDog()
    }

    override fun ownerTick(dt: Double) = Unit

    override fun rules(dt: Double) {
        if (scentLeft > 0) {
            scentLeft -= dt
            if (scentLeft <= 0) scent = null
        }
        for (s in sirens) if (time - dt < s && time >= s) {
            sirenAt = time
            emit(Ev.Sfx("siren", P(0.9, -0.8)))
            say("远处响起了警笛！快点「嚎叫」，或者捏二哈！")
        }
    }

    override fun hintFor(goal: String) = when (goal) {
        "bones" -> "点「闻一闻」，气味会指向最近的骨头。"
        "howl" -> "听到警笛，点「嚎叫」或者捏二哈。"
        "sled" -> "捏雪橇套上，再捏小旗：捏哪一面，二哈都会跑向下一面。"
        else -> "捏雪人：二哈会冲过去撞翻它。"
    }

    companion object {
        const val HOWL_WINDOW = 5.0
    }
}

// ---------------------------------------------------------------- 5 · 林子

class Forest : Level("forest", "林子 · 傻狍子", "二哈跑丢在林子里，遇到了一只傻狍子。二哈会刨雪开路，傻狍子跳得高：换着操作，一起走到右边回家的小路。傻狍子一听到冰面咔嚓响就会愣住！", 1.6, 1.0, 240.0) {
    var freezes = 0
    private var iceTravel = 0.0
    var bridge = false
    var dug = false
    val stream = Block(0.1, 0.0, 0.05, 0.5)
    val snowbank = Block(-0.55, 0.22, 0.25, 0.03)
    val ice = Block(0.45, -0.25, 0.2, 0.15)

    init {
        prop(Prop("bank", Kind.DIG, P(-0.55, 0.22), "snowbank", 0.18, spot = P(-0.55, 0.14), seconds = 2.5, mischief = false, label = "雪堆"))
        prop(Prop("boulders", Kind.SOLID, P(-0.3, 0.36), "rocks", 0.14, block = Block(-0.3, 0.36, 0.02, 0.14), label = "石头"))
        prop(Prop("wall", Kind.SOLID, P(0.4, 0.2), "rocks", 0.3, block = Block(0.4, 0.2, 0.03, 0.3), tall = true, label = "石壁"))
        prop(Prop("rock", Kind.LEDGE, P(-0.05, -0.35), "rock", 0.15, spot = P(-0.05, -0.27), seconds = 1.2, mischief = false, label = "大石头"))
        prop(Prop("exit", Kind.DOOR, P(0.74, 0.3), "", 0.1, spot = P(0.72, 0.3), seconds = 0.3, mischief = false, label = "回家的小路"))
        prop(Prop("pine1", Kind.SOLID, P(-0.6, -0.3), "pine", 0.2, block = footprint(P(-0.6, -0.3), 0.05, 0.05), tall = true))
        prop(Prop("birch1", Kind.SOLID, P(0.75, -0.02), "birch", 0.2, block = footprint(P(0.75, -0.02), 0.03, 0.03), tall = true))
        prop(Prop("pine2", Kind.SOLID, P(-0.2, -0.05), "pine", 0.18, block = footprint(P(-0.2, -0.05), 0.05, 0.05), tall = true))
        build(P(-0.68, 0.0))
        roe = Animal(Who.ROE, P(-0.6, 0.4))
        rebuild()
        owner.present = false
        goal("home", Task.MAIN, "二哈和傻狍子都走到回家的小路") { at(husky) && at(roe!!) }
        goal("fast", Task.CHALLENGE, "两分半之内到家") { at(husky) && at(roe!!) && time <= 150 }
        goal("calm", Task.EXPERT, "傻狍子最多愣住一次") { at(husky) && at(roe!!) && freezes <= 1 }
    }

    private fun at(a: Animal) = a.at.dist(P(0.72, 0.3)) < 0.1
    override val endsOnMain = true

    /** Wanting to go on means what stands in the way: first the snowbank, then the river. */
    override fun redirect(p: Prop): Prop = when {
        (p.id == "exit" || p.id == "rock") && !dug -> props["bank"]!!
        p.id == "exit" && !bridge -> props["rock"]!!
        else -> p
    }

    /** Either animal may be sent to these: [choose] hands over to the one who can. */
    override fun why(a: Animal, p: Prop): String? = when (p.kind) {
        Kind.DIG -> super.why(husky, p)
        Kind.LEDGE -> super.why(roe!!, p)
        else -> super.why(a, p)
    }

    /** The dog digs, the roe deer jumps: pinching the thing hands over to the one who can. */
    override fun choose(p: Prop) {
        when (p.kind) {
            Kind.DIG -> if (active != Who.HUSKY) { active = Who.HUSKY; say("换成二哈：它会刨。") }
            Kind.LEDGE -> if (active != Who.ROE) { active = Who.ROE; say("换成傻狍子：它跳得高。") }
            else -> Unit
        }
    }

    override fun noWay() = when {
        !dug -> "傻狍子被雪堆困住了：捏雪堆，让二哈刨开。"
        !bridge -> "河上没有桥：捏大石头，让傻狍子跳上去。"
        else -> "那里过不去。"
    }

    /** The floor: the stream cut through but where the log lies, the snowbank until dug. */
    private fun rebuild() {
        val blocks = ArrayList<Block>()
        props.values.mapNotNullTo(blocks) { it.block }
        if (!bridge) blocks += stream else {
            blocks += Block(0.1, -0.29, 0.05, 0.21)
            blocks += Block(0.1, 0.33, 0.05, 0.17)
        }
        if (!dug) blocks += snowbank
        floor = Floor(w, d, blocks)
    }

    override fun finish(a: Animal, p: Prop) {
        when (p.id) {
            "bank" -> {
                dug = true
                p.done = true
                rebuild()
                say("二哈刨开了雪堆，后面的路通了！")
                emit(Ev.Did(p.id, p.kind))
            }
            "rock" -> {
                bridge = true
                p.done = true
                rebuild()
                say("傻狍子跳上石头，一蹬，枯树倒下来，搭成了过河的桥！")
                emit(Ev.Did(p.id, p.kind))
                emit(Ev.Sfx("log", P(0.1, 0.0)))
            }
            "exit" -> {
                // The one still out there takes over.
                val other = if (a.who == Who.ROE) husky else roe!!
                if (!at(other)) {
                    active = other.who
                    say("${if (a.who == Who.ROE) "傻狍子" else "二哈"}到了路口。现在换${if (other.who == Who.ROE) "傻狍子" else "二哈"}：再捏一下回家的小路。")
                }
            }
            else -> super.finish(a, p)
        }
    }

    override fun stepped(a: Animal) {
        if (a.who == Who.ROE && ice.has(a.at)) {
            iceTravel += 0.016
            if (iceTravel > 0.12) {
                iceTravel = 0.0
                emit(Ev.Sfx("crack", a.at))
                a.path = emptyList()
                a.afterArrive = null
                a.frozen = 2.0
                a.anim = "freeze"
                freezes++
                say("咔嚓！冰面一响，傻狍子愣住了，回头看了半天……")
            }
        }
    }

    override fun ownerTick(dt: Double) = Unit

    override fun hintFor(goal: String) = "傻狍子被雪堆困住了：先让二哈刨开雪堆。河上没有桥：换傻狍子，捏大石头跳上去。过河后石壁挡路，要从冰面绕过去：傻狍子走冰面会愣住，让它贴着最上边走。"
}

// ---------------------------------------------------------------- 6 · 回家

class Home : Level("home", "回家", "钥匙声！主人还有 90 秒到家：把羽毛、卫生纸、面粉脚印收拾掉，把猫推到花瓶碎片那儿。然后他会一处处检查——每次他回头看你，都要马上装无辜！", 1.6, 1.0, 170.0) {
    private val evidence = listOf("feathers", "paper", "prints", "shards")
    var phase = 0
    private var inspectAt = 0
    private var inspectT = 0.0
    var innocents = 0
    var catScene = false

    init {
        prop(Prop("sofa", Kind.SOLID, P(0.0, -0.37), "sofa", 0.3, block = footprint(P(0.0, -0.37), 0.15, 0.08), tall = true, label = "沙发"))
        prop(Prop("feathers", Kind.CLEAN, P(-0.2, -0.15), "feathers", 0.12, spot = P(-0.2, -0.08), seconds = 2.0, mischief = false, label = "一地羽毛"))
        prop(Prop("paper", Kind.CLEAN, P(-0.5, 0.1), "paper", 0.15, spot = P(-0.5, 0.18), seconds = 2.6, mischief = false, label = "卫生纸"))
        prop(Prop("prints", Kind.CLEAN, P(0.3, 0.0), "prints", 0.12, spot = P(0.3, 0.08), seconds = 2.0, mischief = false, label = "面粉脚印"))
        prop(Prop("shards", Kind.SOLID, P(0.55, 0.3), "shards", 0.08, label = "花瓶碎片"))
        prop(Prop("cat", Kind.PUSH, P(-0.6, -0.3), "cat", 0.1, spot = P(-0.6, -0.22), seconds = 2.2, goal = P(0.5, 0.24), mischief = false, label = "猫"))
        build(P(0.0, 0.25))
        owner.present = false
        owner.at = P(-0.75, 0.45)
        goal("pass", Task.MAIN, "熬过主人的检查（被识破少于 3 次）") { phase == 2 }
        goal("tidy", Task.CHALLENGE, "羽毛、卫生纸、脚印都收拾干净") { evidence.take(3).all { props[it]!!.done } }
        goal("cat", Task.CHALLENGE, "让猫背锅") { catScene }
        goal("innocent", Task.EXPERT, "四次检查全都装无辜成功") { phase == 2 && innocents >= 4 }
    }

    override fun finish(a: Animal, p: Prop) {
        super.finish(a, p)
        if (p.id == "cat") {
            catScene = true
            say("猫被推到了碎片旁边，一脸懵。")
        } else if (p.kind == Kind.CLEAN) say("${p.label}收拾好了。")
    }

    override fun timeOut() = "……"
    override val endsOnMain = true

    override fun ownerTick(dt: Double) {
        val o = owner
        if (phase == 0 && (time >= TIDY || evidence.take(3).all { props[it]!!.done } && catScene)) {
            phase = 1
            o.present = true
            o.at = P(-0.7, 0.4)
            husky.path = emptyList()
            husky.doing = null
            say("门开了！主人：“二哈，我回来了……这屋里怎么回事？”")
            emit(Ev.Sfx("door", o.at))
            inspectAt = 0
            inspectT = 0.0
        }
        if (phase != 1) return
        // Walk to each spot, look at it, then turn to the dog: be innocent when he does.
        val spots = listOf(P(-0.2, -0.15), P(-0.5, 0.1), P(0.3, 0.0), P(0.55, 0.3))
        val spot = spots[inspectAt]
        inspectT += dt
        val d = spot - o.at
        if (d.len() > 0.12) {
            o.heading = turnToward(o.heading, headingOf(d), dt * 5)
            o.at = o.at + d.norm() * minOf(d.len(), 0.2 * dt)
            o.anim = "walk"
            inspectT = 0.0
            return
        }
        o.anim = "inspect"
        if (inspectT < 1.5) return
        if (inspectT - dt < 1.5) {
            o.warning = WARN_SECONDS
            emit(Ev.Warn(WARN_SECONDS))
        }
        if (inspectT >= 1.5 + WARN_SECONDS && inspectT - dt < 1.5 + WARN_SECONDS) {
            o.heading = headingOf(husky.at - o.at)
            o.anim = "look"
            val id = evidence[inspectAt]
            val dirty = !props[id]!!.done && !(id == "shards" && catScene)
            when {
                husky.innocent <= 0 -> caught("主人回头，二哈没来得及装无辜！")
                dirty -> caught("主人：“${props[id]!!.label}……二哈！”")
                else -> {
                    innocents++
                    say(if (id == "shards" && catScene) "主人瞪着猫：“是你干的吧？”" else "主人看看你，你看看主人……他信了。")
                }
            }
        }
        if (inspectT >= 3.2) {
            inspectAt++
            inspectT = 0.0
            if (inspectAt >= spots.size) {
                phase = 2
                o.anim = "idle"
                say("主人摸摸你的头：“今天挺乖嘛。”（其实……）")
            }
        }
    }

    override fun looks() = false

    override fun hintFor(goal: String) = "主人回头前会停一下：那一刻点「装无辜」。"

    companion object {
        const val TIDY = 90.0
    }
}
