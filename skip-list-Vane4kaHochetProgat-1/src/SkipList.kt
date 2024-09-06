package mpp.skiplist

//import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicMarkableReference
//import kotlin.math.pow

sealed class ComparableWithBorders<out E> {
    object Min : ComparableWithBorders<Nothing>()
    class Value<E : Comparable<E>>(val t: E?) : ComparableWithBorders<E>()
    object Max : ComparableWithBorders<Nothing>()
}


class SkipList<E : Comparable<E>> {

    private val head: Node<ComparableWithBorders<E>> = Node(ComparableWithBorders.Min)
    private val tail: Node<ComparableWithBorders<E>> = Node(ComparableWithBorders.Max)

    init {
        for (i in head.next.indices) head.next[i] = AtomicMarkableReference(tail, false)
    }

    class Node<T>(val value: T?, val topLevel: Int = MAX_LEVEL) {
        val next = arrayOfNulls<AtomicMarkableReference<Node<T>>>((topLevel + 1))

        init {
            for (i in next.indices) next[i] = AtomicMarkableReference(null, false)
        }
    }

//    private fun randomLevel(): Int {
//        var prob =  ThreadLocalRandom.current().nextInt(0, 105)
//        if (prob < 75) {
//            return 0
//        }
//        prob -= 75
//        for (i in 1 until MAX_LEVEL) {
//            if (prob > i) {
//                return i
//            }
//            prob -= i
//        }
//        return MAX_LEVEL
//    }

    private fun find(
        x: E,
        preds: Array<Node<ComparableWithBorders<E>>?>,
        succs: Array<Node<ComparableWithBorders<E>>?>
    ): Boolean {
        val bottomLevel = 0
        val marked = booleanArrayOf(false)
        var snip = false
        var pred: Node<ComparableWithBorders<E>>? = null
        var curr: Node<ComparableWithBorders<E>>? = null
        var succ: Node<ComparableWithBorders<E>>? = null
        retry@ while (true) {
            pred = head
            for (level in MAX_LEVEL downTo bottomLevel) {
                curr = pred!!.next[level]!!.reference
                while (true) {
                    succ = curr!!.next[level]!!.get(marked)
                    while (marked[0]) {
                        snip = pred!!.next[level]!!.compareAndSet(
                            curr, succ,
                            false, false
                        )
                        if (!snip) continue@retry
                        curr = pred.next[level]!!.reference
                        succ = curr.next[level]!!.get(marked)
                    }

                    if (curr != null && curr.value !is ComparableWithBorders.Max) {
                        if (curr.value is ComparableWithBorders.Min || (((curr.value as ComparableWithBorders.Value).t != null) && (x > (curr.value as ComparableWithBorders.Value).t!!))) {
                            pred = curr
                            curr = succ
                        }else{
                            break
                        }
                    } else {
                        break
                    }
                }
                preds[level] = pred
                succs[level] = curr
            }
            return curr != null && curr.value !is ComparableWithBorders.Max && curr.value !is ComparableWithBorders.Min && ((curr.value as ComparableWithBorders.Value).t != null) && (x == (curr.value as ComparableWithBorders.Value).t!!)
        }
    }

    /**
     * Adds the specified element to this set
     * if it is not already present.
     *
     * Returns `true` if this set did not
     * already contain the specified element.
     */
    fun add(element: E): Boolean {
        val topLevel = 0
        val bottomLevel = 0
        val preds = arrayOfNulls<Node<ComparableWithBorders<E>>>((MAX_LEVEL + 1))
        val succs = arrayOfNulls<Node<ComparableWithBorders<E>>>((MAX_LEVEL + 1))
        while (true) {
            val found = find(element, preds, succs)
            if (found) {
                return false
            } else {
                val newNode: Node<ComparableWithBorders<E>> = Node(ComparableWithBorders.Value(element), topLevel)
                for (level in bottomLevel..topLevel) {
                    val succ: Node<ComparableWithBorders<E>>? = succs[level]
                    newNode.next[level]?.set(succ, false)
                }
                var pred: Node<ComparableWithBorders<E>>? = preds[bottomLevel]
                var succ: Node<ComparableWithBorders<E>>? = succs[bottomLevel]
                newNode.next[bottomLevel]?.set(succ, false)
                if (!pred!!.next[bottomLevel]!!.compareAndSet(
                        succ, newNode,
                        false, false
                    )
                ) {
                    continue
                }
                for (level in (bottomLevel + 1)..topLevel) {
                    while (true) {
                        pred = preds[level]
                        succ = succs[level]
                        if (pred!!.next[level]!!.compareAndSet(succ, newNode, false, false)) break
                        find(element, preds, succs)
                    }
                }
                return true
            }
        }
    }

    /**
     * Removes the specified element from this set
     * if it is present.
     *
     * Returns `true` if this set contained
     * the specified element.
     */
    fun remove(element: E): Boolean {
        val bottomLevel = 0
        val preds = arrayOfNulls<Node<ComparableWithBorders<E>>>((MAX_LEVEL + 1))
        val succs = arrayOfNulls<Node<ComparableWithBorders<E>>>((MAX_LEVEL + 1))
        var succ: Node<ComparableWithBorders<E>>?
        while (true) {
            val found = find(element, preds, succs)
            if (!found) {
                return false
            } else {
                val nodeToRemove: Node<ComparableWithBorders<E>>? = succs[bottomLevel]
                for (level in (nodeToRemove?.topLevel!!) downTo (bottomLevel + 1)) {
                    val marked = booleanArrayOf(false)
                    succ = nodeToRemove.next[level]?.get(marked)
                    while (!marked[0]) {
                        nodeToRemove.next[level]!!.attemptMark(succ, true)
                        succ = nodeToRemove.next[level]!!.get(marked)
                    }
                }
                val marked = booleanArrayOf(false)
                succ = nodeToRemove.next[bottomLevel]?.get(marked)
                while (true) {
                    val iMarkedIt = nodeToRemove.next[bottomLevel]!!.compareAndSet(
                        succ, succ,
                        false, true
                    )
                    succ = succs[bottomLevel]!!.next[bottomLevel]!!.get(marked)
                    if (iMarkedIt) {
                        find(element, preds, succs)
                        return true
                    } else if (marked[0]) return false
                }
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        val bottomLevel = 0
        val marked = booleanArrayOf(false)
        var pred: Node<ComparableWithBorders<E>>? = head
        var curr: Node<ComparableWithBorders<E>>? = null
        var succ: Node<ComparableWithBorders<E>>? = null
        for (level in MAX_LEVEL downTo bottomLevel) {
            curr = pred!!.next[level]!!.reference
            while (true) {
                succ = curr!!.next[level]!!.get(marked)
                while (marked[0]) {
                    curr = pred!!.next[level]!!.reference
                    succ = curr.next[level]!!.get(marked)
                }
                if (curr != null && curr.value !is ComparableWithBorders.Max) {
                    if (curr.value is ComparableWithBorders.Min || (((curr.value as ComparableWithBorders.Value).t != null) && (element > (curr.value as ComparableWithBorders.Value).t!!))) {
                        pred = curr
                        curr = succ
                    }else{
                        break
                    }
                } else {
                    break
                }
            }
        }
        return curr != null && curr.value !is ComparableWithBorders.Max && curr.value !is ComparableWithBorders.Min && ((curr.value as ComparableWithBorders.Value).t != null) && (element == (curr.value as ComparableWithBorders.Value).t!!)
    }
}

private const val MAX_LEVEL = 31