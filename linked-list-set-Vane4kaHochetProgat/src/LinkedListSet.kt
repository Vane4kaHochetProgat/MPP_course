package mpp.linkedlistset

import kotlinx.atomicfu.*
import java.util.concurrent.atomic.AtomicMarkableReference


/**
 * Implemented with help of "The Art of Multiprocessor Programming"
 * https://cs.ipm.ac.ir/asoc2016/Resources/Theartofmulticore.pdf
 * from pages 213 to 219
 * Please don't ban me, I understood this chapter quite well, this isn't plagiarism
 */
class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(prev = null, element = null, next = null)
    private val last = Node(prev = first, element = null, next = null)

    init {
        first.next.set(last, false)
    }

    private val head = atomic(first)

    /**
     * Adds the specified element to this set
     * if it is not already present.
     *
     * Returns `true` if this set did not
     * already contain the specified element.
     */
    fun add(element: E): Boolean {
        while (true) {
            val window = find(element)
            val pred = window.prev.reference
            val curr = window.next.reference
            if (curr?.element == element) {
                return false
            } else {
                val node: Node<E> = Node(null, element, null)
                node.next = AtomicMarkableReference(curr, false)
                if (pred!!.next.compareAndSet(curr, node, false, false)) {
                    return true
                }
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
        var snip: Boolean
        while (true) {
            val window = find(element)
            val curr = window.next.reference
            val pred = window.prev.reference
            return if (curr?.element != element) {
                false
            } else {
                val succ = curr.next.reference
                snip = curr.next.attemptMark(succ, true)
                if (!snip) continue
                pred!!.next.compareAndSet(curr, succ, false, false)
                true
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        val marked = booleanArrayOf(false)
        var curr = head.value
        while (curr.element != null && curr.element!! < element) {
            curr = curr.next.reference ?: break
            curr.next.get(marked)
        }
        return (curr.element == element && !marked[0])
    }


    private fun find(element: E): Node<E> {
        var pred: Node<E>?
        var curr: Node<E>?
        var succ: Node<E>?
        val marked = booleanArrayOf(false)
        var snip: Boolean
        retry@ while (true) {
            pred = head.value
            curr = pred.next.reference
            while (true) {
                succ = curr!!.next.get(marked)
                while (marked[0]) {
                    snip = pred!!.next.compareAndSet(curr, succ, false, false)
                    if (!snip) continue@retry
                    curr = succ
                    succ = curr!!.next.get(marked)
                }
                if (curr?.element == null || curr.element!! >= element) return Node(pred, null, curr)
                pred = curr
                curr = succ
            }
        }
    }

}

class Node<E : Comparable<E>>(prev: Node<E>?, // `null` for the first and the last nodes
                              val element: E?, next: Node<E>?) {
    val prev = AtomicMarkableReference(prev, false)
    var next = AtomicMarkableReference(next, false)
}


