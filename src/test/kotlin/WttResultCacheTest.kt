package com.wtt.rideridhighlighter

import org.junit.Assert.*
import org.junit.Test
import com.intellij.openapi.progress.ProcessCanceledException

class WttResultCacheTest {
    @Test fun `repeated hits and misses resolve only once`() {
        val cache = WttResultCache<String, String>()
        var calls = 0
        repeat(1000) {
            assertEquals("Resolved", cache.getOrCompute("known") { calls++; "Resolved" })
            assertNull(cache.getOrCompute("unknown") { calls++; null })
        }
        assertEquals(2, calls)
    }

    @Test fun `eviction bounds memory and preserves recently used entries`() {
        val cache = WttResultCache<String, String>(2)
        val calls = mutableMapOf<String, Int>()
        fun lookup(key: String): String? = cache.getOrCompute(key) {
            calls[key] = (calls[key] ?: 0) + 1
            key.takeUnless { it == "missing" }
        }
        lookup("first")
        lookup("missing")
        lookup("first")
        lookup("third")
        lookup("first")
        assertEquals(1, calls["first"])
        lookup("missing")
        assertEquals(2, calls["missing"])
    }

    @Test fun `cancelled computation is retried rather than cached as missing`() {
        val cache = WttResultCache<String, String>()
        assertThrows(ProcessCanceledException::class.java) {
            cache.getOrCompute("item") { throw ProcessCanceledException() }
        }
        assertEquals("Ready", cache.getOrCompute("item") { "Ready" })
    }

    @Test fun `build refresh only fires once after each real build`() {
        var refreshes = 0
        val listener = BuildCompletionTransition { refreshes++ }
        listener.update(false)
        listener.update(false)
        assertEquals(0, refreshes)
        listener.update(true)
        listener.update(true)
        assertEquals(0, refreshes)
        listener.update(false)
        repeat(5) { listener.update(false) }
        assertEquals(1, refreshes)
        listener.update(true)
        listener.update(false)
        assertEquals(2, refreshes)
    }
}
