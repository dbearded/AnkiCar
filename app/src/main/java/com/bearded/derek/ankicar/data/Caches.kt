package com.bearded.derek.ankicar.data

import kotlinx.coroutines.*

interface ConvertingCache<T, E> {
    val onOverflow: (E) -> Unit
    val onStarved: suspend () -> List<T>

    fun accept(t: T, convert: (T) -> E)
    fun rollback(revert: (E) -> T)
    fun get(): T?
    fun flush()
    fun clear()
}

class DefaultConvertingCache<T, E>(
    private val limit: Int = 3,
    override val onOverflow: (E) -> Unit,
    override val onStarved: suspend () -> List<T>
) : ConvertingCache<T, E> {

    private val raw = mutableListOf<T>()
    private val converted = mutableListOf<E>()

    init { GlobalScope.launch(Dispatchers.Default) { raw.addAll(onStarved()) } }

    override fun get(): T? = raw.firstOrNull()

    override fun accept(t: T, convert: (T) -> E) {
        converted.add(0, convert(raw.removeAt(0)))
        if (converted.size > limit) onOverflow(converted.removeAt(converted.size - 1))
    }

    override fun rollback(revert: (E) -> T) {
        if (converted.isEmpty()) return
        raw.add(0, revert(converted.removeAt(0)))
    }

    override fun flush() {
        while (converted.isNotEmpty()) {
            onOverflow(converted.removeAt(converted.size - 1))
        }
    }

    override fun clear() {
        converted.clear()
        raw.clear()
    }
}

class Cache<E>(private val limit: Int = 3, private val onOverflow: (E) -> Unit) {
    private val list = mutableListOf<E>()
    fun add(element: E) {
        list.add(0, element)
        if (list.size > limit) onOverflow(list.removeAt(limit))
    }

    fun previous(): E? = if (list.isEmpty()) null else list.removeAt(0)

    fun flush() {
        while (list.isNotEmpty()) {
            onOverflow(list.removeAt(list.size - 1))
        }
    }

    fun clear() { list.clear() }

    fun asList(): List<E> = list

    val size: Int
        get() = list.size
}