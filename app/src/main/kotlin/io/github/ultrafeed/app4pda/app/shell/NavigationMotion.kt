package io.github.ultrafeed.app4pda.app.shell

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MotionScheme
import androidx.compose.ui.unit.IntOffset
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.get
import androidx.navigation3.scene.Scene
import io.github.ultrafeed.app4pda.app.navigation.MainSectionMetadata
import io.github.ultrafeed.app4pda.app.navigation.mainNavigationForward

/** NavDisplay owns the transition; the same Material motion tokens apply to every stack. */
internal class NavigationMotion(private val enabled: Boolean, private val scheme: MotionScheme) {
    fun sceneTransform(
        scope: AnimatedContentTransitionScope<Scene<NavKey>>,
        pop: Boolean,
    ): ContentTransform =
        transform(
            scope,
            mainNavigationForward(
                scope.initialState.metadata[MainSectionMetadata],
                scope.targetState.metadata[MainSectionMetadata],
                pop,
            ),
        )

    fun transform(scope: AnimatedContentTransitionScope<*>, forward: Boolean): ContentTransform =
        with(scope) {
            if (!enabled) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                val direction =
                    if (forward) AnimatedContentTransitionScope.SlideDirection.Start
                    else AnimatedContentTransitionScope.SlideDirection.End
                val spatial = scheme.defaultSpatialSpec<IntOffset>()
                val effects = scheme.defaultEffectsSpec<Float>()
                (slideIntoContainer(
                    direction,
                    spatial,
                ) + fadeIn(effects)) togetherWith
                    (slideOutOfContainer(
                        direction,
                        spatial,
                    ) + fadeOut(effects))
            }
        }
}
