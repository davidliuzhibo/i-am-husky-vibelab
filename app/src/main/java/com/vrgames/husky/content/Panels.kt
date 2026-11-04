package com.vrgames.husky.content

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.PhysicalLengthConverter
import com.vrgames.husky.ALBUM_ID
import com.vrgames.husky.LIST_ID
import com.vrgames.husky.core.Forest
import com.vrgames.husky.core.Levels
import com.vrgames.husky.core.Task
import com.vrgames.husky.core.Who
import com.vrgames.husky.core.Yard
import com.vrgames.husky.game.Game

const val PANEL_CARD = "card"
const val PANEL_INTRO = "intro"
const val PANEL_HUD = "hud"
const val PANEL_ACTIONS = "actions"
const val PANEL_RESULT = "result"
const val PANEL_DREAM = "dream"
const val PANEL_MENU = "menu"
const val PANEL_NOTE = "note"

val Ink = Color(0xFFFFF8EE)
val Orange = Color(0xFFFFA94D)
val Sky = Color(0xFF8CCBFF)
val Mint = Color(0xFF8FE3B0)
val Dim = Color(0xFFC9C2B8)
val Alarm = Color(0xFFFF6B5A)
private val PanelBg = Color(0xE6222833)
/** Window backdrop: the system glass can be light, and every text color here is meant for dark. */
val WindowBg = Color(0xF2222833)

@Composable
fun PhysicalLengthConverter.m(meters: Float): Dp = lengthToDp(meters, LengthUnit.Meters)

@Composable
fun PhysicalLengthConverter.sz(meters: Float) = m(meters).value.sp

@Composable
private fun PanelBox(c: PhysicalLengthConverter, width: Float, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.016f))
    Column(
        Modifier.width(c.m(width)).clip(shape).background(PanelBg).border(c.m(0.0015f), Color(0x66FFA94D), shape).padding(c.m(0.014f)),
        verticalArrangement = Arrangement.spacedBy(c.m(0.007f)),
    ) { content() }
}

/** The step that moves the game on: bright orange with dark text. */
@Composable
fun CallToAction(c: PhysicalLengthConverter, text: String, color: Color = Orange, onClick: () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.014f))
    Text(
        text,
        Modifier.clip(shape).background(color).border(c.m(0.002f), Color(0xFFFFF4E0), shape)
            .clickable { onClick() }.padding(horizontal = c.m(0.02f), vertical = c.m(0.011f)),
        fontSize = c.sz(0.021f),
        color = Color(0xFF1E1A16),
    )
}

@Composable
fun Pill(c: PhysicalLengthConverter, text: String, color: Color = Dim, onClick: () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.012f))
    Text(
        text,
        Modifier.clip(shape).border(c.m(0.0015f), color, shape).clickable { onClick() }.padding(horizontal = c.m(0.012f), vertical = c.m(0.008f)),
        fontSize = c.sz(0.016f),
        color = color,
    )
}

private fun stars(n: Int) = "★".repeat(n) + "☆".repeat(3 - n)

@Composable
fun CardPanel(c: PhysicalLengthConverter) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    PanelBox(c, 0.52f) {
        Game.version // Read in here too: same arguments, so the box would skip its content.
        Text("我是哈士奇", fontSize = c.sz(0.036f), color = Orange)
        Text("主人一出门，二哈就开始拆家。你是坐在屋子上方的巨人，也是二哈的同伙：指挥它拆靠垫、偷饺子、躲洗澡、在雪地里撒欢——还要在主人回头的那一刻，让它露出那张无辜的脸。", fontSize = c.sz(0.016f), color = Ink)
        Text("怎么玩", fontSize = c.sz(0.018f), color = Mint)
        Text(
            "· 捏一下东西或它的名字：二哈就过去干活，指到附近也算。捏一下地板：跑过去。\n· 要撞东西，直接捏它。想自己冲刺：按「冲刺」再捏地方，或捏住二哈往后拉，松手。\n· 地上的扇形是主人的视线：变黄就是他要回头了，马上点「装无辜」！\n· 被抓 3 次就输了。每关有主线、挑战和高手任务。",
            fontSize = c.sz(0.015f), color = Ink,
        )
        if (Game.stars > 0) Text("已得 ${Game.stars} / ${Game.maxStars} 颗星，相册 ${Game.album.size} / ${Game.PHOTOS.size} 张。", fontSize = c.sz(0.014f), color = Mint)
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.008f)), verticalAlignment = Alignment.CenterVertically) {
            CallToAction(c, if (Game.stars > 0) "接着拆 →" else "开始拆家 →") { Game.start() }
            if (Game.gold) Pill(c, "二哈的梦", Orange) { Game.dream() }
        }
    }
}

