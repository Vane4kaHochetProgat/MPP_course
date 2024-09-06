package mpp.dynamicarray

import kotlinx.atomicfu.*

interface DynamicArray<E> {
    /**
     * Returns the element located in the cell [index],
     * or throws [IllegalArgumentException] if [index]
     * exceeds the [size] of this array.
     */
    fun get(index: Int): E

    /**
     * Puts the specified [element] into the cell [index],
     * or throws [IllegalArgumentException] if [index]
     * exceeds the [size] of this array.
     */
    fun put(index: Int, element: E)

    /**
     * Adds the specified [element] to this array
     * increasing its [size].
     */
    fun pushBack(element: E)

    /**
     * Returns the current size of this array,
     * it increases with [pushBack] invocations.
     */
    val size: Int
}


class DynamicArrayImpl<E> : DynamicArray<E> {
    private val main = atomic(Core<E>(INITIAL_CAPACITY, 0))

    @Suppress("UNCHECKED_CAST")
    override fun get(index: Int): E {
        return main.value[index] as E
    }

    override fun put(index: Int, element: E) {
        var curMain = main.value
        curMain.set(index, element)
        while (true) {
            val newMain = curMain.new.value ?: return
            newMain.set(index, curMain[index])
            curMain = newMain
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val curMain = main.value
            val mainSize = curMain.size
            if (mainSize < curMain.capacity) {
                if (curMain.push(mainSize, element)) {
                    curMain.size = mainSize
                    return
                }
                curMain.size = mainSize
                continue
            }
            var newMain: Core<E>? = Core(2 * curMain.capacity, curMain.capacity)
            if (!curMain.new.compareAndSet(null, newMain)) newMain = curMain.new.value ?: continue
            for (i in 0 until curMain.capacity) newMain!!.push(i, curMain[i])
            main.compareAndSet(curMain, newMain!!)
        }
    }

    override val size: Int
        get() {
            return main.value.size
        }
}

class Core<E>(private val _capacity: Int, length: Int) {
    private val array = atomicArrayOfNulls<E?>(_capacity)
    val new: AtomicRef<Core<E>?> = atomic(null)
    private val _size: AtomicInt = atomic(length)
    var size: Int
        get() {
            return _size.value
        }
        set(newSize) {
            _size.compareAndSet(newSize, newSize + 1)
        }

    val capacity: Int
        get() {
            return _capacity
        }

    operator fun get(index: Int): E? {
        require(-1 < index && index < _size.value)
        return array[index].value
    }

    fun set(index: Int, element: E?) {
        element ?: return
        require(-1 < index && index < _size.value)
        array[index].getAndSet(element)
    }

    fun push(index: Int, element: E?): Boolean {
        element ?: return false
        require(-1 < index && index < capacity)
        return array[index].compareAndSet(null, element)
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME