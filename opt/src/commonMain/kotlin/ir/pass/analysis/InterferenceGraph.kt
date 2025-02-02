package ir.pass.analysis

import ir.module.FunctionData
import ir.module.MutationMarker
import ir.module.Sensitivity
import ir.pass.common.AnalysisResult
import ir.pass.common.FunctionAnalysisPass
import ir.pass.common.FunctionAnalysisPassFabric
import ir.pass.analysis.intervals.LiveIntervalsFabric
import ir.pass.analysis.intervals.LiveRange
import ir.pass.common.AnalysisType
import ir.value.LocalValue


class InterferenceGraph(private val graph: MutableMap<LocalValue, MutableSet<LocalValue>>, marker: MutationMarker): AnalysisResult(marker) {
    override fun toString(): String = buildString {
        for ((value, neighbors) in graph) {
            append("Value: $value\n")
            append("Neighbors: $neighbors\n")
        }
    }

    internal fun addEdge(from: LocalValue, to: LocalValue) {
        val fromEdge = graph[from]
        if (fromEdge == null) {
            graph[from] = hashSetOf(to)
        } else {
            fromEdge.add(to)
        }

        val toEdge = graph[to]
        if (toEdge == null) {
            graph[to] = hashSetOf(from)
        } else {
            toEdge.add(from)
        }
    }

    fun neighbors(value: LocalValue): Set<LocalValue>? {
        return graph[value]
    }
}

private class InterferenceGraphBuilder(functionData: FunctionData): FunctionAnalysisPass<InterferenceGraph>() {
    private val liveIntervals = functionData.analysis(LiveIntervalsFabric)
    private val interferenceGraph = InterferenceGraph(mutableMapOf(), functionData.marker())

    override fun run(): InterferenceGraph {
        val active = hashMapOf<LocalValue, LiveRange>()
        for ((v1, interval1) in liveIntervals) {
            active.entries.retainAll { (_, interval) ->
                interval1.end().to > interval.begin().from
            }
            for ((v2, interval2) in active) {
                if (interval1.intersect(interval2)) {
                    interferenceGraph.addEdge(v1, v2)
                }
            }
            active[v1] = interval1
        }

        return interferenceGraph
    }
}

object InterferenceGraphFabric : FunctionAnalysisPassFabric<InterferenceGraph>() {
    override fun type(): AnalysisType {
        return AnalysisType.INTERFERENCE_GRAPH
    }

    override fun sensitivity(): Sensitivity {
        return Sensitivity.CONTROL_AND_DATA_FLOW
    }

    override fun create(functionData: FunctionData): InterferenceGraph {
        return InterferenceGraphBuilder(functionData).run()
    }
}