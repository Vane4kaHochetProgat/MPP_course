import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.loop

abstract class Descriptor {
    abstract fun complete(): Boolean
}


class AtomicArray<E>(size: Int, initialValue: E) {

    private val a = arrayOfNulls<Ref<E>>(size)

    init {
        for (i in 0 until size) a[i] = Ref(initialValue)
    }

    fun get(index: Int) =
        a[index]!!.value!!

    fun cas(index: Int, expected: E, update: E) =
        a[index]!!.compareAndSet(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {

        if (index1 == index2) {
            return if (expected1 == expected2) cas(index1, expected1, update2) else false
        }

        return if (index1 < index2) {
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

    // used for compareAndSet
    fun compareAndSet(expect: Any?, update: Any?): Boolean {
        v.loop { cur ->
            when (cur) {
                is Descriptor -> cur.complete()
                expect -> if (v.compareAndSet(expect, update)) return true
                else -> return false
            }
        }
    }

    // used for descriptors
    fun tryCas(expect: Any?, update: Any?): Boolean {
        return v.compareAndSet(expect, update)
    }

    fun getAndCas(expect: Any?, update: Any?): Any? {
        do {
            val cur = v.value
            if (cur !== expect) {
                return cur
            }
        } while (!tryCas(expect, update))
        return expect
    }
}

class RDCSSDescriptor(
    val ref: Ref<Any>,
    val expect: Any,
    private val update: Any,
    private val statusRef: Ref<Any>,
    private val expectedStatus: Any
) : Descriptor() {
    override fun complete(): Boolean {
        return if (statusRef.value === expectedStatus) {
            ref.tryCas(this, update)
            true
        } else {
            ref.tryCas(this, expect)
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
            status.tryCas(Status.UNDECIDED, newStatus)
        }

        val succeeded = status.value == Status.SUCCEEDED

        for (entry in entries) {
            entry.a.tryCas(this, if (succeeded) entry.update else entry.expect)
        }

        return succeeded
    }

}