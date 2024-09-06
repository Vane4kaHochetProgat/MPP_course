import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*

//sealed class Operation<out E> {
//    class Push<E>(val element: E) : Operation<E>()
//    class Don
//    object Poll : Operation<Nothing>()
//    object Peek: Operation<Nothing>()
//    //  class Null<E>() : Wrapper<E>()
//}

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val lock = atomic(false)

    //Int for operation: 1 - add, -1 = poll, 0 - peek, 2 - executed, need to be finished
    private val fcArray = atomicArrayOfNulls<Pair<E?, Int>?>(FC_ARRAY_SIZE)


    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        val index = kotlin.random.Random.nextInt(0, FC_ARRAY_SIZE)
        var set = fcArray[index].compareAndSet(null, Pair(null, -1))
        while (!lock.compareAndSet(expect = false, update = true)) {
            if (set) {
                if (fcArray[index].value!!.second == 2) {
                    val elem = fcArray[index].value!!.first
                    fcArray[index].getAndSet(null)
                    return elem
                }
            } else {
                set = fcArray[index].compareAndSet(null, Pair(null, -1))
            }
        }
        if (set) {
            if (fcArray[index].value!!.second == 2) {
                val elem = fcArray[index].value!!.first
                fcArray[index].getAndSet(null)
                lock.getAndSet(false)
                return elem
            }
            fcArray[index].getAndSet(null)
        }
        val elem = q.poll()
        fcHelper()
        lock.getAndSet(false)
        return elem

    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        var index = kotlin.random.Random.nextInt(0, FC_ARRAY_SIZE)
        var set = fcArray[index].compareAndSet(null, Pair(null, 0))
        while (!lock.compareAndSet(expect = false, update = true)) {
            if (set) {
                if (fcArray[index].value!!.second == 2) {
                    val elem = fcArray[index].value!!.first
                    fcArray[index].getAndSet(null)
                    return elem
                }
            } else {
                set = fcArray[index].compareAndSet(null, Pair(null, 0))
            }
        }
        if (set) {
            if (fcArray[index].value!!.second == 2) {
                val elem = fcArray[index].value!!.first
                fcArray[index].getAndSet(null)
                lock.getAndSet(false)
                return elem
            }
            fcArray[index].getAndSet(null)
        }
        val elem = q.peek()
        fcHelper()
        lock.getAndSet(false)

        return elem


    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        var index = kotlin.random.Random.nextInt(0, FC_ARRAY_SIZE)
        var set = fcArray[index].compareAndSet(null, Pair(element, 1))
        while (!lock.compareAndSet(expect = false, update = true)) {
            if (set) {
                if (fcArray[index].value!!.second == 2) {
                    fcArray[index].getAndSet(null)
                    return
                }
            } else {
                set = fcArray[index].compareAndSet(null, Pair(element, 1))
            }
        }
        if (set) {
            if (fcArray[index].value!!.second == 2) {
                fcArray[index].getAndSet(null)
                lock.getAndSet(false)
                return
            }
            fcArray[index].getAndSet(null)
        }
        q.add(element)
        fcHelper()
        lock.getAndSet(false)

    }

    fun fcHelper() {
        for (i in 0 until FC_ARRAY_SIZE) {
            val value = fcArray[i].value ?: continue
            if (value.second == 0) {
                fcArray[i].compareAndSet(value, Pair(q.peek(), 2))
            }
            if (value.second == 1) {
                q.add(value.first)
                fcArray[i].compareAndSet(value, Pair(null, 2))
            }
            if (value.second == -1) {
                fcArray[i].compareAndSet(value, Pair(q.poll(), 2))
            }
        }
    }
}

const val FC_ARRAY_SIZE = 12