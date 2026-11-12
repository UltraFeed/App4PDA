package io.github.ultrafeed.app4pda.core.packages

import java.io.IOException
import java.util.jar.JarFile
import java.util.logging.Level
import java.util.logging.Logger

object AbiUtils {
    private const val NATIVE_LIBRARY_PATH_PARTS = 3
    private const val ARM = 512
    private const val ARM_V5 = 2048
    private const val ARM_V7 = 4096
    private const val ARM_64 = 65536
    private const val X86 = 8192
    private const val X86_64 = 32768
    private const val MIPS = 16384
    private const val MIPS_64 = 131072

    @JvmStatic
    fun readApkAbiFlags(path: String): Int {
        return try {
            JarFile(path).use { archive ->
                val architectures = ArrayList<String>()
                for (entry in archive.entries()) {
                    if (!entry.name.startsWith("lib/")) continue
                    // Java String.split discards trailing empty parts.
                    val segments = entry.name.split('/').dropLastWhile { it.isEmpty() }
                    if (
                        segments.size == NATIVE_LIBRARY_PATH_PARTS &&
                            !architectures.contains(segments[1])
                    )
                        architectures.add(segments[1])
                }
                getAbiFlags(architectures.toTypedArray())
            }
        } catch (error: IOException) {
            Logger.getLogger(AbiUtils::class.java.name)
                .log(
                    Level.WARNING,
                    "Cannot inspect native libraries in APK",
                    error,
                )
            0
        }
    }

    @JvmStatic
    fun getAbiFlags(architectures: Array<out String?>): Int {
        var flags = 0
        for (abi in architectures) {
            if (abi.isNullOrEmpty()) continue
            if (abi.startsWith("armeabi")) flags = flags or ARM
            flags =
                flags or
                    when (abi) {
                        "armeabi" -> ARM_V5
                        "armeabi-v7a" -> ARM_V7
                        "arm64-v8a" -> ARM_64
                        "x86" -> X86
                        "x86_64" -> X86_64
                        "mips" -> MIPS
                        "mips64" -> MIPS_64
                        else -> 0
                    }
        }
        return flags
    }

    @JvmStatic
    fun formatAbiFlags(flags: Int): String = buildString {
        appendLabel(flags, "ARM", ARM)
        append('/')
        appendLabel(flags, "64", ARM_64)
        append(' ')
        appendLabel(flags, "X86", X86)
        append('/')
        appendLabel(flags, "64", X86_64)
        append(' ')
        appendLabel(flags, "MIPS", MIPS)
    }

    private fun StringBuilder.appendLabel(flags: Int, label: String, mask: Int) {
        if (flags and mask == 0) append("<font color=\"grey\">$label</font>")
        else append("<b>$label</b>")
    }
}
