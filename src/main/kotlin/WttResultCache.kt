package com.wtt.rideridhighlighter

/** Bounded LRU that retains misses, without holding its lock during index queries. */
internal class WttResultCache<K, V>(private val capacity: Int = 8192) {
    private data class Result<V>(val value: V?)
    private val entries = object : LinkedHashMap<K, Result<V>>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Result<V>>): Boolean = size > capacity
    }

    init {
        require(this.capacity > 0)
    }

    fun getOrCompute(key: K, compute: () -> V?): V? {
        synchronized(entries) {
            entries[key]?.let {
                return it.value
            }
        }
        // Cancellation and failures propagate without caching an incomplete result.
        val result = Result(compute())
        return synchronized(entries) {
            val existing = entries[key]
            if (existing != null) {
                existing.value
            } else {
                entries[key] = result
                result.value
            }
        }
    }
}
