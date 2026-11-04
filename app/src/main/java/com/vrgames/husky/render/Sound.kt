package com.vrgames.husky.render

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.LoadType
import com.pico.spatial.core.ecs.ObjectAudioComponent
import com.pico.spatial.core.ecs.audio.AudioPlayerController
import com.pico.spatial.core.ecs.resource.AudioResource
import com.vrgames.husky.core.Vec3

/** Effects, all made by tools/sfx_synth.py (see docs/素材来源.md). */
enum class Sfx(val file: String, val volume: Float) {
    START("start", 0.7f),
    /** A deed done (a bite, a grab, a push). */
    DID("did", 0.6f),
    /** The owner stops humming: he is about to turn round. */
    WARN("warn", 0.9f),
    CAUGHT("caught", 0.9f),
    GOAL("goal", 0.8f),
    CRASH("crash", 0.8f),
    WHOOSH("whoosh", 0.6f),
    HOWL("howl", 0.9f),
    BARK("bark", 0.8f),
    DROP("drop", 0.6f),
    DOOR("door", 0.8f),
    POP("pop", 0.6f),
    SIREN("siren", 0.7f),
    SNIFF("sniff", 0.6f),
    CRACK("crack", 0.8f),
    CAMERA("camera", 0.7f),
    WIN("win", 0.9f),
    LOSE("lose", 0.7f),
}

/**
 * Spatial sound from the scene: each effect has one long-lived emitter with a prepared player,
 * moved to where the sound happens and restarted (creating players per event drops frames).
 * Main thread only.
 */
class Sound(private val parent: Entity) {
    private val emitters = HashMap<Sfx, Entity>()
    private val controllers = HashMap<Sfx, AudioPlayerController>()
    var enabled = true

    /** Loads every clip; a clip that fails is skipped (logged). */
    fun load() {
        for (s in Sfx.entries) {
            runCatching {
                val res = AudioResource.load(s.file, "asset://audio/${s.file}.wav", LoadType.FROM_ASSETS)
                val e = Entity()
                e.components.set(ObjectAudioComponent(s.volume))
                parent.addChild(e)
                val c = e.prepareAudio(res)
                if (c.valid) {
                    emitters[s] = e
                    controllers[s] = c
                }
            }.onFailure { android.util.Log.w(TAG, "sfx ${s.file}: ${it.message}") }
        }
    }

    /** Plays [s] at [at] (in [parent]'s frame; by default the middle of the parent). */
    fun play(s: Sfx, at: com.vrgames.husky.core.P) = play(s, Vec3(at.x, 0.08, at.z))

    fun play(s: Sfx, at: Vec3 = Vec3(0.0, 0.1, 0.0)) {
        if (!enabled) return
        val c = controllers[s] ?: return
        emitters[s]?.setPos(at)
        runCatching {
            if (c.isPlaying()) c.stop()
            c.seekTo(0)
            c.play()
        }
    }

    companion object {
        private const val TAG = "GameSound"
    }
}
