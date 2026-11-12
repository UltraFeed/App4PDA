package io.github.ultrafeed.app4pda.core.packages

object AbiUtils {
    private const val ARM = 512
    private const val ARM_V5 = 2048
    private const val ARM_V7 = 4096
    private const val ARM_64 = 65536
    private const val X86 = 8192
    private const val X86_64 = 32768
    private const val MIPS = 16384
    private const val MIPS_64 = 131072

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
