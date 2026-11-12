package io.github.ultrafeed.app4pda.app.navigation

import androidx.navigation3.runtime.NavMetadataKey

/** Scene-local ownership survives concurrent transitions between independent stacks. */
internal object MainSectionMetadata : NavMetadataKey<MainSection>

internal fun mainNavigationForward(
    initial: MainSection?,
    target: MainSection?,
    pop: Boolean,
): Boolean =
    if (initial != null && target != null && initial != target)
        MainSection.entries.indexOf(target) >
            MainSection.entries.indexOf(
                initial,
            )
    else !pop
