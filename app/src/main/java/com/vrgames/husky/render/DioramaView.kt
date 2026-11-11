package com.vrgames.husky.render

import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.EnvironmentLightingSettingsComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.ImageBasedLightComponent
import com.pico.spatial.core.ecs.ImageBasedLightReceiverComponent
import com.pico.spatial.core.ecs.ImageBasedLightSource
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector2
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.core.math.Vector4
import com.vrgames.husky.core.Animal
import com.vrgames.husky.core.Bath
import com.vrgames.husky.core.Forest
import com.vrgames.husky.core.Kind
import com.vrgames.husky.core.Kitchen
import com.vrgames.husky.core.Level
import com.vrgames.husky.core.Living
import com.vrgames.husky.core.Looks
import com.vrgames.husky.core.P
import com.vrgames.husky.core.Prop
import com.vrgames.husky.core.Vec3
import com.vrgames.husky.core.Yard
import kotlin.math.cos
import kotlin.math.sin

/**
 * One level's diorama in front of the seated player (full immersion), like a big doll's house with
 * its front open: a board with a textured floor, low walls for the rooms (or snow and trees
 * outside), the furniture and the things to play with, the husky, the roe deer, the cat and the
 * owner (rigged models playing their clips), the owner's gaze as a fan on the floor, the paper
 * trail, and an invisible grid of floor tiles to pinch ("tile:i:j"). Frame: the diorama's floor at
 * y = 0, its middle at the origin, +z toward the player (the core's [P] frame).
 */
class DioramaView(private val models: Map<String, Entity>, private val tex: Map<String, TextureResource>) {
    val root = Entity()
    /**
     * Everything of the level and its effects, under one node: at the table's scale normally, grown round the dog for
     * 狗狗视角 (v0.2), where it follows the dog without turning (turning the world makes people sick).
     */
    private val stage = Entity().also { root.addChild(it) }
    private var levelRoot = Entity().also { stage.addChild(it) }
    private val hits = HashMap<Entity, String>()
    private val sphere = MeshResource.createSphere(1f).shared()
    private val feathers = Sparks("feathers", 160, sphere, pbr(color(0.97f, 0.97f, 0.95f), roughness = 1f).shared()).also { stage.addChild(it.entity) }
    private val snowPuff = Sparks("snowpuff", 160, sphere, pbr(color(0.95f, 0.97f, 1f), roughness = 1f).shared()).also { stage.addChild(it.entity) }
    private val stars = Sparks("stars", 120, sphere, glow(color(1f, 0.85f, 0.4f, 1f)).shared()).also { stage.addChild(it.entity) }
    // v0.2: what each deed and each fall scatters.
    private val shards = Sparks("shards", 60, MeshResource.createBox(Vector3(1f, 0.3f, 0.6f)).shared(), pbr(color(0.35f, 0.55f, 0.9f), metallic = 0.2f, roughness = 0.2f).shared()).also { stage.addChild(it.entity) }
    private val waterDrops = Sparks("water", 60, sphere, pbr(color(0.6f, 0.8f, 1f, 0.8f), roughness = 0.05f).shared()).also { stage.addChild(it.entity) }
    private val soil = Sparks("soil", 60, sphere, pbr(color(0.36f, 0.24f, 0.14f), roughness = 1f).shared()).also { stage.addChild(it.entity) }
    private val flourDust = Sparks("flour", 120, sphere, pbr(color(0.98f, 0.97f, 0.93f), roughness = 1f).shared()).also { stage.addChild(it.entity) }
    private val crumbs = Sparks("crumbs", 60, sphere, pbr(color(0.85f, 0.7f, 0.45f), roughness = 1f).shared()).also { stage.addChild(it.entity) }
    private val pushDust = Sparks("pushdust", 60, sphere, pbr(color(0.75f, 0.72f, 0.66f, 0.8f), roughness = 1f).shared()).also { stage.addChild(it.entity) }
    private var deedFxClock = 0.0
    private val propViews = HashMap<String, Entity>()
    private val propTags = HashMap<String, Entity>()
    private val pinchBoxes = HashMap<Entity, Pair<Vector3, Vector3>>()
    private val chars = HashMap<String, Pair<Entity, Animator?>>()
    private var cone: Entity? = null
    private val coneCalm = unlit(color(1f, 0.95f, 0.6f, 0.12f), BlendingMode.TRANSPARENT).apply { setCullingMode(MaterialCullingMode.NONE) }.shared()
    private val coneWarn = unlit(color(1f, 0.75f, 0.2f, 0.28f), BlendingMode.TRANSPARENT).apply { setCullingMode(MaterialCullingMode.NONE) }.shared()
    private val coneLook = unlit(color(1f, 0.25f, 0.2f, 0.32f), BlendingMode.TRANSPARENT).apply { setCullingMode(MaterialCullingMode.NONE) }.shared()
    private val tileMat = unlit(color(1f, 1f, 1f, 0.02f), BlendingMode.TRANSPARENT).shared()
    private val markMat = glow(color(1f, 0.85f, 0.3f, 0.6f)).shared()
    private val dashMat = glow(color(0.4f, 0.8f, 1f, 0.8f)).shared()
    private val paperMat = pbr(color(0.98f, 0.98f, 0.96f), roughness = 0.9f).shared()
    private val trailPool = ArrayList<Entity>()
    private var dashRing: Entity? = null
    private var dropMarks = ArrayList<Pair<String, Entity>>()
    private var scentMark: Entity? = null
    private val iblSource = Entity().also { root.addChild(it) }
    private var built: String? = null
    private var clock = 0.0

    // Materials of the code-built things (shared: rebuilt levels destroy their entities).
    private val white = pbr(color(0.95f, 0.95f, 0.93f), roughness = 0.6f).shared()
    private val snowMat = pbr(color(0.95f, 0.97f, 1f), roughness = 0.85f).shared()
    private val wood = pbr(color(0.62f, 0.44f, 0.28f), roughness = 0.7f).shared()
    private val darkWood = pbr(color(0.36f, 0.24f, 0.15f), roughness = 0.7f).shared()
    private val blue = pbr(color(0.25f, 0.45f, 0.85f), roughness = 0.3f).shared()
    private val red = pbr(color(0.85f, 0.2f, 0.15f), roughness = 0.5f).shared()
    private val green = pbr(color(0.3f, 0.62f, 0.3f), roughness = 0.7f).shared()
    private val black = pbr(color(0.08f, 0.08f, 0.09f), roughness = 0.4f).shared()
    private val grey = pbr(color(0.55f, 0.56f, 0.6f), roughness = 0.9f).shared()
    private val cream = pbr(color(0.96f, 0.92f, 0.82f), roughness = 0.8f).shared()
    private val ice = pbr(color(0.7f, 0.85f, 1f, 0.75f), roughness = 0.1f, blending = BlendingMode.TRANSPARENT).shared()
    private val water = pbr(color(0.25f, 0.45f, 0.6f), roughness = 0.15f).shared()
    private val flourMat = pbr(color(0.98f, 0.97f, 0.94f), roughness = 1f).shared()

