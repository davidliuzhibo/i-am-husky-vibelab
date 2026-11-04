package com.vrgames.husky.content

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Text
import com.vrgames.husky.ALBUM_ID
import com.vrgames.husky.LIST_ID
import com.vrgames.husky.core.Levels
import com.vrgames.husky.core.Task
import com.vrgames.husky.game.Game

private val CardBg = Color(0x26FFF8EE)

/** 拆家清单: every level, its stars and goals (pick one to play), and the three trophies. */
@Composable
fun ListWindow() {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val context = LocalContext.current
    val treasure: ImageBitmap? = remember { runCatching { context.assets.open("textures/treasure.jpg").use { BitmapFactory.decodeStream(it).asImageBitmap() } }.getOrNull() }
    Column(Modifier.fillMaxSize().background(WindowBg).padding(28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WindowHeader(LIST_ID, "拆家清单", 32.sp)
        Text("总星数 ${Game.stars} / ${Game.maxStars}。点一关就能玩（或重玩，把星星拿满）。", fontSize = 18.sp, color = Dim)
        for ((k, id) in Levels.IDS.withIndex()) {
            val open = Game.open(id)
            val s = Game.best[id] ?: 0
            val l = remember(id) { Levels.make(id) }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardBg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("第 ${k + 1} 关 · ${l.title}　" + if (s > 0) "★".repeat(s) + "☆".repeat(3 - s) else if (open) "还没玩过" else "还没开放", fontSize = 20.sp, color = if (open) Orange else Dim, modifier = Modifier.weight(1f))
                    if (open) Button(onClick = { Game.intro(id); Game.closeWindow(LIST_ID) }) { Text(if (s > 0) "重玩" else "开始", fontSize = 20.sp) }
                }
                for (g in l.goals) Text("${when (g.tier) { Task.MAIN -> "主线"; Task.CHALLENGE -> "挑战"; Task.EXPERT -> "高手" }}　${g.text}", fontSize = 15.sp, color = Ink)
            }
        }
        Text("奖杯", fontSize = 24.sp, color = Mint)
        Trophy("铜奖杯 · 铜骨头", "六关全部过关。", Game.bronze, "一根铜色的狗骨头，摆在电视柜上。")
        Trophy("银奖杯", "拿到一半以上的星（${Game.maxStars / 2} 颗）。", Game.silver, "相册里的隐藏照片：主人、二哈、猫和傻狍子的全家福。")
        Trophy("金奖杯 · 拆家大王", "拿满全部 ${Game.maxStars} 颗星。", Game.gold, "二哈戴上小王冠；隐藏结局「二哈的梦」；一幅珍藏画。")
        if (Game.gold) {
            Button(onClick = { Game.dream(); Game.closeWindow(LIST_ID) }) { Text("看「二哈的梦」", fontSize = 20.sp) }
            if (treasure != null) Image(treasure, "珍藏画", Modifier.fillMaxWidth().height(420.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Fit)
        }
    }
}

@Composable
private fun Trophy(name: String, rule: String, got: Boolean, reward: String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardBg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(if (got) "$name　已得到" else "$name　？", fontSize = 20.sp, color = if (got) Orange else Dim)
        Text(rule, fontSize = 16.sp, color = Ink)
        Text(if (got) reward else "得到后揭晓。", fontSize = 16.sp, color = if (got) Mint else Dim)
    }
}

/** 相册: the funniest moments, unlocked as they happen. */
@Composable
fun AlbumWindow() {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val context = LocalContext.current
    val cache = remember { HashMap<String, ImageBitmap?>() }
    Column(Modifier.fillMaxSize().background(WindowBg).padding(28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WindowHeader(ALBUM_ID, "相册", 32.sp)
        Text("已收集 ${Game.album.size} / ${Game.PHOTOS.size} 张。在游戏里发生好笑的事，就会自动拍下来。", fontSize = 18.sp, color = Dim)
        for (row in Game.PHOTOS.entries.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((id, caption) in row) {
                    val got = id in Game.album
                    Column(Modifier.width(370.dp).clip(RoundedCornerShape(10.dp)).background(CardBg).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val bmp = if (got) cache.getOrPut(id) { runCatching { context.assets.open("album/$id.jpg").use { BitmapFactory.decodeStream(it).asImageBitmap() } }.getOrNull() } else null
                        if (bmp != null) Image(bmp, caption, Modifier.fillMaxWidth().height(350.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                        else Box(Modifier.fillMaxWidth().height(350.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x22FFFFFF)), contentAlignment = Alignment.Center) { Text("？", fontSize = 60.sp, color = Dim) }
                        Text(if (got) caption else "还没拍到", fontSize = 17.sp, color = if (got) Ink else Dim)
                    }
                }
            }
        }
        Text("照片是 AI 生成的插画，记录游戏里的瞬间。", fontSize = 15.sp, color = Dim)
    }
}
