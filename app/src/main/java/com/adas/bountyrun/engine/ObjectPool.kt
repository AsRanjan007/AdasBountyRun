package com.adas.bountyrun.engine

/**
 * A minimal free-list object pool (spec §28: object pooling for road users).
 * Avoids per-frame allocation of frequently spawned entities.
 */
class ObjectPool<T>(
    initialSize: Int,
    private val factory: () -> T,
    private val isActive: (T) -> Boolean
) {
    private val items = ArrayList<T>(initialSize)

    init {
        repeat(initialSize) { items.add(factory()) }
    }

    /** All backing items (active and inactive); iterate and skip inactive ones. */
    val all: List<T> get() = items

    /** Fetch an inactive item, growing the pool if necessary. */
    fun obtain(): T {
        for (i in items.indices) {
            val it = items[i]
            if (!isActive(it)) return it
        }
        val created = factory()
        items.add(created)
        return created
    }

    fun activeCount(): Int = items.count(isActive)
}
