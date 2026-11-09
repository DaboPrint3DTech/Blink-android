package com.daboprint3dtech.blink.widget

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.Image
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.appwidget.appWidgetBackground
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daboprint3dtech.blink.BlinkApp
import com.daboprint3dtech.blink.MainActivity
import com.daboprint3dtech.blink.data.thumbs.ThumbnailCache
import java.io.File
import java.time.Duration
import java.time.Instant

private val CameraKeyParam = ActionParameters.Key<String>("camera_key")

/** Widget che mostra le miniature (dalla cache su disco) delle telecamere preferite. */
class BlinkWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetSnapshotStore.read(context)
        val thumbsDir = File(context.filesDir, "thumbnails")
        provideContent { WidgetContent(context, snapshot, thumbsDir) }
    }
}

class BlinkWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BlinkWidget()
}

/** Aggiorna le miniature mostrate nel widget (scatto per le telecamere visibili). */
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val container = (context.applicationContext as BlinkApp).container
        container.auth.init()
        container.launchDetached {
            val prefs = container.cameraPreferencesStore.current()
            val cameras = container.repository.refresh().cameras
            val favorites = cameras.filter { prefs.isFavorite(it.key) }.ifEmpty { cameras }
            container.repository.snapshot(favorites.filter { it.isOnline })
            container.widgetUpdater.update()
        }
    }
}

private fun safeKey(key: String) = key.replace(Regex("[^A-Za-z0-9_-]"), "_")

@Composable
private fun WidgetContent(context: Context, snapshot: WidgetSnapshot, thumbsDir: File) {
    Column(
        GlanceModifier.fillMaxSize().appWidgetBackground().background(Color(0xF21A1F24)).padding(10.dp),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Blink Cam",
                style = TextStyle(color = ColorProvider(Color.White), fontWeight = FontWeight.Bold, fontSize = 15.sp),
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                "Aggiorna",
                style = TextStyle(color = ColorProvider(Color(0xFF8FD3FF)), fontSize = 12.sp),
                modifier = GlanceModifier.clickable(actionRunCallback<RefreshWidgetAction>()).padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        Spacer(GlanceModifier.height(6.dp))
        when {
            !snapshot.loggedIn -> Centered("Accedi nell'app per vedere le telecamere")
            snapshot.cameras.isEmpty() -> Centered("Nessuna telecamera. Aggiungi preferiti nell'app.")
            else -> LazyColumn {
                items(snapshot.cameras, itemId = { it.key.hashCode().toLong() }) { cam ->
                    CameraTile(context, cam, thumbsDir)
                }
            }
        }
    }
}

/** Decodifica la miniatura ridimensionata: i widget hanno un limite di memoria per le immagini. */
private fun decodeScaled(file: File, maxWidth: Int = 500): android.graphics.Bitmap? {
    if (!file.exists()) return null
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxWidth) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}

@Composable
private fun CameraTile(context: Context, cam: WidgetCamera, thumbsDir: File) {
    val file = File(thumbsDir, "${safeKey(cam.key)}.jpg")
    val bitmap = decodeScaled(file)
    val openApp = actionStartActivity(
        Intent(context, MainActivity::class.java)
            .setAction("open-camera")
            .putExtra("camera_key", cam.key)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
    )
    Box(
        GlanceModifier.fillMaxWidth().height(120.dp).padding(bottom = 8.dp).cornerRadius(14.dp).clickable(openApp),
    ) {
        if (bitmap != null) {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = cam.name,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize().cornerRadius(14.dp),
            )
        } else {
            Box(GlanceModifier.fillMaxSize().cornerRadius(14.dp).background(Color(0xFF2A3139))) {}
        }
        Column(GlanceModifier.fillMaxSize().padding(8.dp), verticalAlignment = Alignment.Bottom) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    cam.name,
                    style = TextStyle(color = ColorProvider(Color.White), fontWeight = FontWeight.Bold, fontSize = 14.sp),
                    modifier = GlanceModifier.defaultWeight(),
                )
                if (!cam.online) Badge("offline", Color(0xFFC62828))
                else if (cam.lowBattery) Badge("batt.", Color(0xFFF9A825))
            }
            Text(
                relativeMillis(cam.thumbnailMillis),
                style = TextStyle(color = ColorProvider(Color(0xFFE0E0E0)), fontSize = 11.sp),
            )
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(
        text,
        style = TextStyle(color = ColorProvider(Color.White), fontSize = 10.sp, fontWeight = FontWeight.Medium),
        modifier = GlanceModifier.background(color).cornerRadius(6.dp).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun Centered(text: String) {
    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = TextStyle(color = ColorProvider(Color(0xFFBBBBBB)), fontSize = 13.sp))
    }
}

private fun relativeMillis(millis: Long?): String {
    if (millis == null || millis <= 0) return "—"
    val d = Duration.between(Instant.ofEpochMilli(millis), Instant.now())
    return when {
        d.toMinutes() < 1 -> "adesso"
        d.toMinutes() < 60 -> "${d.toMinutes()} min fa"
        d.toHours() < 24 -> "${d.toHours()} h fa"
        else -> "${d.toDays()} g fa"
    }
}
