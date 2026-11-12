package io.github.ultrafeed.app4pda.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.ultrafeed.app4pda.core.ui.theme.LocalAppAnimations

/** Shared busy state; disabling motion keeps an indeterminate, static indicator. */
@Composable
fun AppLoadingIndicator(modifier: Modifier = Modifier, progress: Float? = null) {
    Box(
        modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        if (progress != null)
            LinearWavyProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                waveSpeed =
                    if (LocalAppAnimations.current)
                        WavyProgressIndicatorDefaults.LinearDeterminateWavelength
                    else 0.dp,
            )
        else if (LocalAppAnimations.current) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
        else
            LinearWavyProgressIndicator(
                progress = { 0.5f },
                waveSpeed = 0.dp,
                modifier =
                    Modifier.fillMaxWidth().semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                    },
            )
    }
}
