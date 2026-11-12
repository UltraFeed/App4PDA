package io.github.ultrafeed.app4pda.feature.installed.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import io.github.ultrafeed.app4pda.core.ui.theme.LocalAppAnimations

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HomePullToRefresh(
    refreshing: Boolean,
    enabled: Boolean,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit,
) {
    val state = rememberPullToRefreshState()
    val haptics = LocalHapticFeedback.current
    val animations = LocalAppAnimations.current
    LaunchedEffect(state, haptics, enabled) {
        var signalled = false
        snapshotFlow { state.distanceFraction to state.isAnimating }
            .collect { (distance, animating) ->
                if (distance == 0f) signalled = false
                if (!enabled || animating) return@collect
                if (distance >= 1f && !signalled) {
                    signalled = true
                    haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                }
            }
    }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
        state = state,
        enabled = enabled,
        indicator = {
            if (animations)
                PullToRefreshDefaults.LoadingIndicator(
                    state = state,
                    isRefreshing = refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            else StaticRefreshIndicator(state, refreshing, Modifier.align(Alignment.TopCenter))
        },
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StaticRefreshIndicator(
    state: PullToRefreshState,
    refreshing: Boolean,
    modifier: Modifier,
) {
    PullToRefreshDefaults.IndicatorBox(
        state = state,
        isRefreshing = refreshing,
        modifier =
            modifier.size(
                LoadingIndicatorDefaults.ContainerWidth,
                LoadingIndicatorDefaults.ContainerHeight,
            ),
        containerColor = PullToRefreshDefaults.loadingIndicatorContainerColor,
        elevation = PullToRefreshDefaults.LoadingIndicatorElevation,
    ) {
        ContainedLoadingIndicator(
            progress = { if (refreshing) 0.5f else state.distanceFraction.coerceIn(0f, 1f) },
            modifier =
                Modifier.semantics {
                    if (refreshing) progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                },
            containerColor = PullToRefreshDefaults.loadingIndicatorContainerColor,
            indicatorColor = PullToRefreshDefaults.loadingIndicatorColor,
        )
    }
}
