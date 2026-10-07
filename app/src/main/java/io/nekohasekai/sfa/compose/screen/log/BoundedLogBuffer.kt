package io.nekohasekai.sfa.compose.screen.log

import java.util.LinkedList

internal fun <T> LinkedList<T>.appendBounded(entries: List<T>, capacity: Int) {
    require(capacity > 0)
    addAll(entries.takeLast(capacity))
    while (size > capacity) {
        removeFirst()
    }
}