@Composable
fun IntroPanel(c: PhysicalLengthConverter) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val l = Game.level ?: return
    PanelBox(c, 0.52f) {
        Game.version // Read in here too: same arguments, so the box would skip its content.
        Text("第 ${Levels.IDS.indexOf(l.id) + 1} 关 · ${l.title}", fontSize = c.sz(0.03f), color = Orange)
        Text(l.intro, fontSize = c.sz(0.016f), color = Ink)
        Goals(c, l)
        Text("限时 ${(l.limit / 60).toInt()} 分 ${(l.limit % 60).toInt()} 秒。", fontSize = c.sz(0.014f), color = Dim)
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.008f))) {
            CallToAction(c, "开始 →") { Game.begin() }
            Pill(c, "返回") { Game.toCard() }
        }
    }
}

@Composable
private fun Goals(c: PhysicalLengthConverter, l: com.vrgames.husky.core.Level, small: Boolean = false) {
    // Read here: the Level is the same object as it changes, so without this the list skips recomposing.
    Game.version
    for (tier in Task.entries) {
        val gs = l.goals.filter { it.tier == tier }
        if (gs.isEmpty()) continue
        val name = when (tier) { Task.MAIN -> "主线"; Task.CHALLENGE -> "挑战"; Task.EXPERT -> "高手" }
        Text(name, fontSize = c.sz(if (small) 0.013f else 0.015f), color = if (tier == Task.MAIN) Orange else if (tier == Task.CHALLENGE) Sky else Mint)
        for (g in gs) Text((if (g.met) "✔ " else "○ ") + g.text, fontSize = c.sz(if (small) 0.013f else 0.014f), color = if (g.met) Mint else Ink)
    }
}

/** At the front left: time, strikes and the goals. */
@Composable
fun HudPanel(c: PhysicalLengthConverter, toggle: (String) -> Unit) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val l = Game.level ?: return
    PanelBox(c, 0.27f) {
        Game.version // Read in here too: same arguments, so the box would skip its content.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(l.title, fontSize = c.sz(0.02f), color = Orange)
            Spacer(Modifier.weight(1f))
            val left = l.left.toInt()
            Text("%d:%02d".format(left / 60, left % 60), fontSize = c.sz(0.02f), color = if (left < 30) Alarm else Ink)
        }
        Text("被抓 " + "●".repeat(l.strikes) + "○".repeat((3 - l.strikes).coerceAtLeast(0)), fontSize = c.sz(0.014f), color = if (l.strikes > 0) Alarm else Dim)
        Goals(c, l, small = true)
        if (Game.lastPinch.isNotEmpty()) Text("刚才捏到：${Game.lastPinch}", fontSize = c.sz(0.012f), color = Dim)
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.006f))) {
            Pill(c, "提示", Orange) { Game.hint() }
            Pill(c, "拆家清单", Dim) { toggle(LIST_ID) }
        }
    }
}

/** At the front right: the buttons of the moment. */
@Composable
fun ActionsPanel(c: PhysicalLengthConverter) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val l = Game.level ?: return
    PanelBox(c, 0.24f) {
        Game.version // Read in here too: same arguments, so the box would skip its content.
        val warn = l.owner.present && l.owner.warning > 0
        CallToAction(c, if (warn) "装无辜！！" else "装无辜", if (warn) Alarm else Orange) { Game.innocent() }
        if (l.active == Who.HUSKY) Pill(c, if (Game.dashMode) "冲刺：捏要冲去的地方" else "冲刺", Sky) { Game.dashButton() }
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.006f))) {
            Pill(c, "嚎叫", Ink) { Game.howl() }
            if (l is Yard) Pill(c, "闻一闻", Mint) { Game.sniff() }
        }
        if (l is Forest) Pill(c, if (l.active == Who.HUSKY) "换傻狍子" else "换二哈", Mint) { Game.switch() }
        if (l.mainAt >= 0) CallToAction(c, "收工！", Mint) { Game.wrapUp() }
    }
}

