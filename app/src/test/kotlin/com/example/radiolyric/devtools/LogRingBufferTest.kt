package com.example.radiolyric.devtools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogRingBufferTest {

    @Test
    fun ringBufferEvictsOldestPastCapacity() {
        val buf = LogRingBuffer(capacity = 3)
        repeat(5) { i -> buf.add(entry(i.toLong(), "msg-$i")) }

        val snapshot = buf.snapshot.value
        assertEquals(3, snapshot.size)
        assertEquals(listOf("msg-2", "msg-3", "msg-4"), snapshot.map { it.message })
        assertEquals(3, buf.size())
    }

    @Test
    fun clearEmptiesBufferAndUpdatesSnapshot() {
        val buf = LogRingBuffer(capacity = 3)
        buf.add(entry(1, "one"))
        buf.add(entry(2, "two"))
        assertEquals(2, buf.snapshot.value.size)

        buf.clear()
        assertTrue(buf.snapshot.value.isEmpty())
        assertEquals(0, buf.size())
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroCapacityIsRejected() {
        LogRingBuffer(capacity = 0)
    }

    private fun entry(ts: Long, msg: String) =
            LogEntry(timestampMs = ts, level = LogEntry.Level.I, tag = "TEST", message = msg)
}
