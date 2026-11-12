package io.github.ultrafeed.app4pda.feature.gallery.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.app.MainActivity
import io.github.ultrafeed.app4pda.app.navigation.AppDestination
import io.github.ultrafeed.app4pda.core.images.AppImageSource
import io.github.ultrafeed.app4pda.core.images.ImageOrigin
import io.github.ultrafeed.app4pda.core.ui.components.AppImage
import io.github.ultrafeed.app4pda.core.ui.components.AppLoadingIndicator
import io.github.ultrafeed.app4pda.core.ui.theme.AppTheme
import io.github.ultrafeed.app4pda.feature.settings.data.SettingsRepository
import org.koin.compose.koinInject

@Composable
internal fun GalleryDestination(
    activity: MainActivity,
    destination: AppDestination.Gallery,
    onClose: () -> Unit,
) {
    val window = activity.window
    DisposableEffect(window) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        val previousBehavior = controller.systemBarsBehavior
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller.systemBarsBehavior = previousBehavior
            // Recreated windows inherit immersive flags; the calling shell always shows its bars.
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    if (destination.images.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
    } else {
        val settings: SettingsRepository = koinInject()
        val appearance by settings.snapshots.collectAsStateWithLifecycle()
        AppTheme(
            true,
            appearance.accentColor,
            appearance.animationsEnabled,
            appearance.useSystemAccent,
        ) {
            ImageGallery(destination.images, destination.origin, destination.page, onClose)
        }
    }
}

@Composable
fun ImageGallery(
    images: List<String>,
    origin: ImageOrigin,
    initial: Int = 0,
    onClose: () -> Unit,
) {
    val pager =
        rememberPagerState(
            initialPage = initial.coerceIn(0, maxOf(0, images.lastIndex)),
            pageCount = { images.size },
        )
    var zoomed by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.statusBarsPadding().padding(12.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(
                            R.string.gallery_position,
                            pager.currentPage + 1,
                            images.size,
                        ),
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(modifier = Modifier, onClick = onClose) {
                        Icon(Icons.Default.Close, null)
                    }
                }
            }
            HorizontalPager(
                state = pager,
                userScrollEnabled = !zoomed,
                modifier = Modifier.weight(1f),
            ) { page ->
                ZoomableScreenshot(images[page], origin) { value ->
                    if (page == pager.currentPage) zoomed = value
                }
            }
        }
    }
}

@Composable
private fun ZoomableScreenshot(
    url: String,
    origin: ImageOrigin,
    onZoom: (Boolean) -> Unit,
) {
    var scale by rememberSaveable(url) { mutableFloatStateOf(1f) }
    var x by rememberSaveable(url) { mutableFloatStateOf(0f) }
    var y by rememberSaveable(url) { mutableFloatStateOf(0f) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var loaded by remember(url) { mutableStateOf<Boolean?>(null) }
    var attempt by remember(url) { mutableIntStateOf(0) }
    LaunchedEffect(scale) { onZoom(scale > 1f) }
    val transform = rememberTransformableState { _, zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        val maxX = size.width * (scale - 1f) / 2f
        val maxY = size.height * (scale - 1f) / 2f
        x = (x + pan.x).coerceIn(-maxX, maxX)
        y = (y + pan.y).coerceIn(-maxY, maxY)
    }
    Box(
        Modifier.fillMaxSize()
            .onSizeChanged { size = it }
            .transformable(state = transform, canPan = { scale > 1f })
            .pointerInput(url) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else 3f
                        x = 0f
                        y = 0f
                    },
                )
            },
    ) {
        key(attempt) {
            AppImage(
                AppImageSource.Url(url, origin),
                null,
                "",
                Modifier.fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = x,
                        translationY = y,
                    ),
                onResult = { loaded = it },
            )
        }
        if (loaded == null) AppLoadingIndicator(Modifier.align(Alignment.Center))
        if (loaded == false)
            FilledTonalButton(
                onClick = {
                    loaded = null
                    attempt++
                },
                modifier = Modifier.align(Alignment.Center),
            ) {
                Text(stringResource(R.string.action_retry))
            }
    }
}
