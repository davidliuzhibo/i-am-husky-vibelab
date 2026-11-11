package com.vrgames.husky.game

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.vrgames.husky.core.Levels

/**
 * Test hook, debug builds only: plays the levels without hands (the unit tests' solver), so every
 * one can be checked on the emulator. Results go to logcat (tag HuskyTest).
 *
 *   adb shell am broadcast -a com.vrgames.husky.TEST -p com.vrgames.husky --es cmd walk
 *
 * cmd: start · level --es id <level> · begin · auto (the current level plays itself) · walk (every
 * level in order) · grant --es id <stars> · dream · innocent · view --es id "x,y,z,yaw,scale,pitch" ·
 * open --es id list|album|menu|close · reset · stop
 */
object AutoPilot {
    const val ACTION = "com.vrgames.husky.TEST"
    const val TAG = "HuskyTest"
    private val main = Handler(Looper.getMainLooper())
    var debug = false; private set
    private var walkAt = 0

    fun register(context: Context) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        debug = true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = command(i.getStringExtra("cmd") ?: "", i.getStringExtra("id"))
        }
        ContextCompat.registerReceiver(context.applicationContext, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_EXPORTED)
        Log.i(TAG, "ready")
    }

    fun ready() = Log.i(TAG, "diorama ready")

    private fun command(cmd: String, id: String?) {
        Log.i(TAG, "cmd $cmd ${id.orEmpty()}")
        when (cmd) {
            "start" -> Game.start()
            "level" -> id?.let { Game.intro(it) }
            "begin" -> Game.begin()
            "auto" -> Game.autoplay()
            "walk" -> {
                main.removeCallbacksAndMessages(null)
                walkAt = 0
                main.postDelayed(::walkStep, 300)
            }
            "grant" -> Game.grant(id?.toIntOrNull() ?: 3)
            "dream" -> Game.dream()
            "innocent" -> Game.innocent()
            "dog" -> if ((id == "off") == Game.dogView) Game.toggleDogView()
            "view" -> Game.debugView = id?.takeIf { it != "reset" }
            "open" -> Game.debugOpen = id
            "reset" -> { main.removeCallbacksAndMessages(null); Game.restart() }
            "stop" -> main.removeCallbacksAndMessages(null)
            "speed" -> Game.timeScale = id?.toDoubleOrNull() ?: 1.0
            "probe" -> Probe.start(main, id)
            else -> Log.w(TAG, "unknown command $cmd")
        }
    }

    private fun walkStep() {
        if (walkAt >= Levels.IDS.size) {
            Log.i(TAG, "walk done stars=${Game.stars}/${Game.maxStars} bronze=${Game.bronze} silver=${Game.silver} gold=${Game.gold} album=${Game.album.size}")
            return
        }
        val id = Levels.IDS[walkAt++]
        Game.intro(id)
        main.postDelayed({
            Game.begin()
            Game.autoplay()
            waitResult(id)
        }, 800)
    }

    private fun waitResult(id: String) {
        if (Game.phase == Game.Phase.RESULT) {
            val l = Game.level
            Log.i(TAG, "result $id won=${l?.won} stars=${l?.stars()} time=${"%.1f".format(l?.time ?: 0.0)} strikes=${l?.strikes}")
            main.postDelayed(::walkStep, 1500)
        } else main.postDelayed({ waitResult(id) }, 500)
    }
}
