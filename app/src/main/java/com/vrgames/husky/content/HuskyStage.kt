package com.vrgames.husky.content

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.LoadType
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.event.SceneEvents
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.TextureCreateOption
import com.pico.spatial.core.ecs.resource.TextureEncoding
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.lifecycle.Cancellable
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.DataProvider
import com.pico.spatial.tracking.hmd.HMDTrackingProvider
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.content.SpatialViewAttachments
import com.pico.spatial.ui.foundation.content.toColor4
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialDragGesture
import com.pico.spatial.ui.foundation.gesture.detectSpatialPointerEvent
import com.pico.spatial.ui.foundation.gesture.detectSpatialTapGesture
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import com.vrgames.husky.ALBUM_ID
import com.vrgames.husky.LIST_ID
import com.vrgames.husky.core.Forest
import com.vrgames.husky.core.P
import com.vrgames.husky.core.Yard
import com.vrgames.husky.game.AutoPilot
import com.vrgames.husky.game.Game
import com.vrgames.husky.render.DioramaView
import com.vrgames.husky.render.Music
import com.vrgames.husky.render.Sound
import com.vrgames.husky.render.color
import com.vrgames.husky.render.model
import com.vrgames.husky.render.setPos
import com.vrgames.husky.render.setScale
import com.vrgames.husky.render.shared
import com.vrgames.husky.render.transform
import com.vrgames.husky.render.unlit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The giant's view (a full-immersion Stage): each level's diorama lies in front of the seated
 * player, a little below the eyes, inside a panorama of the place. Pinch the floor to send the dog
 * there, a thing to deal with it, the dog to aim a dash (then pinch where to), or pinch and pull
 * the dog back and let go to dash it like a slingshot. The roe deer is pinched to take it over.
 */
