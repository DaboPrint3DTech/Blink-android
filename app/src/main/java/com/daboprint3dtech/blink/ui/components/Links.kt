package com.daboprint3dtech.blink.ui.components

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/** Canale YouTube di DaboPrint3D. */
const val DABOPRINT3D_YOUTUBE_URL = "https://www.youtube.com/@daboPrint3d"

fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
