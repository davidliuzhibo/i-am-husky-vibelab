package com.vrgames.husky.render

import android.util.Log
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.animation.AnimationPlayConfig
import com.pico.spatial.core.ecs.animation.AnimationPlaybackController
import com.pico.spatial.core.ecs.animation.AnimationTransitionMode
import com.pico.spatial.core.ecs.resource.AnimationResource

/**
 * Plays a rigged model's animations (glTF clips made by tools/blender/rig_animal.py) by name, with a
 * short crossfade between them. Looping clips are repeated many times; one-shot clips stay on their
 * last frame until the next [play].
 */
class Animator(private val e: Entity, private val tag: String) {
    private val clips = HashMap<String, AnimationResource>()
    private var current: String? = null
    private var ctrl: AnimationPlaybackController? = null

    init {
        runCatching { e.getAnimationResources() }.getOrNull()?.forEach { r -> clips[r.getName()] = r }
        Log.i(TAG, "$tag clips: ${clips.keys.sorted()}")
    }

    val names: Set<String> get() = clips.keys

    fun play(name: String, loop: Boolean = LOOPING.contains(name), fade: Float = 0.2f) {
        if (name == current) return
        val res = clips[name] ?: clips.entries.firstOrNull { it.key.endsWith(name) }?.value ?: return
        current = name
        runCatching {
            val r = if (loop) res.repeat(LOOPS) else res
            ctrl = e.playAnimation(r, AnimationPlayConfig(fade, AnimationTransitionMode.CROSSFADE, 0, 1f))
        }.onFailure { Log.w(TAG, "$tag play $name: ${it.message}") }
    }

    companion object {
        private const val TAG = "HuskyAnim"
        private const val LOOPS = 100000
        val LOOPING = setOf("idle", "walk", "run", "tug", "dig", "dash", "search", "angry", "carry")
    }
}
