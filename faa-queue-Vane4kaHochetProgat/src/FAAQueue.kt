package mpp.faaqueue

import kotlinx.atomicfu.*

sealed class Wrapper<out E> {
    class Elem<E>(val element: E) : Wrapper<E>()
    object Broken : Wrapper<Nothing>()
    //  class Null<E>() : Wrapper<E>()
}

class FAAQueue<E> {
    private val head: AtomicRef<Segment<E>> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment<E>> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)
    // private val Idx = atomic(1L)

    init {
        val firstNode = Segment<E>(0L)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Adds the specified element [element] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val cur_tail = tail.value
            val index = enqIdx.getAndIncrement()
            var new_tail = cur_tail
            while (index / SEGMENT_SIZE > new_tail.segmentIndex) {
                new_tail.next.compareAndSet(null, Segment(new_tail.segmentIndex + 1))
                new_tail = new_tail.next.value ?: break
            }

            tail.compareAndSet(cur_tail, new_tail)
            if (new_tail.elements[(index % SEGMENT_SIZE).toInt()].compareAndSet(null, Wrapper.Elem(element))) return
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if (isEmpty) return null
            val cur_head = head.value
            val index = deqIdx.getAndIncrement()
            var new_head = cur_head
            while (index / SEGMENT_SIZE > new_head.segmentIndex) {
                new_head.next.compareAndSet(null, Segment<E>(new_head.segmentIndex + 1))
                new_head = new_head.next.value ?: break
            }

            head.compareAndSet(cur_head, new_head)
            if (new_head.elements[(index % SEGMENT_SIZE).toInt()].compareAndSet(null, Wrapper.Broken)) {
                continue
            } else {
                return (new_head.elements[(index % SEGMENT_SIZE).toInt()].value as Wrapper.Elem).element
            }
        }
    }


    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return deqIdx.value >= enqIdx.value
        }
}

private class Segment<E>(_segmentIndex: Long) {
    val next = atomic<Segment<E>?>(null)
    val elements = atomicArrayOfNulls<Wrapper<E>>(SEGMENT_SIZE)
    val segmentIndex = _segmentIndex
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