    // ---- Building ----

    fun build(l: Level) {
        if (built == l.id) return
        built = l.id
        levelRoot.destroy()
        hits.clear()
        pinchBoxes.clear()
        propTags.clear()
        propViews.clear()
        chars.clear()
        trailPool.clear()
        dropMarks.clear()
        cone = null
        dashRing = null
        scentMark = null
        levelRoot = Entity().also { stage.addChild(it) }
        val outdoor = l is Yard || l is Forest
        // The board and its floor.
        box(Vector3(l.w.toFloat() + 0.08f, 0.06f, l.d.toFloat() + 0.08f), Vector3(0f, -0.031f, 0f), darkWood)
        val floorTex = tex[if (outdoor) "snow" else if (l is Kitchen) "tiles" else "woodfloor"]
        // The light birch boards read as white under the room's light: tinted honey, glowing less.
        val floorMat = floorTex?.let { if (outdoor || l is Kitchen) textured(it, 0.4f) else textured(it, 0.26f, color(0.86f, 0.64f, 0.44f)) } ?: if (outdoor) snowMat else wood
        levelRoot.addChild(model(quad(l.w.toFloat(), l.d.toFloat(), if (outdoor) 2.5f else 4f), floorMat).apply {
            setPos(Vector3(0f, 0.001f, 0f))
            transform().eulerAngles = EulerAngles(-90f, 0f, 0f)
        })
        if (!outdoor) {
            // Low walls at the back and sides; the front is open to the player.
            val wall = tex["wallpaper"]?.let { textured(it, 0.32f, color(0.97f, 0.9f, 0.8f)) } ?: cream
            val h = 0.3f
            levelRoot.addChild(model(quad(l.w.toFloat(), h, 3f), wall).apply { setPos(Vector3(0f, h / 2, -l.d.toFloat() / 2)) })
            levelRoot.addChild(model(quad(l.d.toFloat(), h, 2f), wall).apply { setPos(Vector3(-l.w.toFloat() / 2, h / 2, 0f)); transform().eulerAngles = EulerAngles(0f, 90f, 0f) })
            levelRoot.addChild(model(quad(l.d.toFloat(), h, 2f), wall).apply { setPos(Vector3(l.w.toFloat() / 2, h / 2, 0f)); transform().eulerAngles = EulerAngles(0f, -90f, 0f) })
            for (x in listOf(-1f, 1f)) box(Vector3(0.025f, h + 0.02f, 0.025f), Vector3(x * l.w.toFloat() / 2, h / 2, -l.d.toFloat() / 2), white)
            box(Vector3(l.w.toFloat(), 0.02f, 0.02f), Vector3(0f, h, -l.d.toFloat() / 2), white)
            decorate(l, h)
        }
        if (l is Forest) {
            // The stream, and (once the roe deer has kicked it down) the log across it.
            box(Vector3(0.1f, 0.006f, l.d.toFloat()), Vector3(0.1f, 0.003f, 0f), water)
            box(Vector3(0.4f, 0.004f, 0.3f), Vector3(0.45f, 0.004f, -0.25f), ice)
            propViews["log"] = cylinder(0.022f, 0.24f, darkWood).apply { setPos(Vector3(0.1f, 0.025f, 0.04f)); transform().eulerAngles = EulerAngles(0f, 0f, 90f); enabled = false }
        }
        for (p in l.props.values) buildProp(p)
        // Characters.
        addChar("husky", "husky", 0.15f)
        if (l.roe != null) addChar("roe", "roe", 0.17f)
        if (l is Kitchen) addChar("cat", "cat", 0.09f)
        addChar("owner", "owner", 0.36f)
        if (l is Yard) for (k in 0 until 3) addChar("hen$k", "hen", 0.06f)
        // The owner's gaze on the floor.
        cone = model(fan(l.eyeRange, l.eyeHalf), coneCalm).also { levelRoot.addChild(it) }
        // The floor's pinch tiles.
        val nx = (l.w / TILE).toInt()
        val nz = (l.d / TILE).toInt()
        val tileMesh = MeshResource.createBox(Vector3(TILE.toFloat() * 0.96f, 0.004f, TILE.toFloat() * 0.96f), 0.002f).shared()
        for (j in 0 until nz) for (i in 0 until nx) {
            val c = tileCenter(l, i, j)
            val e = model(tileMesh, tileMat)
            e.setPos(Vector3(c.x.toFloat(), 0.004f, c.z.toFloat()))
            pinchable(e, "tile:$i:$j", Vector3(TILE.toFloat(), 0.01f, TILE.toFloat()))
            levelRoot.addChild(e)
        }
        dashRing = model(MeshResource.createTorus(0.1f, 0.006f), dashMat).also { it.enabled = false; levelRoot.addChild(it) }
        scentMark = model(sphere, glow(color(0.7f, 1f, 0.5f, 0.8f))).also { it.enabled = false; it.setScale(0.02f); levelRoot.addChild(it) }
        receive(levelRoot)
        // Everyone where the level starts them, at once: until a level is played nothing else places them, and on the
        // start card (device v0.2.2) the dog and the owner (out at work in the first room) stood inside the tea table.
        sync(l, false, null)
    }

    fun tileCenter(l: Level, i: Int, j: Int) = P(-l.w / 2 + (i + 0.5) * TILE, -l.d / 2 + (j + 0.5) * TILE)

    private fun addChar(id: String, modelName: String, height: Float) {
        val holder = Entity()
        val m = models[modelName]?.clone(Entity.CloneOptions(true, true))
        val anim = if (m != null) {
            m.setScale(height)
            CHAR_YAW[modelName]?.let { m.transform().eulerAngles = EulerAngles(0f, it, 0f) }
            holder.addChild(m)
            Animator(m, id).also { it.play("idle") }
        } else {
            holder.addChild(model(MeshResource.createCapsule(height, height * 0.25f), if (id == "owner") blue else white).apply { setPos(Vector3(0f, height / 2, 0f)) })
            null
        }
        if (id == "husky" || id == "roe") {
            val w = if (id == "husky") Looks.DOG_W.toFloat() else 0.16f
            val h = if (id == "husky") Looks.DOG_H.toFloat() else height
            pinchable(holder, id, Vector3(w, h, w), Vector3(0f, h / 2, 0f))
        }
        levelRoot.addChild(holder)
        chars[id] = holder to anim
    }

