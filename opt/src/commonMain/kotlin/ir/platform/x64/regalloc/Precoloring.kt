package ir.platform.x64.regalloc

import ir.value.LocalValue
import asm.x64.Operand
import common.assertion
import ir.instruction.*
import ir.liveness.GroupedLiveIntervals
import ir.liveness.LiveRange
import ir.liveness.LiveIntervals
import ir.types.TupleType
import ir.value.Value

//TODO
class Precoloring private constructor(private val intervals: LiveIntervals, private val precolored: Map<LocalValue, Operand>) {
    private val visited = hashSetOf<LocalValue>()
    private val groups = hashMapOf<Group, LiveRange>()

    private fun build(): GroupedLiveIntervals {
        mergePhiOperands()
        completeOtherGroups()

        val result = groups.toList().sortedBy { (_, value) -> value.begin().order } // TODO
        val map = linkedMapOf<Group, LiveRange>()
        for ((k, v) in result) {
            map[k] = v
        }

        return GroupedLiveIntervals(map)
    }

    private fun handlePhiOperands(value: Phi, range: LiveRange) {
        val group = arrayListOf<LocalValue>(value)
        visited.add(value)
        var liveRange = range
        for (used in value.operands()) {
            if (used !is LocalValue) {
                continue
            }
            assertion(used is Copy) { "expect this invariant: used=$used" }

            liveRange = liveRange.merge(intervals[used])
            group.add(used)
            visited.add(used)
        }

        groups[Group(group, null)] = liveRange
    }

    private fun handleTuple(value: LocalValue, range: LiveRange) {
        visited.add(value)

        value.usedIn().forEach { proj ->
            proj as Projection
            if (visited.contains(proj)) {
                return@forEach
            }

            val liveRange = range.merge(intervals[proj])
            val group = Group(arrayListOf<LocalValue>(proj), null)
            groups[group] = liveRange

            visited.add(proj)
        }
    }

    private fun mergePhiOperands() {
        for ((value, range) in intervals) {
            if (value is Phi) {
                handlePhiOperands(value, range)
            } else if (value.type() is TupleType) {
                handleTuple(value, range)
            }
        }
    }

    private fun completeOtherGroups() {
        for ((value, range) in intervals) {
            if (value is Phi) {
                continue
            }
            if (visited.contains(value)) {
                continue
            }

            val op = precolored[value]
            groups[Group(arrayListOf(value), op)] = range
            visited.add(value)
        }
    }

    companion object {
        fun evaluate(liveIntervals: LiveIntervals, registerMap: Map<LocalValue, Operand>): GroupedLiveIntervals {
            return Precoloring(liveIntervals, registerMap).build()
        }
    }
}