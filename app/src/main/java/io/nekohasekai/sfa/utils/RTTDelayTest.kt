package io.nekohasekai.sfa.utils

import io.nekohasekai.sfa.database.Settings

object RTTDelayTest {
    private const val TEST_PREFIX = "__sfa_rtt__:"
    private const val MODE_PREFIX = "__sfa_rtt_mode__:"

    fun outboundTag(outboundTag: String): String = if (Settings.rttDelayTest && !CommandTarget.isRemote) {
        TEST_PREFIX + outboundTag
    } else {
        outboundTag
    }

    fun syncMode(enabled: Boolean = Settings.rttDelayTest) {
        if (CommandTarget.isRemote) return
        CommandTarget.standaloneClient().urlTest(MODE_PREFIX + if (enabled) "1" else "0")
    }
}