    private fun buildProp(p: Prop) {
        val e = Entity()
        val h = MODEL_H[p.model]
        val m = if (h != null) models[p.model]?.clone(Entity.CloneOptions(true, true)) else null
        if (m != null) {
            m.setScale(h!!)
            m.transform().eulerAngles = EulerAngles(0f, (p.yaw + (MODEL_YAW[p.model] ?: 0.0)).toFloat(), 0f)
            e.addChild(m)
        } else coded(p, e)
        e.setPos(Vector3(p.pos.x.toFloat(), p.lift.toFloat(), p.pos.z.toFloat()))
        Looks.hitBox(p)?.let { b ->
            pinchable(e, "prop:${p.id}", Vector3(b.w.toFloat(), b.h.toFloat(), b.d.toFloat()), Vector3(0f, b.cy.toFloat(), 0f))
        }
        levelRoot.addChild(e)
        propViews[p.id] = e
        // What can be dealt with says its name: a pillow on a cream sofa is hard to tell by eye.
        if (Looks.hasTag(p)) {
            val t = tag(p.label).apply { setPos(Vector3(0f, Looks.tagY(p).toFloat(), 0f)) }
            // The name is the easiest thing to aim at: pinching it is pinching the thing.
            if (Looks.pinchTag(p)) {
                pinchable(t, "prop:${p.id}", Vector3(Looks.tagWide(p).toFloat(), 0.05f, 0.02f))
            }
            e.addChild(t)
            propTags[p.id] = t
        }
        if (p.kind == Kind.DROP) {
            val ring = model(MeshResource.createTorus(0.05f, 0.004f), markMat).apply { setPos(Vector3(p.at.x.toFloat(), 0.01f, p.at.z.toFloat())); enabled = false }
            levelRoot.addChild(ring)
            dropMarks += p.id to ring
        }
    }

    /** Small things with no AI model, made from shapes. */
    private fun coded(p: Prop, e: Entity) {
        fun add(mesh: MeshResource, m: Material, at: Vector3, rot: EulerAngles? = null) = model(mesh, m).also { it.setPos(at); rot?.let { r -> it.transform().eulerAngles = r }; e.addChild(it) }
        when (p.model) {
            "pillow" -> add(MeshResource.createBox(Vector3(0.09f, 0.04f, 0.06f), 0.015f), red, Vector3(0f, 0.095f, 0f))
            "remote" -> add(MeshResource.createBox(Vector3(0.035f, 0.008f, 0.012f), 0.003f), black, Vector3(0f, 0.004f, 0f))
            "tissue" -> add(MeshResource.createCylinder(0.03f, 0.016f), white, Vector3(0f, 0.016f, 0f), EulerAngles(0f, 0f, 90f))
            "vase" -> { add(MeshResource.createCylinder(0.05f, 0.016f), blue, Vector3(0f, 0.025f, 0f)); add(sphere, blue, Vector3(0f, 0.022f, 0f)).setScale(0.019f) }
            "books" -> for (k in 0 until 4) add(MeshResource.createBox(Vector3(0.04f, 0.009f, 0.03f), 0.001f), listOf(red, blue, green, cream)[k], Vector3(0f, 0.005f + k * 0.009f, 0f), EulerAngles(0f, k * 12f, 0f))
            "plant" -> { add(MeshResource.createCylinder(0.03f, 0.015f), red, Vector3(0f, 0.015f, 0f)); add(sphere, green, Vector3(0f, 0.05f, 0f)).setScale(0.025f) }
            "dumplings" -> {
                add(MeshResource.createCylinder(0.006f, 0.035f), white, Vector3(0f, 0.065f, 0f))
                for (k in 0 until 6) add(sphere, cream, Vector3(0.018f * cos(k * 1.05f), 0.072f, 0.018f * sin(k * 1.05f))).setScale(0.008f)
            }
            "lowtable" -> { add(MeshResource.createBox(Vector3(0.14f, 0.01f, 0.1f), 0.003f), wood, Vector3(0f, 0.058f, 0f)); for (x in listOf(-0.06f, 0.06f)) for (z in listOf(-0.04f, 0.04f)) add(MeshResource.createBox(Vector3(0.008f, 0.055f, 0.008f), 0.001f), wood, Vector3(x, 0.028f, z)) }
            "pin" -> add(MeshResource.createCylinder(0.09f, 0.008f), wood, Vector3(0f, 0.008f, 0f), EulerAngles(0f, 0f, 90f))
            "shelf" -> { add(MeshResource.createBox(Vector3(0.14f, 0.16f, 0.06f), 0.004f), wood, Vector3(0f, 0.08f, 0f)) }
            "mound" -> add(sphere, snowMat, Vector3(0f, 0f, 0f)).also { it.transform().setScaleVector(Vector3(0.05f, 0.03f, 0.045f)) }
            "flag" -> { add(MeshResource.createCylinder(0.09f, 0.003f), darkWood, Vector3(0f, 0.045f, 0f)); add(MeshResource.createBox(Vector3(0.035f, 0.022f, 0.002f), 0f), red, Vector3(0.018f, 0.08f, 0f)) }
            "snowman" -> { add(sphere, snowMat, Vector3(0f, 0.03f, 0f)).setScale(0.032f); add(sphere, snowMat, Vector3(0f, 0.075f, 0f)).setScale(0.022f); add(MeshResource.createCone(0.02f, 0.004f), red, Vector3(0f, 0.077f, 0.022f), EulerAngles(90f, 0f, 0f)) }
            "snowbank" -> add(MeshResource.createBox(Vector3(0.5f, 0.05f, 0.06f), 0.02f), snowMat, Vector3(0f, 0.025f, 0f))
            "rock" -> add(sphere, grey, Vector3(0f, 0.03f, 0f)).also { it.transform().setScaleVector(Vector3(0.07f, 0.05f, 0.06f)) }
            "rocks" -> { val b = p.block; if (b != null) for (k in 0 until 6) add(sphere, grey, Vector3(((k % 2) - 0.5f) * (b.hx * 1.2f).toFloat(), 0.025f, (-b.hz + b.hz * 2 * k / 5).toFloat())).setScale(0.04f) }
            "feathers" -> for (k in 0 until 14) add(sphere, white, Vector3(0.05f * cos(k * 2.3f) * (k % 3 + 1) / 3, 0.004f, 0.05f * sin(k * 2.3f) * (k % 3 + 1) / 3)).also { it.transform().setScaleVector(Vector3(0.01f, 0.003f, 0.006f)) }
            "paper" -> for (k in 0 until 8) add(MeshResource.createBox(Vector3(0.03f, 0.002f, 0.015f), 0f), paperMat, Vector3(-0.07f + k * 0.02f, 0.002f, 0.02f * sin(k * 1.3f)), EulerAngles(0f, k * 25f, 0f))
            "prints" -> for (k in 0 until 8) add(sphere, flourMat, Vector3(-0.06f + k * 0.017f, 0.002f, if (k % 2 == 0) -0.01f else 0.01f)).also { it.transform().setScaleVector(Vector3(0.008f, 0.002f, 0.01f)) }
            "shards" -> for (k in 0 until 5) add(MeshResource.createBox(Vector3(0.015f, 0.004f, 0.01f), 0f), blue, Vector3(0.02f * cos(k * 1.3f), 0.003f, 0.02f * sin(k * 1.3f)), EulerAngles(0f, k * 40f, 0f))
            "curtain" -> add(MeshResource.createBox(Vector3(0.14f, 0.26f, 0.02f), 0.006f), red, Vector3(0f, 0.13f, 0f))
            "closet" -> add(MeshResource.createBox(Vector3(0.2f, 0.28f, 0.08f), 0.006f), wood, Vector3(0f, 0.14f, 0f))
            "bed" -> add(MeshResource.createBox(Vector3(0.36f, 0.08f, 0.22f), 0.01f), blue, Vector3(0f, 0.06f, 0f))
            "sofa" -> add(MeshResource.createBox(Vector3(0.38f, 0.1f, 0.12f), 0.02f), red, Vector3(0f, 0.05f, 0f))
            "cat" -> Unit
            "", "fence" -> Unit
            else -> add(MeshResource.createBox(Vector3(p.size.toFloat(), p.size.toFloat() * 0.6f, p.size.toFloat()), 0.01f), cream, Vector3(0f, p.size.toFloat() * 0.3f, 0f))
        }
    }

