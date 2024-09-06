package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Pair<E?, Int>>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        for (i in 0..ELIMINATION_ARRAY_SIZE) {
            val index = kotlin.random.Random.nextInt(0, ELIMINATION_ARRAY_SIZE)

            if (eliminationArray[index].compareAndSet(Pair(null, 0), Pair(x, 2))) {
                for (j in 0..ELIMINATION_ARRAY_SIZE * 5) {
                    if (eliminationArray[index].compareAndSet(Pair(null, 1), Pair(null, 0))) return
                }
                //       return
            }
        }
        while (true) {
            val cur_top = top.value
            val new_top = Node(x, cur_top)
            if (top.compareAndSet(cur_top, new_top)) return
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        for (i in 0..ELIMINATION_ARRAY_SIZE) {
            val index = kotlin.random.Random.nextInt(0, ELIMINATION_ARRAY_SIZE)

            val elim_top = eliminationArray[index].value
            if (elim_top == null) continue
            if (elim_top.second == 2 && eliminationArray[index].compareAndSet(elim_top, Pair(null, 1))) {

                return elim_top.first
            }
        }
        while (true) {
            val cur_top = top.value
            if (cur_top === null) return null
            val new_top = cur_top.next
            if (top.compareAndSet(cur_top, new_top)) return cur_top.x

        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT