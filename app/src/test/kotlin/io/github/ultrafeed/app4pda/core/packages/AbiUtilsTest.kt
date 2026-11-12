package io.github.ultrafeed.app4pda.core.packages

import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class AbiUtilsTest {
    @Test
    fun mixedArchitecturesKeepLegacyBitmask() {
        assertThat(
            AbiUtils.getAbiFlags(
                arrayOf(
                    "armeabi-v7a",
                    "arm64-v8a",
                    "x86",
                    "x86_64",
                    "mips",
                    "mips64",
                    null,
                    "unknown",
                ),
            ),
            `is`(
                512 or 4096 or 65536 or 8192 or 32768 or 16384 or 131072,
            ),
        )
        assertThat(
            AbiUtils.getAbiFlags(arrayOf("armeabi")),
            `is`(
                512 or 2048,
            ),
        )
    }

    @Test
    fun htmlFormattingRetainsHighlightedAndGreyLabels() {
        assertThat(
            AbiUtils.formatAbiFlags(512 or 65536),
            `is`(
                "<b>ARM</b>/<b>64</b> <font color=\"grey\">X86</font>/" +
                    "<font color=\"grey\">64</font> <font color=\"grey\">MIPS</font>",
            ),
        )
    }
}