@Composable
fun HuskyStage() {
    val context = LocalContext.current
    val converter = LocalPhysicalLengthConverter.current
    val navigator = LocalSpatialNavigator.current
    val main = remember { Handler(Looper.getMainLooper()) }
    val head = remember { HMDTrackingProvider() }
    val subscription = remember { arrayOfNulls<Cancellable>(1) }
    var view by remember { mutableStateOf<DioramaView?>(null) }
    val seat = remember { Seat() }
    val panels = remember { HashMap<String, Entity>() }
    val pull = remember { PullState() }
    val skies = remember { HashMap<String, Pair<TextureResource?, TextureResource?>>() }
    val sky = remember { arrayOfNulls<Entity>(1) }
    val appVersion = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }

    val toggleWindow: (String) -> Unit = { id ->
        val shown = Game.window
        if (shown != null) Game.closeWindow(shown)
        if (shown != id) {
            Game.windowShown(id)
            navigator.openWindowContainer(id)
        }
    }
    val tap: (String) -> Unit = { id ->
        Log.i(INPUT_TAG, "tap $id")
        Game.pinched(id)
        val l = Game.level
        val f = id.split(":")
        when {
            l == null -> Unit
            f[0] == "tile" -> view?.tileCenter(l, f[1].toInt(), f[2].toInt())?.let { Game.tapTile(it, seat.eyeInDiorama()) }
            f[0] == "prop" -> Game.tapProp(f[1])
            f[0] == "husky" || f[0] == "roe" -> Game.tapAnimal(f[0])
        }
    }
    val letGo: (String) -> Unit = { why ->
        if (pull.on) {
            pull.on = false
            Log.i(INPUT_TAG, "pull release ($why) ${pull.dx},${pull.dz}")
            if (hypot(pull.dx, pull.dz) > 0.03) Game.pulled(P(pull.dx, pull.dz)) else Game.tapAnimal("husky")
        }
    }

    DisposableEffect(Unit) {
        Game.init(context)
        Game.debugTap = tap
        Game.closeWindow = { id ->
            navigator.closeWindowContainer(id)
            Game.windowGone(id)
        }
        val hd = runCatching { head.start() }
        Log.i(TAG, "head tracking ${hd.getOrNull() ?: hd.exceptionOrNull()?.message}")
        onDispose {
            runCatching { head.stop() }
            subscription[0]?.cancel()
            Game.closeWindow(LIST_ID)
            Game.closeWindow(ALBUM_ID)
            Game.closeWindow = {}
        }
    }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val music = Music(context).also {
            it.muted = !Game.musicOn
            Game.music = it
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) music.resume() else if (e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) music.pause()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            music.release()
            Game.music = null
        }
    }
    // A level's diorama and its sky.
    LaunchedEffect(Game.levelId, Game.phase, view) {
        val v = view ?: return@LaunchedEffect
        val l = Game.level ?: com.vrgames.husky.core.Levels.make(Game.levelId)
        synchronized(Game.lock) { v.build(l) }
        val key = if (Game.phase == Game.Phase.DREAM) "aurora" else if (l is Yard) "snow" else if (l is Forest) "forest" else "home"
        val (pano, ibl) = skies[key] ?: (null to null)
        sky[0]?.let { s ->
            if (pano != null) DioramaSky.setMaterial(s, UnlitMaterial.create().apply { setBaseColorTexture(pano) })
        }
        ibl?.let { v.useIbl(it) }
    }
    LaunchedEffect(Game.faceRequest) { if (Game.faceRequest > 0) seat.again() }
    LaunchedEffect(Game.noteVersion) {
        val n = Game.note ?: return@LaunchedEffect
        kotlinx.coroutines.delay(if (n.length > 40) 9000 else 6000)
        if (Game.note == n) Game.note = null
    }
    LaunchedEffect(Game.phase, Game.menuOpen, Game.note, view) {
        fun show(id: String, on: Boolean) = panels[id]?.let { if (it.enabled != on) it.enabled = on }
        val ph = Game.phase
        show(PANEL_CARD, ph == Game.Phase.CARD)
        show(PANEL_INTRO, ph == Game.Phase.INTRO)
        show(PANEL_RESULT, ph == Game.Phase.RESULT)
        show(PANEL_HUD, ph == Game.Phase.PLAYING)
        show(PANEL_ACTIONS, ph == Game.Phase.PLAYING)
        show(PANEL_DREAM, ph == Game.Phase.DREAM)
        show(PANEL_NOTE, Game.note != null)
        show(PANEL_MENU, true)
    }
    LaunchedEffect(Game.debugOpen) {
        when (Game.debugOpen ?: return@LaunchedEffect) {
            "list" -> if (Game.window != LIST_ID) toggleWindow(LIST_ID)
            "album" -> if (Game.window != ALBUM_ID) toggleWindow(ALBUM_ID)
            "menu" -> Game.menuOpen = true
            "close" -> Game.window?.let { Game.closeWindow(it) }
        }
        Game.debugOpen = null
    }

    SpatialView(
        Modifier
            .pointerInput(Unit) {
                detectSpatialTapGesture(context, TargetEntity.any()) { value ->
                    val id = view?.hitOf(value.targetEntity) ?: return@detectSpatialTapGesture
                    // The rest are taken as the fingers close (see the pointer events below).
                    if (id == "husky" || id == "roe") main.post { tap(id) }
                }
            }
            // Pinch the husky and pull back: a slingshot dash on letting go.
            .pointerInput(1) {
                fun meters(px: Float) = with(density) { converter.dpToLength(px.toDp(), LengthUnit.Meters) }.toDouble()
                detectSpatialDragGesture(
                    context = context,
                    targetedToEntity = TargetEntity.any(),
                    onDragStart = { _, value ->
                        val id = view?.hitOf(value.targetEntity) ?: return@detectSpatialDragGesture
                        pull.last = SystemClock.uptimeMillis()
                        if (id == "husky" && Game.phase == Game.Phase.PLAYING) {
                            pull.on = true
                            pull.dx = 0.0
                            pull.dz = 0.0
                        }
                    },
                    onDragEnd = { main.post { letGo("end") } },
                    onDragCancel = { main.post { letGo("cancel") } },
                ) { value ->
                    if (!pull.on) return@detectSpatialDragGesture
                    pull.last = SystemClock.uptimeMillis()
                    val (dx, dz) = seat.toDiorama(meters(value.dragAmount.x), meters(value.dragAmount.z))
                    pull.dx += dx
                    pull.dz += dz
                }
            }
            .pointerInput(2) {
                detectSpatialPointerEvent(context, TargetEntity.any()) { infos ->
                    if (infos.any { it.isUpEvent() }) main.post { letGo("fingers up") }
                    // Taken as the fingers close: closing them is what moves a hand's ray off its aim,
                    // so waiting for a tap to finish reads whatever the ray has drifted to by then.
                    for (i in infos) if (i.isDownEvent()) {
                        val id = view?.hitOf(i.targetedEntity) ?: continue
                        if (id != "husky" && id != "roe") main.post { tap(id) }
                    }
                    false
                }
            },
        attachments = {
            AttachmentPanel(PANEL_CARD) { CardPanel(converter) }
            AttachmentPanel(PANEL_INTRO) { IntroPanel(converter) }
            AttachmentPanel(PANEL_HUD) { HudPanel(converter, toggleWindow) }
            AttachmentPanel(PANEL_ACTIONS) { ActionsPanel(converter) }
            AttachmentPanel(PANEL_RESULT) { ResultPanel(converter, toggleWindow) }
            AttachmentPanel(PANEL_DREAM) { DreamPanel(converter) }
            AttachmentPanel(PANEL_NOTE) { NotePanel(converter) }
            AttachmentPanel(PANEL_MENU) {
                MenuPanel(converter, toggleWindow, faceMe = { Game.faceRequest++ }, quit = { context.findActivity()?.finishAndRemoveTask() }, version = appVersion)
            }
        },
        update = { _, attachments -> view?.let { attach(it, attachments, panels) } },
        initial = { content, attachments ->
            content.addEntity(Entity().apply {
                components.set(DirectionalLightComponent(Color(0xFFFFF2DE).toColor4(), 1100f, true))
                components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(-55f, 25f, 0f))
            })
            val tex = HashMap<String, TextureResource>()
            withContext(Dispatchers.IO) {
                for (name in TEXTURES) {
                    runCatching { TextureResource.load("textures/$name.jpg", LoadType.FROM_ASSETS, TextureCreateOption()).shared() }
                        .onSuccess { tex[name] = it }
                        .onFailure { Log.i(TAG, "texture $name: ${it.message}") }
                }
                for (k in listOf("home", "snow", "forest", "aurora")) {
                    val pano = runCatching { TextureResource.load("textures/sky_$k.jpg", LoadType.FROM_ASSETS, TextureCreateOption()).shared() }.getOrNull()
                    val ibl = runCatching { TextureResource.load("textures/ibl_$k.ktx", LoadType.FROM_ASSETS, TextureCreateOption().apply { textureEncoding = TextureEncoding.LINEAR }).shared() }.getOrNull()
                    skies[k] = pano to ibl
                }
            }
            val models = HashMap<String, Entity>()
            for (name in MODELS) {
                runCatching { Entity.loadSuspend("asset://models/$name.glb") }
                    .onSuccess { models[name] = it }
                    .onFailure { Log.i(TAG, "model $name: ${it.message}") }
            }
            // The panorama all around, centered on the player's eyes.
            val s = models["sky"]?.clone(Entity.CloneOptions(true, true))
                ?: model(com.pico.spatial.core.ecs.resource.MeshResource.createSphere(1f), unlit(color(0.75f, 0.82f, 0.9f)).apply { setCullingMode(MaterialCullingMode.FRONT) })
            s.setScale(if (models["sky"] != null) 9f / 12f else 9f)
            s.setPos(Vector3(0f, 1.2f, 0f))
            sky[0] = s
            val world = Entity()
            world.addChild(s)
            content.addEntity(world)
            val v = DioramaView(models, tex)
            seat.world = world
            seat.diorama = v.root
            world.addChild(v.root)
            attach(v, attachments, panels)
            Game.view = v
            Game.sound = Sound(v.root).also { it.load() }
            view = v
            AutoPilot.ready()
            subscription[0] = content.subscribe(SceneEvents.Update::class.java) { e ->
                val dt = e.deltaTime.toDouble().coerceIn(0.0, 0.1)
                val pose = runCatching { if (head.state == DataProvider.State.STARTED) head.latestData?.hmdPose else null }.getOrNull()
                seat.frame(pose?.position, pose?.rotation)
                // A pull whose end never came: let go after two silent seconds.
                if (pull.on && SystemClock.uptimeMillis() - pull.last > 2000) main.post { letGo("watchdog") }
                main.post { Game.tick(dt) }
                synchronized(Game.lock) {
                    val l = Game.level
                    if (l != null) v.sync(l, Game.dashMode || Game.dogMark > 0, (l as? Yard)?.scent)
                    if (l != null) v.dogView(l, Game.dogView && Game.phase == Game.Phase.PLAYING, dt)
                    if (Game.phase == Game.Phase.DREAM) v.dream(Game.dreamTime)
                }
                v.frame(dt)
            }
        },
    )
}

