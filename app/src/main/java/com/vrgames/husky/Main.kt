package com.vrgames.husky

import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.Form
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.WindowContainer
import com.pico.spatial.ui.foundation.dsl.WindowContainerSize
import com.vrgames.husky.content.AlbumWindow
import com.vrgames.husky.content.HuskyStage
import com.vrgames.husky.content.ListWindow

const val LIST_ID = "List"
const val ALBUM_ID = "Album"

/**
 * Three linked spaces: the diorama of each level in front of the seated player (a full-immersion
 * Stage), the 拆家清单 (levels, goals, trophies) and the 相册 (the funny moments). They share
 * [com.vrgames.husky.game.Game].
 */
fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultStage {
            PicoTheme { HuskyStage() }
        }
        WindowContainer(LIST_ID, Form.Planar, { defaultSize = WindowContainerSize(820.dp, 1000.dp) }) {
            PicoTheme { ListWindow() }
        }
        WindowContainer(ALBUM_ID, Form.Planar, { defaultSize = WindowContainerSize(820.dp, 1000.dp) }) {
            PicoTheme { AlbumWindow() }
        }
    }
