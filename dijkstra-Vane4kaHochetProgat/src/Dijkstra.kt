package dijkstra

import java.util.*
import java.util.concurrent.Phaser
import java.util.concurrent.atomic.AtomicInteger
import kotlin.Comparator
import kotlin.concurrent.thread

private val NODE_DISTANCE_COMPARATOR = Comparator<Node> { o1, o2 -> o1!!.distance.compareTo(o2!!.distance) }

// Returns `Integer.MAX_VALUE` if a path has not been found.
fun shortestPathParallel(start: Node) {
    val workers = Runtime.getRuntime().availableProcessors()
    // The distance to the start node is `0`
    start.distance = 0
    // Create a priority (by distance) queue and add the start node into it
    val q = MultiQueue(workers, NODE_DISTANCE_COMPARATOR) // TODO replace me with a multi-queue based PQ!
    start.distance = 0
    q.insert(start)
    val activeNodes = AtomicInteger(1)
    // Run worker threads and wait until the total work is done
    val onFinish = Phaser(workers + 1) // `arrive()` should be invoked at the end by each worker
    repeat(workers) {
        thread {
            while (true) {
                if (activeNodes.compareAndSet(0, 0)) break
                val cur: Node = synchronized(q) { q.delete() } ?: continue
                for (e in cur.outgoingEdges) {
                    while (true) {
                        val distance = e.to.distance
                        val update = cur.distance + e.weight
                        if(distance <= update) break
                        if (distance > update && e.to.casDistance(distance, update)) {
                            q.insert(e.to)
                            activeNodes.incrementAndGet()
                            break
                        }
                    }
                }
                activeNodes.decrementAndGet()
            }
            onFinish.arrive()
        }
    }
    onFinish.arriveAndAwaitAdvance()
}

class MultiQueue<E>(workers: Int, comparator: Comparator<E>) {
    private val T = workers * 6 // 6 is for constant C
    private val _comparator = comparator
    private val queues = Array<PriorityQueue<E>>(T) { PriorityQueue(_comparator) }

    fun insert(task: E) {
        val q = queues[kotlin.random.Random.nextInt(0, T)]
        synchronized(q) {
            q.add(task)
        }

    }

    fun delete(): E? {
        var i1: Int
        var i2: Int
        while (true) {
            i1 = kotlin.random.Random.nextInt(0, T)
            i2 = kotlin.random.Random.nextInt(0, T)
            if (i1 != i2) {
                break
            }
        }
        val q1 = queues[i1]
        val q2 = queues[i2]
        synchronized(q1) {
            synchronized(q2) {
                if (q1.isEmpty()) {
                    return q2.poll()
                }
                if (q2.isEmpty()) {
                    return q1.poll()
                }
                return if (_comparator.compare(q1.peek(), q2.peek()) < 0) q1.poll() else q2.poll()
            }
        }
    }
}