private class PullState {
    var on = false
    var dx = 0.0
    var dz = 0.0
    var last = 0L
}

/** Setting a material on every model of an entity tree. */
object DioramaSky {
    fun setMaterial(e: Entity, m: com.pico.spatial.core.ecs.resource.Material) = DioramaView.setMaterials(e, m)
}

/** Textures and models loaded if present (see docs/素材来源.md), else plain stand-ins. */
private val TEXTURES = listOf("woodfloor", "wallpaper", "snow", "tiles")
private val MODELS = listOf(
    "sky", "husky", "roe", "owner", "cat", "hen", "sofa", "teatable", "stool", "tvstand", "kitchen", "bed", "bathtub",
    "doghouse", "sled", "pine", "birch", "cottage", "fence", "plush", "slippers", "flour", "baozi",
)

private fun attach(v: DioramaView, a: SpatialViewAttachments, panels: HashMap<String, Entity>) {
    fun put(id: String, pos: Vector3, pitch: Float, yaw: Float = 0f) {
        val e = a.entity(id) ?: return
        if (panels[id] === e) return
        panels[id] = e
        v.root.addChild(e)
        e.setPos(pos)
        e.transform().eulerAngles = EulerAngles(pitch, yaw, 0f)
    }
    // Diorama frame (floor at 0, +z toward the player): cards high over the room's middle, tipped
    // toward the player, so a ray from the hand (lower than the eyes) reaches their buttons without
    // grazing the furniture's pinch boxes or the back wall; the hud at the left front corner and the
    // action buttons at the right, tilted up to the player.
    put(PANEL_CARD, Vector3(0f, 0.85f, 0.05f), -12f)
    put(PANEL_INTRO, Vector3(0f, 0.85f, 0.05f), -12f)
    put(PANEL_RESULT, Vector3(0f, 0.85f, 0.05f), -12f)
    put(PANEL_DREAM, Vector3(0f, 0.88f, -0.05f), -10f)
    put(PANEL_HUD, Vector3(-0.66f, 0.33f, 0.62f), -30f, 28f)
    put(PANEL_ACTIONS, Vector3(0.62f, 0.27f, 0.62f), -35f, -28f)
    put(PANEL_NOTE, Vector3(0f, 0.55f, -0.52f), 0f)
    put(PANEL_MENU, Vector3(-0.5f, 0.15f, 0.68f), -45f, 20f)
}

