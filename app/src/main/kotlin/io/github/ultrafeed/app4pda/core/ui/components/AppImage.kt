package io.github.ultrafeed.app4pda.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.crossfade
import io.github.ultrafeed.app4pda.core.images.AppImageSource
import io.github.ultrafeed.app4pda.core.images.AppImages
import io.github.ultrafeed.app4pda.core.images.imageRequest
import io.github.ultrafeed.app4pda.core.ui.theme.LocalAppAnimations
import org.koin.compose.koinInject

@Composable
fun AppImage(
    source: AppImageSource?,
    cacheKey: String?,
    title: String,
    modifier: Modifier = Modifier,
    onResult: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val images: AppImages = koinInject()
    val loader = images.loader
    val animations = LocalAppAnimations.current
    val result by rememberUpdatedState(onResult)
    var loaded by remember(source, cacheKey) { mutableStateOf(false) }
    val request =
        remember(context, source, cacheKey, animations) {
            source
                ?.takeUnless {
                    it is AppImageSource.Url && it.value.isBlank()
                }
                ?.let {
                    imageRequest(context, it, cacheKey)
                        .newBuilder()
                        .crossfade(if (animations) 200 else 0)
                        .build()
                }
        }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (!loaded)
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    title.take(1),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        if (request != null)
            AsyncImage(
                request,
                null,
                imageLoader = loader,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                onSuccess = {
                    loaded = true
                    result(true)
                },
                onError = {
                    loaded = false
                    result(false)
                },
            )
    }
}
