package io.nekohasekai.sfa.database

import java.util.Date
import java.util.Locale

data class SubscriptionUserInfo(
    val upload: Long,
    val download: Long,
    val total: Long,
    val expire: Long? = null,
) {
    val used: Long
        get() = if (upload > Long.MAX_VALUE - download) Long.MAX_VALUE else upload + download

    val remaining: Long
        get() = (total - used.coerceAtMost(total)).coerceAtLeast(0L)

    val remainingFraction: Float
        get() = (remaining.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)

    val expireAt: Date?
        get() = expire
            ?.takeIf { it > 0L && it <= Long.MAX_VALUE / 1000L }
            ?.let { Date(it * 1000L) }

    companion object {
        const val HEADER_NAME = "subscription-userinfo"
        private const val BINARY_UNIT = 1024.0
        private val BYTE_UNITS = arrayOf("B", "KB", "MB", "GB", "TB", "PB")

        fun formatBytes(bytes: Long): String {
            var value = bytes.coerceAtLeast(0L).toDouble()
            var unitIndex = 0
            while (value >= BINARY_UNIT && unitIndex < BYTE_UNITS.lastIndex) {
                value /= BINARY_UNIT
                unitIndex++
            }
            if (unitIndex == 0) return "${bytes.coerceAtLeast(0L)} B"
            val decimals = if (value >= 10.0) 1 else 2
            return String.format(Locale.getDefault(), "%.${decimals}f %s", value, BYTE_UNITS[unitIndex])
        }

        fun parse(headerValue: String?): SubscriptionUserInfo? {
            val values = buildMap {
                headerValue.orEmpty().split(';').forEach { entry ->
                    val separator = entry.indexOf('=')
                    if (separator <= 0) return@forEach
                    val key = entry.substring(0, separator).trim().lowercase(Locale.ROOT)
                    val value = entry.substring(separator + 1).trim().toLongOrNull()
                    if (value != null && value >= 0L) put(key, value)
                }
            }

            val total = values["total"]
            return if (total != null && total > 0L) {
                SubscriptionUserInfo(
                    upload = values["upload"] ?: 0L,
                    download = values["download"] ?: 0L,
                    total = total,
                    expire = values["expire"]?.takeIf { it > 0L },
                )
            } else {
                null
            }
        }
    }
}
