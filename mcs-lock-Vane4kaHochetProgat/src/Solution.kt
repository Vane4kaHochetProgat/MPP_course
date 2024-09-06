import java.util.concurrent.atomic.AtomicReference

class Solution(private val env: Environment) : Lock<Solution.Node> {
    private val tail = AtomicReference<Node?>()

    override fun lock(): Node {
        val my = Node() // сделали узел
        my.locked.set(true)
        val pred = tail.getAndSet(my)
        if (pred != null) {
            pred.next.getAndSet(my)
            while (my.locked.value) {
                env.park()
            }
        }
        return my // вернули узел
    }

    override fun unlock(node: Node) {
        if (node.next.value == null) {
            if (tail.compareAndSet(node, null)) {
                return
            } else {
                while (node.next.value == null){
                    //do nothing
                }
            }
        }
        val next = node.next.value ?:return
        next.locked.set(false)
        env.unpark(next.thread)
    }

    class Node {
        val thread: Thread = Thread.currentThread() // запоминаем поток, которые создал узел
        val locked = AtomicReference(false)
        val next = AtomicReference<Node?>(null)
    }
}