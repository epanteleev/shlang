package ir.codegen.x64.regalloc.liveness

import ir.*
import ir.utils.OrderedLocation

open class LiveRange internal constructor(protected val creation: OrderedLocation, protected var end: OrderedLocation) {
    fun begin(): OrderedLocation {
        return creation
    }

    fun end(): OrderedLocation {
        return end
    }

    fun merge(other: LiveRange): LiveRange {
        val begin = minOf(other.creation, creation, compareBy { it.index })
        val end = maxOf(other.end, end, compareBy { it.index })
        return LiveRange(begin, end)
    }

    override fun toString(): String {
        return "range [$creation : $end]"
    }
}

class LiveRangeImpl internal constructor(creation: OrderedLocation, end: OrderedLocation): LiveRange(creation, end) {
    internal fun registerUsage(location: OrderedLocation) {
        end = location
    }
}