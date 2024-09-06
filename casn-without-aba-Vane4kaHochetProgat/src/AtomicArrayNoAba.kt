import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.loop

abstract class Descriptor {
    abstract fun complete(): Boolean
}

/**
 * implemented with help of
 * https://paulcavallaro.com/blog/a-practical-multi-word-compare-and-swap-operation/
 * https://www.cl.cam.ac.uk/research/srg/netos/papers/2002-casn.pdf
 */
class AtomicArrayNoAba<E>(size: Int, initialValue: E) {

    private val a = arrayOfNulls<Ref<E>>(size)

    init {
        for (i in 0 until size) a[i] = Ref(initialValue)
    }

    fun get(index: Int) =
        a[index]!!.value!!

    fun cas(index: Int, expected: E, update: E) =
        a[index]!!.cas(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {

        if (index1 == index2 && expected2 != expected1) return false

        // https://t.me/c/1727157385/1014
        if (index1 == index2) {
            @Suppress("UNCHECKED_CAST")
            return cas(index1, expected1, (update1 as Int + update2 as Int - expected1 as Int) as E)
        }

        return if (index1 <= index2) {
            CASNDescriptor(
                Entry(a[index1]!!, expected1, update1),
                Entry(a[index2]!!, expected2, update2)
            ).complete()
        } else {
            CASNDescriptor(
                Entry(a[index2]!!, expected2, update2),
                Entry(a[index1]!!, expected1, update1)
            ).complete()
        }
    }

}


class Ref<T>(
    initialValue: T
) {
    private val v = atomic<Any?>(initialValue)

    val value: T
        get() {
            @Suppress("UNCHECKED_CAST")
            v.loop { cur ->
                when (cur) {
                    is Descriptor -> cur.complete()
                    else -> return cur as T
                }
            }
        }


    fun cas(expect: Any?, update: Any?): Boolean {
        return v.compareAndSet(expect, update)
    }

    fun getAndCas(expect: Any?, update: Any?): Any? {
        do {
            val cur = v.value
            if (cur !== expect) {
                return cur
            }
        } while (!cas(expect, update))
        return expect
    }
}

class RDCSSDescriptor(
    val ref: Ref<Any>, val expect: Any, private val update: Any, private val statusRef: Ref<Any>, private val expectedStatus: Any
) : Descriptor() {
    override fun complete(): Boolean {
        return if (statusRef.value === expectedStatus) {
            ref.cas(this, update)
            true
        } else {
            ref.cas(this, expect)
            false
        }
    }
}

fun dcss(cd: RDCSSDescriptor): Any? {
    var r: Any?
    do {
        r = cd.ref.getAndCas(cd.expect, cd)
        if (r is RDCSSDescriptor) {
            r.complete()
        }
    } while (r is RDCSSDescriptor)
    if (r === cd.expect) {
        cd.complete()
    }
    return r
}


class Entry<E>(val a: Ref<E>, val expect: E, val update: E)

enum class Status {
    UNDECIDED,
    SUCCEEDED,
    FAILED
}

class CASNDescriptor<E>(entry1: Entry<E>, entry2: Entry<E>) : Descriptor() {

    private val status = Ref(Status.UNDECIDED)
    private val entries = arrayOf(entry1, entry2)

    override fun complete(): Boolean {
        if (status.value === Status.UNDECIDED) {
            var newStatus = Status.SUCCEEDED

            var i = 0
            while (i < entries.size) {
                val entry = entries[i]

                @Suppress("UNCHECKED_CAST")
                val value = dcss(
                    RDCSSDescriptor(
                        entry.a as Ref<Any>,
                        entry.expect!!, this, status as Ref<Any>, Status.UNDECIDED
                    )
                )
                if (value is CASNDescriptor<*>) {
                    if (value != this) {
                        value.complete()
                        continue
                    }
                } else {
                    if (value != entry.expect) {
                        newStatus = Status.FAILED
                        break
                    }
                }
                i++
            }
            status.cas(Status.UNDECIDED, newStatus)
        }

        val succeeded = status.value == Status.SUCCEEDED

        for (entry in entries) {
            entry.a.cas(this, if (succeeded) entry.update else entry.expect)
        }

        return succeeded
    }

}