/**
 * Where the player sits: the world turned once to the mean heading of the first second of tracking
 * (and on 「摆到我正前方」), the diorama [AHEAD] meters in front at [FLOOR_Y].
 */
private class Seat {
    var world: Entity? = null
    var diorama: Entity? = null
    private var placed = false
    private var facing = 0f
    private val headings = ArrayList<Float>()
    private var started = 0L

    fun again() {
        placed = false
        headings.clear()
    }

    /** The eye, last seen, in the diorama frame (floor at 0, +z toward the player); where it sat when placed until then. */
    fun eyeInDiorama(): com.vrgames.husky.core.Vec3 {
        val e = lastEye
        if (!placed || e == null || Game.debugView != null) return com.vrgames.husky.core.Vec3(0.0, (1.2 - FLOOR_Y).toDouble(), AHEAD.toDouble())
        // Into the world's frame (turned by [facing] about the eye's foot), then into the diorama's.
        val dx = (e.x - origin.x).toDouble(); val dy = (e.y - origin.y).toDouble(); val dz = (e.z - origin.z).toDouble()
        val a = Math.toRadians(-facing.toDouble())
        val wx = dx * cos(a) + dz * sin(a)
        val wz = -dx * sin(a) + dz * cos(a)
        return com.vrgames.husky.core.Vec3(wx, dy - FLOOR_Y, wz + AHEAD)
    }
    private var lastEye: Vector3? = null
    private var origin = Vector3(0f, 0f, 0f)

    /** A hand motion in the room as (x, z) on the diorama. */
    fun toDiorama(dx: Double, dz: Double): Pair<Double, Double> {
        val a = Math.toRadians(-facing.toDouble())
        return (dx * cos(a) + dz * sin(a)) to (-dx * sin(a) + dz * cos(a))
    }

    fun frame(eye: Vector3?, rot: Quat?) {
        if (eye != null) lastEye = eye
        val d = Game.debugView?.split(",")?.mapNotNull { it.trim().toFloatOrNull() }
        if (d != null && d.size >= 6) {
            diorama?.setPos(Vector3(d[0], d[1], d[2]))
            diorama?.transform()?.eulerAngles = EulerAngles(d[5], d[3], 0f)
            diorama?.transform()?.setScaleVector(Vector3(d[4]))
            return
        }
        if (placed || rot == null || eye == null) return
        val f = rot.rotateVector(Vector3(0f, 0f, -1f))
        if (hypot(f.x, f.z) < 0.2f) return
        if (headings.isEmpty()) started = SystemClock.uptimeMillis()
        headings += Math.toDegrees(atan2(-f.x, -f.z).toDouble()).toFloat()
        if (SystemClock.uptimeMillis() - started < 1000) return
        val s = headings.sumOf { sin(Math.toRadians(it.toDouble())) }
        val c = headings.sumOf { cos(Math.toRadians(it.toDouble())) }
        facing = Math.toDegrees(atan2(s, c)).toFloat()
        placed = true
        world?.let {
            // Measured from the eye (taken as 1.2 m above a seated player's floor), not from the room's origin, which is
            // not always the floor on the device (a fixed height put 鲁班's bench almost on the floor, 2026-11-10).
            origin = Vector3(eye.x, eye.y - 1.2f, eye.z)
            it.setPos(origin)
            it.transform().eulerAngles = EulerAngles(0f, facing, 0f)
        }
        diorama?.setPos(Vector3(0f, FLOOR_Y, -AHEAD))
        diorama?.transform()?.eulerAngles = EulerAngles(0f, 0f, 0f)
        diorama?.transform()?.setScaleVector(Vector3(1f))
        Log.i(TAG, "facing ${headings.size} samples, mean $facing, eye $eye")
    }

    companion object {
        // 0.75 m below the eye: low enough that a hand in the lap points down at the far things, not across the near ones.
        const val FLOOR_Y = 0.45f
        // The diorama is 1 m deep: its near edge 0.65 m out, past a seated player's knees.
        const val AHEAD = 1.15f
    }
}

private fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}

private const val TAG = "HuskyStage"
/** Every pinch the stage sees (kept in release builds, for on-device reports). */
private const val INPUT_TAG = "HuskyInput"
