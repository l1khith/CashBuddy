// NO-NETWORK
package com.cashbuddy.core.prob

import kotlin.concurrent.AtomicReference

actual class ConcurrentHashMap<K : Any, V : Any> actual constructor() : MutableMap<K, V> {
    private val ref = AtomicReference<Map<K, V>>(emptyMap())

    actual override val size: Int get() = ref.value.size
    actual override fun containsKey(key: K): Boolean = ref.value.containsKey(key)
    actual override fun containsValue(value: V): Boolean = ref.value.containsValue(value)
    actual override fun get(key: K): V? = ref.value[key]
    actual override fun isEmpty(): Boolean = ref.value.isEmpty()
    private class ConcurrentEntry<K, V>(
        override val key: K,
        override var value: V
    ) : MutableMap.MutableEntry<K, V> {
        override fun setValue(newValue: V): V {
            val old = value
            value = newValue
            return old
        }
    }

    actual override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = ref.value.entries.mapTo(mutableSetOf()) { (k, v) -> ConcurrentEntry(k, v) }
    actual override val keys: MutableSet<K> get() = ref.value.keys.toMutableSet()
    actual override val values: MutableCollection<V> get() = ref.value.values.toMutableList()

    actual override fun clear() {
        ref.value = emptyMap()
    }

    actual override fun put(key: K, value: V): V? {
        while (true) {
            val old = ref.value
            val prev = old[key]
            val newMap = old + (key to value)
            if (ref.compareAndSet(old, newMap)) return prev
        }
    }

    actual override fun putAll(from: Map<out K, V>) {
        while (true) {
            val old = ref.value
            val newMap = old + from
            if (ref.compareAndSet(old, newMap)) return
        }
    }

    actual override fun remove(key: K): V? {
        while (true) {
            val old = ref.value
            val prev = old[key] ?: return null
            val newMap = old - key
            if (ref.compareAndSet(old, newMap)) return prev
        }
    }
}
