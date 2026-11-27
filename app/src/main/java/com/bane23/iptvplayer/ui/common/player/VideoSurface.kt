package com.bane23.iptvplayer.ui.common.player

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import com.bane23.iptvplayer.player.PlaybackStatus
import com.bane23.iptvplayer.player.SurfaceReset
import com.bane23.iptvplayer.ui.common.SettingsViewModel
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import kotlinx.coroutines.flow.StateFlow

/**
 * Slika plejera, bez ugrađenih kontrola (kontrole crta Compose). Titlove iz samog sadržaja prikazuje
 * PlayerView; titl iz fajla (sa interneta) stiže kao tekst u [subtitleText] i crta se na isti način
 * preko slike, pa njegovo uključivanje ne prekida gledanje.
 *
 * Sa [holdLastFrame] poslednja slika ostaje na ekranu kad aplikacija ode u pozadinu i vrati se,
 * sve dok strim ponovo ne pošalje prvu sliku, umesto crnog ekrana.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(
    player: Player,
    aspectMode: AspectMode,
    modifier: Modifier = Modifier,
    subtitleText: StateFlow<String?>? = null,
    holdLastFrame: Boolean = true,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val lastFrame = remember { LastFrame() }
    val embeddedSubtitleView = remember { arrayOfNulls<SubtitleView>(1) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() = lastFrame.clear()
            override fun onPlayerError(error: PlaybackException) = lastFrame.clear()
            override fun onCues(cueGroup: CueGroup) {
                embeddedSubtitleView[0]?.setCues(withUserPosition(cueGroup.cues))
            }
        }
        embeddedSubtitleView[0]?.setCues(withUserPosition(player.currentCues.cues))
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            lastFrame.clear()
        }
    }
    if (holdLastFrame) {
        LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { lastFrame.capture(player) }
        // Površina nije uništena (npr. slika u slici): snimak nije potreban.
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            lastFrame.dropPending()
            // Plejer koji se posle povratka ne priprema ponovo (npr. greška) ne bi skinuo snimak.
            if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) lastFrame.clear()
        }
    }

    // Izgled titlova iz podešavanja; izmena važi odmah, i dok se nešto gleda.
    val settings by settingsViewModel.playerSettings.collectAsStateWithLifecycle()
    val fileSubtitle = subtitleText?.collectAsStateWithLifecycle()?.value
    val fileSubtitleView = remember { arrayOfNulls<SubtitleView>(1) }
    val videoView = remember { arrayOfNulls<View>(1) }
    LaunchedEffect(player) {
        // Sakrivanje i ponovno prikazivanje uništava površinu i pravi novu, bez veze sa tunelom.
        SurfaceReset.requests.collect { target ->
            val surface = videoView[0]
            if (target === player && surface != null) {
                surface.visibility = View.GONE
                surface.post { surface.visibility = View.VISIBLE }
            }
        }
    }
    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    // Fokus ostaje na Compose elementima, inače daljinski ne radi.
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    this.player = player
                    (videoSurfaceView as? SurfaceView)?.let(lastFrame::attach)
                    videoView[0] = videoSurfaceView
                    // Titlove iz sadržaja crta naš sloj umesto PlayerView-ovog, da bi poslušali
                    // izabrani položaj (vidi withUserPosition). Titl iz fajla dobija svoj sloj na
                    // istom mestu.
                    (subtitleView?.parent as? ViewGroup)?.let { frame ->
                        subtitleView?.visibility = View.GONE
                        val embedded = SubtitleView(context)
                        frame.addView(embedded, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        embedded.setCues(withUserPosition(player.currentCues.cues))
                        embeddedSubtitleView[0] = embedded
                        val extra = SubtitleView(context)
                        frame.addView(extra, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        fileSubtitleView[0] = extra
                    }
                }
            },
            update = { view ->
                view.player = player
                for (subtitles in listOfNotNull(embeddedSubtitleView[0] ?: view.subtitleView, fileSubtitleView[0])) {
                    subtitles.setStyle(
                        CaptionStyleCompat(
                            android.graphics.Color.WHITE,
                            if (settings.subtitleBackground) android.graphics.Color.BLACK else android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            android.graphics.Color.BLACK,
                            null,
                        ),
                    )
                    // Stil iz samog strima bi poništio izbor korisnika.
                    subtitles.setApplyEmbeddedStyles(false)
                    subtitles.setApplyEmbeddedFontSizes(false)
                    subtitles.setFractionalTextSize(settings.subtitleSize.fraction)
                    subtitles.setBottomPaddingFraction(settings.subtitlePosition.bottomFraction)
                }
                fileSubtitleView[0]?.setCues(
                    if (fileSubtitle.isNullOrEmpty()) emptyList() else listOf(Cue.Builder().setText(fileSubtitle).build()),
                )
                view.resizeMode = when (aspectMode) {
                    AspectMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    AspectMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    AspectMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
            onRelease = { view ->
                view.player = null
                embeddedSubtitleView[0] = null
                videoView[0] = null
                lastFrame.detach()
            },
        )
        lastFrame.shown.value?.let { bitmap ->
            Image(
                bitmap = remember(bitmap) { bitmap.asImageBitmap() },
                contentDescription = null,
                // Snimak ima oblik površine, pa ga isto razvlačenje vraća na isto mesto.
                contentScale = when (aspectMode) {
                    AspectMode.FIT -> ContentScale.Fit
                    AspectMode.FILL -> ContentScale.FillBounds
                    AspectMode.ZOOM -> ContentScale.Crop
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Titlovi iz sadržaja (npr. SRT u filmu) često sami nose mesto pri dnu slike, pa SubtitleView ne bi
 * primenio izabrani položaj. Tekstu koji stoji u donjoj polovini to mesto se briše, pa ga crta
 * podešeni razmak od dna. Tekst namerno postavljen gore i titlovi kao slike ostaju gde jesu.
 */