@Composable
fun ResultPanel(c: PhysicalLengthConverter, toggle: (String) -> Unit) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val l = Game.level ?: return
    PanelBox(c, 0.52f) {
        Game.version // Read in here too: same arguments, so the box would skip its content.
        if (l.won) {
            Text("${l.title}　${stars(l.stars())}", fontSize = c.sz(0.03f), color = Orange)
            Text("用时 ${l.time.toInt()} 秒，被抓 ${l.strikes} 次" + if (l.hints > 0) "，用了 ${l.hints} 次提示（三星要不用提示）" else "", fontSize = c.sz(0.015f), color = Ink)
        } else {
            Text("${l.title}　没过关", fontSize = c.sz(0.03f), color = Alarm)
            Text("别灰心，二哈从不放弃拆家。", fontSize = c.sz(0.015f), color = Ink)
        }
        Goals(c, l, small = true)
        val t = Game.trophies
        if (t > Game.trophiesBefore) Text(
            when (t) {
                1 -> "得到铜奖杯「铜骨头」！六关全过。"
                2 -> "得到银奖杯！相册里多了一张全家福。"
                else -> "得到金奖杯「拆家大王」！解锁隐藏结局「二哈的梦」和珍藏画。"
            },
            fontSize = c.sz(0.017f), color = Mint,
        )
        Text("总星数 ${Game.stars} / ${Game.maxStars}", fontSize = c.sz(0.014f), color = Dim)
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.008f)), verticalAlignment = Alignment.CenterVertically) {
            if (l.won && Levels.IDS.indexOf(l.id) < Levels.IDS.size - 1) CallToAction(c, "下一关 →") { Game.next() }
            Pill(c, "再来一次", Orange) { Game.retry() }
            Pill(c, "相册", Sky) { toggle(ALBUM_ID) }
            if (Game.gold) Pill(c, "二哈的梦", Orange) { Game.dream() }
        }
    }
}

@Composable
fun DreamPanel(c: PhysicalLengthConverter) {
    PanelBox(c, 0.44f) {
        Text("二哈的梦", fontSize = c.sz(0.028f), color = Orange)
        Text("极光下的雪原上，戴着王冠的雪橇犬之王带着傻狍子，一圈又一圈地狂奔。", fontSize = c.sz(0.016f), color = Ink)
        Pill(c, "醒来") { Game.toCard() }
    }
}

@Composable
fun NotePanel(c: PhysicalLengthConverter) {
    val n = Game.note ?: return
    val shape = RoundedCornerShape(c.m(0.012f))
    Text(n, Modifier.widthIn(max = c.m(0.6f)).clip(shape).background(PanelBg).padding(c.m(0.01f)), fontSize = c.sz(0.018f), color = Orange)
}

@Composable
fun MenuPanel(c: PhysicalLengthConverter, toggle: (String) -> Unit, faceMe: () -> Unit, quit: () -> Unit, version: String) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    if (!Game.menuOpen) {
        Pill(c, "菜单", Orange) { Game.menuOpen = true }
        return
    }
    PanelBox(c, 0.26f) {
        Game.version // Read in here too: same arguments, so the box would skip its content.
        Pill(c, if (Game.window == LIST_ID) "收起拆家清单" else "拆家清单（选关、奖杯）", Orange) { toggle(LIST_ID) }
        Pill(c, if (Game.window == ALBUM_ID) "收起相册" else "相册") { toggle(ALBUM_ID) }
        if (Game.gold) Pill(c, "二哈的梦", Orange) { Game.menuOpen = false; Game.dream() }
        Pill(c, "把屋子摆到我正前方") { faceMe() }
        Pill(c, if (Game.musicOn) "音乐：开" else "音乐：关") { Game.toggleMusic() }
        RestartPill(c)
        Pill(c, "退出") { quit() }
        Text("进度自动保存。版本 $version", fontSize = c.sz(0.012f), color = Dim)
        Pill(c, "收起菜单") { Game.menuOpen = false }
    }
}

@Composable
private fun RestartPill(c: PhysicalLengthConverter) {
    if (Game.confirmRestart) {
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.006f))) {
            Pill(c, "确定清空进度", Alarm) { Game.confirmRestart = false; Game.menuOpen = false; Game.restart() }
            Pill(c, "算了") { Game.confirmRestart = false }
        }
    } else Pill(c, "清空进度从头开始") { Game.confirmRestart = true }
}

@Composable
fun WindowHeader(id: String, title: String, size: TextUnit) {
    val copy = remember { Any() }
    DisposableEffect(copy) {
        Game.windowShown(id, copy)
        onDispose { Game.windowGone(id, copy) }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = size, color = Orange)
        Spacer(Modifier.weight(1f))
        Button(onClick = { Game.closeWindow(id) }) { Text("关闭", fontSize = 22.sp) }
    }
}
