package ir.pass.analysis.intervals

import common.assertion
import ir.module.MutationMarker
import ir.value.LocalValue
import ir.pass.common.AnalysisResult
import ir.platform.x64.regalloc.Group


data class LiveIntervalsException(override val message: String): Exception(message)

class MergedLiveIntervals(private val liveness: Map<Group, LiveRange>) {
    private val valueToGroup = run {
        val map = hashMapOf<LocalValue, Group>()
        for (group in liveness.keys) {
            for (value in group) {
                map[value] = group
            }
        }
        map
    }

    override fun toString(): String {
        val builder = StringBuilder()
        for ((group, range) in liveness) {
            builder.append("$group - $range\n")
        }

        return builder.toString()
    }

    operator fun get(v: LocalValue): LiveRange? {
        return liveness[valueToGroup[v]]
    }

    operator fun get(v: Group): LiveRange? {
        return liveness[v]
    }

    operator fun iterator(): Iterator<Map.Entry<Group, LiveRange>> {
        return liveness.iterator()
    }

    fun getGroup(value: LocalValue): Group? {
        return valueToGroup[value]
    }
}

class LiveIntervals(private val liveIntervals: MutableMap<LocalValue, LiveRangeImpl>, marker: MutationMarker): AnalysisResult(marker) {
    override fun toString(): String {
        val builder = StringBuilder()
        for ((v, ranges) in liveIntervals) {
            builder.append("$v -> $ranges\n")
        }

        return builder.toString()
    }

    operator fun get(v: LocalValue): LiveRange {
        val range = liveIntervals[v]
        assertion(range != null) {
            "cannot find v=$v"
        }

        return range as LiveRange
    }

    operator fun set(v: LocalValue, other: LiveRange) {
        liveIntervals[v] = other as LiveRangeImpl
    }

    operator fun iterator(): Iterator<Map.Entry<LocalValue, LiveRange>> {
        return liveIntervals.iterator()
    }
}