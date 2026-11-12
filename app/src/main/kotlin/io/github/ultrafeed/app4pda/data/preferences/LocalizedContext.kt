package io.github.ultrafeed.app4pda.data.preferences

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import java.util.Locale

/** Local resources at Application/Activity attachment; never mutate a shared Configuration. */
fun Context.withAppLanguage(language: String): Context {
    // Override only locales: copying uiMode/density would freeze system changes in Application.
    val configuration = Configuration()
    configuration.setLocales(
        if (language == "default") Resources.getSystem().configuration.locales
        else LocaleList(Locale.forLanguageTag(language)),
    )
    val localized = createConfigurationContext(configuration)
    // Preserve the Activity in the wrapper chain for ActivityResultRegistry/LocalActivity.
    return object : ContextWrapper(this) {
        override fun getResources(): Resources = localized.resources

        override fun getAssets(): AssetManager = localized.assets
    }
}
