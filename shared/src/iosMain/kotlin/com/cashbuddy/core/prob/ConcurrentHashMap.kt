// NO-NETWORK
package com.cashbuddy.core.prob

import kotlin.concurrent.AtomicReference

actual class ConcurrentHashMap<K : Any, V : Any> : MutableMap<K, V> {
    private val ref = AtomicReference<Map<K, V>>(emptyMap())

    override val size: Int get() = ref.value.size
    override fun containsKey(key: K): Boolean = ref.value.containsKey(key)
    override fun containsValue(value: V): Boolean = ref.value.containsValue(value)
    override fun get(key: K): V? = ref.value[key]
    override fun isEmpty(): Boolean = ref.value.isEmpty()
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = ref.value.entries.toMutableSet()
    override val keys: MutableSet<K> get() = ref.value.keys.toMutableSet()
    override val values: MutableCollection<V> get() = ref.value.values.toMutableList()

    override fun clear() {
        ref.value = emptyMap()
    }

    override fun put(key: K, value: V): V? {
        while (true) {
            val old = ref.value
            val prev = old[key]
            val newMap = old + (key to value)
            if (ref.compareAndSet(old, newMap)) return prev
        }
    }

    override fun putAll(from: Map<out K, V>) {
        while (true) {
            val old = ref.value
            val newMap = old + from
            if (ref.compareAndSet(old, newMap)) return
        }
    }

    override fun remove(key: K): V? {
        while (true) {
            val old = ref.value
            val prev = old[key] ?: return null
            val newMap = old - key
            if (ref.compareAndSet(old, newMap)) return prev
        }
    }
}