    private fun box(size: Vector3, at: Vector3, m: Material) = model(MeshResource.createBox(size, 0.004f), m).also { it.setPos(at); levelRoot.addChild(it) }
    private fun cylinder(h: Float, r: Float, m: Material) = model(MeshResource.createCylinder(h, r), m).also { levelRoot.addChild(it) }

    private fun textured(t: TextureResource, glowShare: Float, tint: com.pico.spatial.core.math.Color4? = null): Material = PhysicallyBasedMaterial.create().apply {
        setBaseColorTexture(t)
        tint?.let { setBaseColor(it) }
        setRoughness(0.85f)
        setEmissiveTexture(t)
        val c = tint ?: color(1f, 1f, 1f)
        setEmissiveColor(color(c.red * glowShare, c.green * glowShare, c.blue * glowShare))
    }.shared()

    /**
     * A lived-in room: skirting along the walls, a window with curtains and framed pictures on the
     * back wall, a clock, and (in the living room) a round rug. All on the walls or flat on the
     * floor, so nothing stands in the husky's way.
     */
    private fun decorate(l: Level, h: Float) {
        val w = l.w.toFloat()
        val d = l.d.toFloat()
        val back = -d / 2 + 0.006f
        box(Vector3(w, 0.022f, 0.008f), Vector3(0f, 0.011f, back), darkWood)
        for (x in listOf(-1f, 1f)) box(Vector3(0.008f, 0.022f, d), Vector3(x * (w / 2 - 0.006f), 0.011f, 0f), darkWood)
        // The window: sky-blue glass in a white frame, with crossbars and two curtains.
        val wx = if (l is Kitchen) 0.45f else -0.3f
        box(Vector3(0.3f, 0.18f, 0.006f), Vector3(wx, 0.16f, back), white)
        box(Vector3(0.27f, 0.15f, 0.004f), Vector3(wx, 0.16f, back + 0.004f), glass)
        box(Vector3(0.008f, 0.15f, 0.006f), Vector3(wx, 0.16f, back + 0.006f), white)
        box(Vector3(0.27f, 0.008f, 0.006f), Vector3(wx, 0.16f, back + 0.006f), white)
        for (x in listOf(-0.17f, 0.17f)) box(Vector3(0.05f, 0.22f, 0.01f), Vector3(wx + x, 0.15f, back + 0.012f), curtain)
        box(Vector3(0.42f, 0.008f, 0.012f), Vector3(wx, 0.262f, back + 0.012f), darkWood)
        // Pictures and a clock.
        val pics = if (l is Kitchen) listOf(-0.35f to 0.12f, 0.05f to 0.09f) else listOf(0.25f to 0.12f, 0.5f to 0.09f)
        for ((k, pic) in pics.withIndex()) {
            val (px, size) = pic
            box(Vector3(size + 0.014f, size * 0.8f + 0.014f, 0.006f), Vector3(px, 0.18f, back + 0.002f), if (k == 0) wood else darkWood)
            box(Vector3(size, size * 0.8f, 0.004f), Vector3(px, 0.18f, back + 0.006f), paintings[(k + l.id.length) % paintings.size])
        }
        val clock = Entity().apply { setPos(Vector3(if (l is Kitchen) -0.6f else 0.05f, 0.21f, back + 0.008f)); transform().eulerAngles = EulerAngles(90f, 0f, 0f) }
        clock.addChild(model(MeshResource.createCylinder(0.008f, 0.035f), red))
        clock.addChild(model(MeshResource.createCylinder(0.009f, 0.03f), white))
        for (a in listOf(0f, 60f)) clock.addChild(model(MeshResource.createBox(Vector3(0.003f, 0.002f, 0.022f), 0.0005f), darkWood).apply { setPos(Vector3(0f, -0.006f, 0f)); transform().eulerAngles = EulerAngles(0f, a, 0f) })
        levelRoot.addChild(clock)
        // A round rug in the living rooms.
        if (l is Living || l.id == "home") {
            levelRoot.addChild(model(MeshResource.createCylinder(0.003f, 0.3f), rug).apply { setPos(Vector3(0.05f, 0.0025f, 0.05f)); transform().setScaleVector(Vector3(1.3f, 1f, 0.9f)) })
        }
    }

