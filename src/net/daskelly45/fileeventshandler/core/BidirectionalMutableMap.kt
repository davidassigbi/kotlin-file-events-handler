package net.daskelly45.fileeventshandler.core

class BidirectionalMutableMap<K, V>: HashMap<K, V>() {
    private val inverseMap = HashMap<V, K>()

    fun getKey(value: V) = inverseMap[value]

    override fun put(key: K, value: V): V? {
        return super.put(key, value).also {
            inverseMap[value] = key
        }
    }

    operator fun set(value: V, key: K) {

    }

    fun getValue(key: K) = this[key]
}
