import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
class SynchronousQueue<E> {

    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(null, true)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    private suspend fun basicContinuation(node: Node<E>, curTail: Node<E>): Boolean {
        return suspendCoroutine sc@{ cont ->
            node.task = cont
            if (!curTail.next.compareAndSet(null, node)) {
                cont.resume(false)
                return@sc
            }
            tail.compareAndSet(curTail, node)
        }
    }

    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E) {
        val node = Node(element, true)
        while (true) {
            val curTail = tail.value
            var next = curTail.next.value
            if (next != null) {
                tail.compareAndSet(curTail, next)
                continue
            }
            val curHead = head.value
            if (curHead == curTail || curTail.isSend) {
                if (basicContinuation(node, curTail)) return
            } else {
                next = curHead.next.value
                if (next?.task != null && !next.isSend && head.compareAndSet(curHead, next)) {
                    next.element.compareAndSet(null, element)
                    next.task!!.resume(true)
                    return
                }
            }
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */
    suspend fun receive(): E {
        val node: Node<E> = Node(null, false)
        while (true) {
            val curTail = tail.value
            var next = curTail.next.value
            if (next != null) {
                tail.compareAndSet(curTail, next)
                continue
            }
            val curHead = head.value
            if (curHead == curTail || !curTail.isSend) {
                if (basicContinuation(node, curTail)) return node.element.value!!
            } else {
                next = curHead.next.value
                if (next?.task != null && next.isSend && head.compareAndSet(curHead, next)) {
                    val element = next.element.value ?: continue
                    next.element.compareAndSet(element, null)
                    next.task!!.resume(true)
                    return element
                }
            }
        }
    }
}

class Node<E>(x: E?, val isSend: Boolean) {
    val element = atomic(x)
    var task: Continuation<Boolean>? = null
    val next = atomic<Node<E>?>(null)
}