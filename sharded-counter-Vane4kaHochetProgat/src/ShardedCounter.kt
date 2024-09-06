package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import kotlin.concurrent.thread

/**
 * linearizability seems obvious: for all elements in array program with inc() and get would be linearizable,
 * because all the operations on each element are atomic
 * If we add inc(delta) func,
 * which would work like delta times inc(),
 * that obviously wouldn't be linearizable - if one thread get() in process of other thread incrementing number >1,
 * that would live first thread with get() == something in between of get() before inc(delta) and after inc(delta)
 */
class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        val index = kotlin.random.Random.nextInt(0, ARRAY_SIZE)
        counters[index].incrementAndGet()
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        var sum = 0
        for (i in 0..1) {
            sum += counters[i].value
        }
        return sum
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME