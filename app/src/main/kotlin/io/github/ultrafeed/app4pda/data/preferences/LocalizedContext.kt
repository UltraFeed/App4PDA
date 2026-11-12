package io.github.ultrafeed.app4pda.data.preferences

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat

/** Local resources at Application/Activity attachment; never mutate a shared Configuration. */
fun Context.withAppLanguage(language: String): Context {
    // Override only locales: copying uiMode/density would freeze system changes in Application.
    val configuration = Configuration()

    val locales =
        if (language == "default") {
            ConfigurationCompat.getLocales(Resources.getSystem().configuration)
        } else {
            LocaleListCompat.forLanguageTags(language)
        }

    ConfigurationCompat.setLocales(configuration, locales)

    val localized = createConfigurationContext(configuration)

    // Preserve the Activity in the wrapper chain for ActivityResultRegistry/LocalActivity.
    return object : ContextWrapper(this) {
        override fun getResources(): Resources = localized.resources

        override fun getAssets(): AssetManager = localized.assets
    }
}