    private val glass = pbr(color(0.62f, 0.82f, 0.95f), roughness = 0.1f, emissive = color(0.45f, 0.62f, 0.75f)).shared()
    private val curtain = pbr(color(0.95f, 0.72f, 0.55f), roughness = 0.9f).shared()
    private val paintings = listOf(color(0.55f, 0.75f, 0.9f), color(0.95f, 0.8f, 0.45f), color(0.6f, 0.8f, 0.55f)).map { c ->
        PhysicallyBasedMaterial.create().apply {
            // A little landscape: sky, a hill, a sun.
            val b = android.graphics.Bitmap.createBitmap(64, 52, android.graphics.Bitmap.Config.ARGB_8888)
            val cv = android.graphics.Canvas(b)
            cv.drawColor(android.graphics.Color.rgb((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt()))
            val p = android.graphics.Paint().apply { isAntiAlias = true }
            p.color = 0xFF6DA35A.toInt(); cv.drawOval(-20f, 30f, 50f, 80f, p)
            p.color = 0xFF4E8A4A.toInt(); cv.drawOval(20f, 34f, 90f, 84f, p)
            p.color = 0xFFFFF3B0.toInt(); cv.drawCircle(46f, 14f, 7f, p)
            val t = TextureResource.create(b)
            setBaseColorTexture(t); setEmissiveTexture(t); setEmissiveColor(color(0.35f, 0.35f, 0.35f)); setRoughness(0.6f)
        }.shared()
    }
    private val rug = PhysicallyBasedMaterial.create().apply {
        val n = 128
        val b = android.graphics.Bitmap.createBitmap(n, n, android.graphics.Bitmap.Config.ARGB_8888)
        val cv = android.graphics.Canvas(b)
        val p = android.graphics.Paint().apply { isAntiAlias = true }
        for ((k, c) in listOf(0xFFB5523B, 0xFFF0D9A8, 0xFF3F6E8C, 0xFFF0D9A8, 0xFFB5523B).withIndex()) {
            p.color = c.toInt(); cv.drawCircle(n / 2f, n / 2f, n / 2f - k * 12f, p)
        }
        val t = TextureResource.create(b)
        setBaseColorTexture(t); setEmissiveTexture(t); setEmissiveColor(color(0.3f, 0.3f, 0.3f)); setRoughness(0.95f)
    }.shared()

    /** A name tag: dark text on a cream pill, tipped back to face a player looking down at the room. */
    private fun tag(text: String): Entity {
        val px = 48
        val ink = android.graphics.Paint().apply { isAntiAlias = true; color = 0xFF2A1A10.toInt(); textSize = px * 0.72f; typeface = android.graphics.Typeface.DEFAULT_BOLD; textAlign = android.graphics.Paint.Align.CENTER }
        val b = android.graphics.Bitmap.createBitmap((ink.measureText(text) + px * 0.7f).toInt(), px, android.graphics.Bitmap.Config.ARGB_8888)
        val cv = android.graphics.Canvas(b)
        cv.drawRoundRect(0f, 0f, b.width.toFloat(), px.toFloat(), px / 2f, px / 2f, android.graphics.Paint().apply { isAntiAlias = true; color = 0xFFFDF6E3.toInt() })
        cv.drawText(text, b.width / 2f, px * 0.73f, ink)
        val m = unlit(color(1f, 1f, 1f), BlendingMode.MASKED).apply { setBaseColorTexture(TextureResource.create(b)); setOpacityThreshold(0.3f) }
        val h = Looks.TAG_H.toFloat()
        val w = h * b.width / px
        val x = w / 2
        val y = h / 2
        val mesh = MeshResource.createWithMeshModel(
            MeshModel(listOf(Vector3(-x, -y, 0f), Vector3(x, -y, 0f), Vector3(x, y, 0f), Vector3(-x, y, 0f)), listOf(0, 1, 2, 0, 2, 3), List(4) { Vector3(0f, 0f, 1f) }, List(4) { Vector4(1f, 0f, 0f, 1f) }, listOf(Vector2(0f, 0f), Vector2(1f, 0f), Vector2(1f, 1f), Vector2(0f, 1f))),
            BoundingBox(Vector3(-x, -y, -0.001f), Vector3(x, y, 0.001f)),
            "tag${w}",
        )
        return model(mesh, m).apply { transform().eulerAngles = EulerAngles(-25f, 0f, 0f) }
    }

    /** A quad [w]×[h] in the xy plane, facing +z, its texture repeated [rep] times along the longer side. */
    private fun quad(w: Float, h: Float, rep: Float): MeshResource {
        val x = w / 2
        val y = h / 2
        val ru = if (w >= h) rep else rep * w / h
        val rv = if (h > w) rep else rep * h / w
        val pos = listOf(Vector3(-x, -y, 0f), Vector3(x, -y, 0f), Vector3(x, y, 0f), Vector3(-x, y, 0f))
        val uv = listOf(Vector2(0f, rv), Vector2(ru, rv), Vector2(ru, 0f), Vector2(0f, 0f))
        return MeshResource.createWithMeshModel(
            MeshModel(pos, listOf(0, 1, 2, 0, 2, 3), List(4) { Vector3(0f, 0f, 1f) }, List(4) { Vector4(1f, 0f, 0f, 1f) }, uv),
            BoundingBox(Vector3(-x, -y, -0.001f), Vector3(x, y, 0.001f)),
            "q${w}x${h}r$rep",
        )
    }

    /** The owner's field of view as a fan on the floor: apex at the origin, opening toward -z. */
    private fun fan(range: Double, half: Double): MeshResource {
        val pos = arrayListOf(Vector3(0f, 0f, 0f))
        val n = 16
        val r = range.toFloat()
        for (k in 0..n) {
            val a = -half + 2 * half * k / n
            pos += Vector3((r * sin(a)).toFloat(), 0f, (-r * cos(a)).toFloat())
        }
        val idx = ArrayList<Int>()
        for (k in 1..n) { idx += 0; idx += k; idx += k + 1 }
        return MeshResource.createWithMeshModel(
            MeshModel(pos, idx, List(pos.size) { Vector3(0f, 1f, 0f) }, List(pos.size) { Vector4(1f, 0f, 0f, 1f) }, List(pos.size) { Vector2(0f, 0f) }),
            BoundingBox(Vector3(-r, -0.001f, -r), Vector3(r, 0.001f, 0.01f)),
            "fan$range",
        )
    }

    /** Makes [e] something the hand's ray can pinch, by a box [size] around [center]. */
    private fun pinchable(e: Entity, id: String, size: Vector3, center: Vector3 = Vector3(0f, 0f, 0f)) {
        e.components.set(CollisionComponent(listOf(ShapeResource.createBox(size).offsetByTranslation(center)), PhysicsMaterialResource()))
        e.components.set(InteractableComponent())
        e.components.set(HoverEffectComponent())
        hits[e] = id
        pinchBoxes[e] = size to center
    }

    /**
     * What a ray from [from] toward [to] (diorama frame) pinches first, among the boxes really in the
     * scene and where they really are now (the test hook: the SDK has no ray query of its own).
     */
    fun rayHit(from: Vector3, to: Vector3): String? {
        val d = floatArrayOf(to.x - from.x, to.y - from.y, to.z - from.z)
        val o = floatArrayOf(from.x, from.y, from.z)
        var best = Float.MAX_VALUE
        var hit: String? = null
        for ((e, box) in pinchBoxes) {
            if (!e.enabled) continue
            var at = box.second
            var cur: Entity? = e
            while (cur != null && cur !== root) {
                val p = cur.transform().position
                at = Vector3(at.x + p.x, at.y + p.y, at.z + p.z)
                cur = cur.getParent()
            }
            val c = floatArrayOf(at.x, at.y, at.z)
            val half = floatArrayOf(box.first.x / 2, box.first.y / 2, box.first.z / 2)
            var t0 = 0f
            var t1 = Float.MAX_VALUE
            var missed = false
            for (k in 0..2) {
                if (kotlin.math.abs(d[k]) < 1e-6f) {
                    if (kotlin.math.abs(o[k] - c[k]) > half[k]) missed = true
                    continue
                }
                val a = (c[k] - half[k] - o[k]) / d[k]
                val b = (c[k] + half[k] - o[k]) / d[k]
                t0 = maxOf(t0, minOf(a, b))
                t1 = minOf(t1, maxOf(a, b))
            }
            if (!missed && t0 <= t1 && t0 < best) {
                best = t0
                hit = hits[e]
            }
        }
        return hit
    }

    fun hitOf(e: Entity?): String? {
        var cur = e
        while (cur != null) {
            hits[cur]?.let { return it }
            cur = cur.getParent()
        }
        return null
    }

    // ---- Each frame ----

    fun sync(l: Level, dashMode: Boolean, scent: P?) {
        // Characters.
        placeChar("husky", l.husky, l.husky.hiddenIn == null)
        l.roe?.let { placeChar("roe", it, true) }
        if (l is Kitchen) placeChar("cat", l.cat, true)
        val o = l.owner
        chars["owner"]?.let { (e, a) ->
            if (e.enabled != o.present) e.enabled = o.present
            e.setPos(Vector3(o.at.x.toFloat(), 0f, o.at.z.toFloat()))
            e.transform().eulerAngles = EulerAngles(0f, yawOf(o.heading), 0f)
            a?.play(o.anim)
        }
        cone?.let { c ->
            val show = o.present
            if (c.enabled != show) c.enabled = show
            c.setPos(Vector3(o.at.x.toFloat(), 0.006f, o.at.z.toFloat()))
            c.transform().eulerAngles = EulerAngles(0f, -Math.toDegrees(o.heading).toFloat(), 0f)
            val m = if (o.looking > 0 || l is Bath) coneLook else if (o.warning > 0) coneWarn else coneCalm
            c.components[ModelComponent::class.java]?.materials?.let { mats -> if (mats.size > 0 && mats[0] !== m) mats[0] = m }
        }
        // Things.
        for (p in l.props.values) {
            val e = propViews[p.id] ?: continue
            val inMouth = l.husky.carrying === p
            e.setPos(Vector3(p.pos.x.toFloat(), if (inMouth) MOUTH_Y else p.lift.toFloat(), p.pos.z.toFloat()))
            // Held crosswise in the mouth, turning with the dog (v0.2: it used to sit inside the head).
            if (inMouth) e.transform().eulerAngles = EulerAngles(0f, yawOf(l.husky.heading) + 90f, 0f)
            val gone = (p.kind == Kind.CLEAN && p.done) || (p.hidden && p.kind == Kind.CARRY) || (p.kind == Kind.EAT && p.done) || (p.kind == Kind.DIG && p.done && p.model == "snowbank")
            if (e.enabled == gone) e.enabled = !gone
            propTags[p.id]?.let { t ->
                val spent = p.knocked || p.retired || (p.done && p.kind != Kind.DROP && p.kind != Kind.HIDE && p.kind != Kind.DOOR && p.kind != Kind.TRAIL && p.kind != Kind.GATE)
                if (t.enabled == spent) t.enabled = !spent
            }
            if (p.knocked) e.transform().eulerAngles = EulerAngles(0f, 0f, 80f)
            if (p.kind == Kind.BITE && p.done) e.transform().setScaleVector(Vector3(1f, 0.4f, 1f))
            if (p.kind == Kind.DIG && p.done && p.model == "mound") e.transform().setScaleVector(Vector3(1f, 0.3f, 1f))
            if (p.kind == Kind.GATE && p.done) e.transform().setScaleVector(Vector3(1.2f))
        }
        deedInProgress(l, syncDt())
        if (l is Forest) propViews["log"]?.let { if (it.enabled != l.bridge) it.enabled = l.bridge }
        if (l is Kitchen && l.framed) propViews["flour"]?.let { it.setPos(Vector3(l.cat.at.x.toFloat(), 0.08f, l.cat.at.z.toFloat())) }
        // The paper trail.
        if (l is Living) {
            val pts = l.trail.points
            while (trailPool.size < pts.size - 1 && trailPool.size < 160) trailPool += model(MeshResource.createBox(Vector3(1f, 0.002f, 0.025f), 0f), paperMat).also { levelRoot.addChild(it) }
            for (k in 0 until minOf(pts.size - 1, trailPool.size)) {
                val a = pts[k]; val b = pts[k + 1]
                val m = trailPool[k]
                m.setPos(Vector3(((a.x + b.x) / 2).toFloat(), 0.003f, ((a.z + b.z) / 2).toFloat()))
                m.transform().setScaleVector(Vector3(a.dist(b).toFloat() + 0.005f, 1f, 1f))
                m.transform().eulerAngles = EulerAngles(0f, -Math.toDegrees(kotlin.math.atan2(b.z - a.z, b.x - a.x)).toFloat(), 0f)
            }
        }
        // Where something carried can go.
        for ((id, ring) in dropMarks) {
            val show = l.husky.carrying != null && !(l.props[id]?.done ?: false)
            if (ring.enabled != show) ring.enabled = show
        }
        dashRing?.let {
            if (it.enabled != dashMode) it.enabled = dashMode
            it.setPos(Vector3(l.husky.at.x.toFloat(), 0.01f, l.husky.at.z.toFloat()))
        }
        scentMark?.let { s ->
            val on = scent != null
            if (s.enabled != on) s.enabled = on
            if (scent != null) {
                // A green wisp running from the dog toward the bone, over and over.
                val k = (clock % 1.2) / 1.2
                val p = l.husky.at + (scent - l.husky.at) * k
                s.setPos(Vector3(p.x.toFloat(), 0.06f + 0.02f * sin(clock * 9).toFloat(), p.z.toFloat()))
            }
        }
    }

    private fun placeChar(id: String, a: Animal, visible: Boolean) {
        val (e, anim) = chars[id] ?: return
        if (e.enabled != visible) e.enabled = visible
        e.setPos(Vector3(a.at.x.toFloat(), 0f, a.at.z.toFloat()))
        e.transform().eulerAngles = EulerAngles(0f, yawOf(a.heading), 0f)
        anim?.play(a.anim)
    }

    /** A core heading (0 = -z) as the yaw that turns a model (facing +z) that way. */
    private fun yawOf(h: Double) = 180f - Math.toDegrees(h).toFloat()

    /**
     * The gold trophy's dream: on a snowfield under the aurora, the husky (in a little crown of
     * light) pulls the sled round and round with the roe deer riding it.
     */
    fun dream(t: Double) {
        if (built != "dream") {
            built = "dream"
            levelRoot.destroy()
            hits.clear(); pinchBoxes.clear(); propViews.clear(); propTags.clear(); chars.clear(); trailPool.clear(); dropMarks.clear()
            cone = null; dashRing = null; scentMark = null
            levelRoot = Entity().also { stage.addChild(it) }
            box(Vector3(1.68f, 0.06f, 1.08f), Vector3(0f, -0.031f, 0f), darkWood)
            levelRoot.addChild(model(quad(1.6f, 1.0f, 2.5f), tex["snow"]?.let { textured(it, 0.45f) } ?: snowMat).apply {
                setPos(Vector3(0f, 0.001f, 0f)); transform().eulerAngles = EulerAngles(-90f, 0f, 0f)
            })
            for (k in 0 until 8) {
                val a = k * Math.PI / 4
                models["pine"]?.clone(Entity.CloneOptions(true, true))?.let { tr ->
                    tr.setScale(0.32f + 0.08f * (k % 3))
                    tr.setPos(Vector3((0.68 * cos(a)).toFloat(), 0f, (0.42 * sin(a)).toFloat()))
                    levelRoot.addChild(tr)
                }
            }
            addChar("husky", "husky", 0.17f)
            addChar("roe", "roe", 0.15f)
            models["sled"]?.clone(Entity.CloneOptions(true, true))?.let { sl -> sl.setScale(0.06f); propViews["sled"] = sl; levelRoot.addChild(sl) }
            propViews["crown"] = model(MeshResource.createTorus(0.025f, 0.006f), markMat).also { levelRoot.addChild(it) }
            receive(levelRoot)
        }
        val a = t * 0.9
        fun at(off: Double) = P(0.4 * cos(a - off), 0.26 * sin(a - off))
        val h = at(0.0)
        chars["husky"]?.let { (e, an) -> e.setPos(Vector3(h.x.toFloat(), 0f, h.z.toFloat())); e.transform().eulerAngles = EulerAngles(0f, yawOf(headOfPath(a)), 0f); an?.play("run") }
        val s = at(0.45)
        propViews["sled"]?.let { it.setPos(Vector3(s.x.toFloat(), 0f, s.z.toFloat())); it.transform().eulerAngles = EulerAngles(0f, yawOf(headOfPath(a - 0.45)), 0f) }
        chars["roe"]?.let { (e, an) -> e.setPos(Vector3(s.x.toFloat(), 0.03f, s.z.toFloat())); e.transform().eulerAngles = EulerAngles(0f, yawOf(headOfPath(a - 0.45)), 0f); an?.play("sit") }
        propViews["crown"]?.let { it.setPos(Vector3(h.x.toFloat(), 0.2f, h.z.toFloat())); it.transform().eulerAngles = EulerAngles(0f, (t * 90).toFloat(), 0f) }
        if ((t * 10).toInt() % 3 == 0) snowPuff.emit(Vec3(h.x, 0.01, h.z), 2, 0.15, 0.6, 0.006f)
    }

    /** The heading of the dream's oval path at angle [a] (radians, the core's heading convention). */
    private fun headOfPath(a: Double): Double {
        val dx = -0.4 * sin(a)
        val dz = 0.26 * cos(a)
        return kotlin.math.atan2(dx, -dz)
    }

    /** A deed finished: a last burst of what it scattered (the pillow's feathers are puffed by the game). */
    fun deedDone(p: com.vrgames.husky.core.Prop?) {
        p ?: return
        val v = Vec3(p.pos.x, 0.03, p.pos.z)
        if (p.kind == Kind.BITE) propViews[p.id]?.transform()?.eulerAngles = EulerAngles(0f, 0f, 0f)
        when (p.kind) {
            Kind.EAT -> crumbs.emit(v + Vec3(0.0, p.lift, 0.0), 14, 0.15, 0.9, 0.005f, Vec3(0.0, -0.2, 0.0))
            Kind.DIG -> snowPuff.emit(v, 26, 0.35, 1.0, 0.009f, Vec3(0.0, 0.1, 0.0))
            Kind.CLEAN -> stars.emit(v + Vec3(0.0, 0.03, 0.0), 14, 0.2, 0.8, 0.005f)
            Kind.PUSH -> pushDust.emit(v, 12, 0.15, 0.8, 0.008f, Vec3(0.0, 0.05, 0.0))
            else -> Unit
        }
    }

    /**
     * While the dog is at a deed, the thing shows it (v0.2): a cushion jerks and sheds feathers, food gets smaller and
     * drops crumbs, a pushed stool raises dust, snow sprays from digging, tidying leaves a sparkle.
     */
    private fun deedInProgress(l: Level, dt: Double) {
        val p = l.husky.doing ?: return
        val e = propViews[p.id] ?: return
        // How far along the deed is (a deed broken off keeps its progress: doingLeft then starts lower).
        val k = ((p.seconds - l.husky.doingLeft) / p.seconds.coerceAtLeast(0.1)).coerceIn(0.0, 1.0)
        val v = Vec3(p.pos.x, 0.03 + p.lift, p.pos.z)
        deedFxClock -= dt
        val burst = deedFxClock <= 0
        if (burst) deedFxClock = 0.25
        when (p.kind) {
            Kind.BITE -> {
                val jerk = (sin(clock * 30) * 6 * (1 - k * 0.5)).toFloat()
                e.transform().eulerAngles = EulerAngles(0f, jerk, jerk * 0.5f)
                if (burst) feathers.emit(v + Vec3(0.0, 0.04, 0.0), 6, 0.25, 1.8, 0.007f, Vec3(0.0, 0.12, 0.0))
            }
            Kind.EAT -> {
                e.transform().setScaleVector(Vector3((1.0 - 0.6 * k).toFloat()))
                if (burst) crumbs.emit(v, 3, 0.1, 0.7, 0.004f, Vec3(0.0, -0.15, 0.0))
            }
            Kind.PUSH -> if (burst) pushDust.emit(Vec3(p.pos.x, 0.01, p.pos.z), 3, 0.08, 0.7, 0.007f, Vec3(0.0, 0.04, 0.0))
            Kind.DIG -> if (burst) snowPuff.emit(Vec3(p.pos.x, 0.02, p.pos.z), 5, 0.25, 0.8, 0.007f, Vec3(0.0, 0.12, 0.0))
            Kind.CLEAN -> if (burst) stars.emit(v, 2, 0.1, 0.5, 0.004f)
            else -> Unit
        }
    }

    fun puff(at: P, kind: String) {
        val v = Vec3(at.x, 0.04, at.z)
        when (kind) {
            "feathers" -> feathers.emit(v, 40, 0.4, 2.5, 0.008f, Vec3(0.0, 0.15, 0.0))
            "snow" -> snowPuff.emit(v, 20, 0.3, 1.0, 0.008f, Vec3(0.0, 0.1, 0.0))
            "shards" -> shards.emit(v, 18, 0.35, 1.4, 0.008f, Vec3(0.0, -0.2, 0.0))
            "water" -> waterDrops.emit(v, 24, 0.3, 0.9, 0.006f, Vec3(0.0, -0.25, 0.0))
            "soil" -> soil.emit(v, 22, 0.25, 1.2, 0.007f, Vec3(0.0, -0.2, 0.0))
            "flour" -> flourDust.emit(v + Vec3(0.0, 0.03, 0.0), 60, 0.25, 2.2, 0.012f, Vec3(0.0, 0.05, 0.0))
            else -> stars.emit(v + Vec3(0.0, 0.08, 0.0), 16, 0.3, 0.8, 0.006f)
        }
    }

    private var lastSyncClock = 0.0

    // ---- 狗狗视角 ----

    /** How far the dog view has grown in (0 table, 1 dog view): eased both ways. */
    private var dogK = 0.0
    private var follow = Vec3(0.0, 0.0, 0.0)

    /**
     * Grows the level [DOG_SCALE] times round [l]'s dog, which then stands about a metre in front of the player at
     * floor level, half as tall as a person; [on] false brings the table back. The world only slides, never turns.
     */
    fun dogView(l: Level, on: Boolean, dt: Double) {
        val target = if (on) 1.0 else 0.0
        if (dogK == target && !on) return
        dogK = if (dogK < target) minOf(target, dogK + dt / DOG_EASE_S) else maxOf(target, dogK - dt / DOG_EASE_S)
        val e = dogK * dogK * (3 - 2 * dogK)
        val s = 1.0 + (DOG_SCALE - 1.0) * e
        // Follow the dog gently, so its small hops do not shake the world.
        val dog = Vec3(l.husky.at.x, 0.0, l.husky.at.z)
        val f = (dt * 3.0).coerceAtMost(1.0)
        follow = if (dogK <= 0.0) dog else Vec3(follow.x + (dog.x - follow.x) * f, 0.0, follow.z + (dog.z - follow.z) * f)
        // The player's feet in the diorama's frame are FLOOR_DROP below and PLAYER_Z toward them; the dog goes a metre ahead.
        val tx = 0.0
        val ty = -FLOOR_DROP * e
        val tz = (PLAYER_Z - DOG_AHEAD) * e
        stage.transform().setScaleVector(Vector3(s.toFloat()))
        stage.setPos(Vector3((tx - follow.x * s * e).toFloat(), ty.toFloat(), (tz - follow.z * s * e).toFloat()))
    }

    /** Seconds since the last [sync] (it runs once a frame, after [frame]). */
    private fun syncDt(): Double {
        val d = (clock - lastSyncClock).coerceIn(0.0, 0.1)
        lastSyncClock = clock
        return d
    }

    fun frame(dt: Double) {
        clock += dt
        feathers.update(dt, 2.5)
        shards.update(dt, 1.2); waterDrops.update(dt, 1.5); soil.update(dt, 1.5); flourDust.update(dt, 2.5); crumbs.update(dt, 1.5); pushDust.update(dt, 2.0)
        snowPuff.update(dt)
        stars.update(dt)
    }

    fun useIbl(t: TextureResource) {
        iblSource.components.set(ImageBasedLightComponent(ImageBasedLightSource.Single(t), 9f))
        receive(levelRoot)
    }

    private fun receive(e: Entity) {
        if (iblSource.components[ImageBasedLightComponent::class.java] == null) return
        if (e.components[ModelComponent::class.java] != null && e.components[ImageBasedLightReceiverComponent::class.java] == null) {
            e.components.set(ImageBasedLightReceiverComponent(iblSource))
            e.components.set(EnvironmentLightingSettingsComponent(0f))
        }
        e.getChildren().forEach(::receive)
    }

    companion object {
        const val TILE = 0.1
        val MODEL_H = Looks.MODEL_H
        /** Turns for the characters that do not face +z (their heads, checked on side renders: the rigs mislabel some). */
        val CHAR_YAW = mapOf("husky" to 180f, "owner" to 180f, "roe" to 90f)
        fun setMaterials(e: Entity, m: Material) {
            e.components[ModelComponent::class.java]?.materials?.let { mats -> for (i in 0 until mats.size) mats[i] = m }
            e.getChildren().forEach { setMaterials(it, m) }
        }

        /** Turns for AI models that do not face +z (checked on their previews). */
        val MODEL_YAW = mapOf<String, Double>()
        /** 狗狗视角: the level grown this much (the dog about 1.2 m tall), eased in and out over [DOG_EASE_S]. */
        const val DOG_SCALE = 8.0
        const val DOG_EASE_S = 1.2
        /** The diorama's top is this far above the floor, and the player this far toward them from its middle. */
        const val FLOOR_DROP = 0.45
        const val PLAYER_Z = 1.15
        const val DOG_AHEAD = 1.1
        /** The dog's mouth above the floor (the dog is drawn 0.15 m tall). */
        const val MOUTH_Y = 0.075f
    }
}