@OptIn(UnstableApi::class)
internal fun withUserPosition(cues: List<Cue>): List<Cue> = cues.map { cue ->
    val atBottom = when {
        cue.text == null || cue.bitmap != null || cue.verticalType != Cue.TYPE_UNSET -> false
        cue.line == Cue.DIMEN_UNSET -> false
        cue.lineType == Cue.LINE_TYPE_FRACTION -> cue.line >= 0.5f
        cue.lineType == Cue.LINE_TYPE_NUMBER -> cue.line < 0f
        else -> false
    }
    if (atBottom) cue.buildUpon().setLine(Cue.DIMEN_UNSET, Cue.TYPE_UNSET).setLineAnchor(Cue.TYPE_UNSET).build() else cue
}

/**
 * Kopija poslednje slike sa površine plejera. Snima se kad aktivnost ide u pauzu (površina tada još
 * postoji), a prikazuje tek kad Android površinu zaista uništi; skida se na prvu novu sliku.
 */
private class LastFrame : SurfaceHolder.Callback {
    val shown = mutableStateOf<Bitmap?>(null)
    private var pending: Bitmap? = null
    private var surface: SurfaceView? = null
    private var surfaceAlive = false
    private var generation = 0

    fun attach(view: SurfaceView) {
        surface = view
        surfaceAlive = view.holder.surface?.isValid == true
        view.holder.addCallback(this)
    }

    fun detach() {
        surface?.holder?.removeCallback(this)
        surface = null
        surfaceAlive = false
    }

    fun capture(player: Player) {
        val view = surface ?: return
        if (!surfaceAlive || view.width <= 0 || view.height <= 0) return
        if (player.videoSize == VideoSize.UNKNOWN) return
        val state = player.playbackState
        if (state != Player.STATE_READY && state != Player.STATE_BUFFERING) return
        // Umanjena kopija je dovoljna za par trenutaka čekanja, a zauzima malo memorije.
        val scale = minOf(1f, MAX_WIDTH.toFloat() / view.width)
        val bitmap = try {
            Bitmap.createBitmap(
                (view.width * scale).toInt().coerceAtLeast(1),
                (view.height * scale).toInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
        } catch (_: OutOfMemoryError) {
            return
        }
        val request = ++generation
        try {
            PixelCopy.request(view, bitmap, { result ->
                if (request != generation || result != PixelCopy.SUCCESS) return@request
                if (surfaceAlive) pending = bitmap else shown.value = bitmap
            }, Handler(Looper.getMainLooper()))
        } catch (_: IllegalArgumentException) {
            // Površina je u međuvremenu nestala.
        }
    }

    fun dropPending() {
        generation++
        pending = null
    }

    fun clear() {
        generation++
        pending = null
        shown.value = null
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceAlive = true
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceAlive = false
        pending?.let { shown.value = it }
        pending = null
    }

    private companion object {
        const val MAX_WIDTH = 1280
    }
}

/**
 * Drži ekran upaljen dok se nešto pušta ili učitava. Pauziran ili neuspeo sadržaj ne drži ekran:
 * uređaj ga tada gasi po svom podešavanju, da zaboravljena pauza ne prazni bateriju.
 */
@Composable
fun KeepScreenOn(status: PlaybackStatus) {
    KeepScreenOn(
        active = status == PlaybackStatus.PLAYING || status == PlaybackStatus.LOADING || status == PlaybackStatus.RETRYING,
    )
}

/** Drži ekran upaljen dok je [active]. */
@Composable
fun KeepScreenOn(active: Boolean = true) {
    val view = LocalView.current
    DisposableEffect(view, active) {
        view.keepScreenOn = active
        onDispose { view.keepScreenOn = false }
    }
}
