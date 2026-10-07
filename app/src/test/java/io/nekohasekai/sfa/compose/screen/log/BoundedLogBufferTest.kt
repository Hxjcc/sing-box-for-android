package io.nekohasekai.sfa.compose.screen.log

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.LinkedList

class BoundedLogBufferTest {
    @Test
    fun pausedLoggingKeepsOnlyNewestEntries() {
        val pending = LinkedList<Int>()
        repeat(100) { pending.appendBounded(listOf(it), 3) }
        assertEquals(listOf(97, 98, 99), pending)
    }

    @Test
    fun oversizedBatchDoesNotOverflowOrRemoveFromEmptyBuffer() {
        val buffer = LinkedList(listOf(-1))
        buffer.appendBounded((0..99).toList(), 3)
        assertEquals(listOf(97, 98, 99), buffer)
    }

    @Test
    fun resumingAfterLongPauseMergesWithinLimit() {
        val displayed = LinkedList(listOf(1, 2))
        val pending = LinkedList<Int>()
        pending.appendBounded((3..100).toList(), 3)
        displayed.appendBounded(pending, 3)
        assertEquals(listOf(98, 99, 100), displayed)
    }

    @Test
    fun emptyBatchPreservesExistingLogs() {
        val buffer = LinkedList(listOf(1, 2))
        buffer.appendBounded(emptyList(), 3)
        assertEquals(listOf(1, 2), buffer)
    }
}
