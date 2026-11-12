package io.github.ultrafeed.app4pda.data.googleplay

import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.telephony.TelephonyManager
import com.aurora.gplayapi.PlayClient
import com.aurora.gplayapi.PlayException
import com.aurora.gplayapi.device.DeviceProfile
import com.aurora.gplayapi.device.DeviceRegistry
import com.aurora.gplayapi.engine.OkHttpEngine
import com.aurora.gplayapi.net.HttpEngine
import com.aurora.gplayapi.session.DefaultPlaySessions
import java.util.Locale
import java.util.Properties
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class AnonymousPlaySessionProvider(context: Context, http: PlayHttpClient) {
    private val context = context.applicationContext
    private val engine = OkHttpEngine(http.client)
    private val clients = ConcurrentHashMap<Locale, LocalizedPlayClient>()

    fun requestLocale(): Locale {
        val language = context.resources.configuration.locales[0]?.language
        return if (language == "ru") Locale.forLanguageTag("ru") else Locale.ENGLISH
    }

    suspend fun <T> execute(locale: Locale, operation: suspend (PlayClient) -> T): T {
        val localized = clients.computeIfAbsent(locale, ::createClient)
        val generation = localized.generation
        return try {
            operation(localized.client)
        } catch (_: PlayException.Unauthorized) {
            currentCoroutineContext().ensureActive()
            localized.invalidate(generation)
            operation(localized.client)
        }
    }

    private fun createClient(locale: Locale): LocalizedPlayClient {
        val profile = DeviceRegistry.parse("app4pda", deviceProperties(locale))
        return LocalizedPlayClient(engine, profile, locale)
    }

    private fun Properties.addDeviceCapabilities() {
        val metrics = context.resources.displayMetrics
        val config = context.resources.configuration
        setDeviceProperty("Screen.Density", metrics.densityDpi)
        setDeviceProperty("Screen.Width", metrics.widthPixels)
        setDeviceProperty("Screen.Height", metrics.heightPixels)
        setDeviceProperty("TouchScreen", config.touchscreen)
        setDeviceProperty("Keyboard", config.keyboard)
        setDeviceProperty("Navigation", config.navigation)
        setDeviceProperty(
            "ScreenLayout",
            config.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK,
        )
        setDeviceProperty(
            "HasHardKeyboard",
            config.keyboard != Configuration.KEYBOARD_NOKEYS,
        )
        setDeviceProperty(
            "HasFiveWayNavigation",
            config.navigation == Configuration.NAVIGATION_DPAD,
        )
        val activity = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also(activity::getMemoryInfo)
        setDeviceProperty("LowRamDevice", if (activity.isLowRamDevice) 1 else 0)
        setDeviceProperty("MaxNumOfCPUCores", Runtime.getRuntime().availableProcessors())
        setDeviceProperty("TotalMemoryBytes", memory.totalMem)
        setDeviceProperty("Platforms", Build.SUPPORTED_ABIS.joinToString(","))
        setDeviceProperty(
            "Features",
            context.packageManager.systemAvailableFeatures
                .mapNotNull { it.name }
                .joinToString(",")
                .ifBlank { EMPTY_DEVICE_LIST },
        )
        setDeviceProperty(
            "SharedLibraries",
            context.packageManager.systemSharedLibraryNames.orEmpty().joinToString(",").ifBlank {
                EMPTY_DEVICE_LIST
            },
        )
        setDeviceProperty(
            "GL.Version",
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
                .deviceConfigurationInfo
                .reqGlEsVersion,
        )
        setDeviceProperty("GL.Extensions", EMPTY_DEVICE_LIST)
    }

    private fun deviceProperties(locale: Locale) =
        Properties().apply {
            fun property(name: String, value: Any?) {
                setProperty(name, value?.toString().orEmpty())
            }
            property("UserReadableName", Build.MODEL)
            property("Build.HARDWARE", Build.HARDWARE)
            property("Build.FINGERPRINT", Build.FINGERPRINT)
            property("Build.BRAND", Build.BRAND)
            property("Build.DEVICE", Build.DEVICE)
            property("Build.MODEL", Build.MODEL)
            property("Build.MANUFACTURER", Build.MANUFACTURER)
            property("Build.PRODUCT", Build.PRODUCT)
            property("Build.ID", Build.ID)
            property("Build.BOOTLOADER", Build.BOOTLOADER.ifBlank { Build.UNKNOWN })
            property("Build.RADIO", "unknown")
            property("Build.VERSION.SDK_INT", Build.VERSION.SDK_INT)
            property("Build.VERSION.RELEASE", Build.VERSION.RELEASE)
            addDeviceCapabilities()
            property("Client", "android-google")
            val playDefaults = DeviceRegistry.default
            listOf("GSF.version", "Vending.version", "Vending.versionString").forEach { key ->
                property(key, playDefaults.property(key))
            }
            property("Roaming", "mobile-notroaming")
            property("TimeZone", TimeZone.getDefault().id)
            val phone = context.getSystemService(TelephonyManager::class.java)
            property("CellOperator", phone?.networkOperator.orEmpty().ifBlank { NO_OPERATOR })
            property("SimOperator", phone?.simOperator.orEmpty().ifBlank { NO_OPERATOR })
            property("Locales", "$locale")
        }
}

internal fun Properties.setDeviceProperty(name: String, value: Any?) {
    setProperty(name, value?.toString().orEmpty())
}

private class LocalizedPlayClient(engine: HttpEngine, profile: DeviceProfile, locale: Locale) {
    private val sessions = DefaultPlaySessions(engine, profile, locale)
    val client = PlayClient(engine, sessions)

    @Volatile
    var generation = 0
        private set

    @Synchronized
    fun invalidate(failedGeneration: Int) {
        if (generation != failedGeneration) return
        sessions.invalidateAnonymous()
        generation++
    }
}

// Aurora requires nonblank device properties even when an optional list or operator is absent.
private const val EMPTY_DEVICE_LIST = ","
private const val NO_OPERATOR = "0"
