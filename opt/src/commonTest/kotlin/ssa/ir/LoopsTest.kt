package ssa.ir

import ir.instruction.*
import ir.module.Module
import ir.module.block.BlockViewer
import ir.module.builder.impl.ModuleBuilder
import ir.pass.analysis.traverse.LinearScanOrderFabric
import ir.pass.analysis.LoopDetectionPassFabric
import ir.types.I64Type
import ir.types.Type
import ir.value.constant.I32Value
import ir.value.constant.I64Value
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class LoopsTest {
    private fun makeLoop(): Module {
        val moduleBuilder = ModuleBuilder.create()
        val builder = moduleBuilder.createFunction("test", I64Type, arrayListOf())

        val b1 = builder.createLabel()
        val b2 = builder.createLabel()
        val b3 = builder.createLabel()
        val b4 = builder.createLabel()
        val b5 = builder.createLabel()
        val b6 = builder.createLabel()
        val b7 = builder.createLabel()

        builder.branch(b1)
        builder.switchLabel(b1)
        val cmp1 = builder.icmp(
            I32Value(12), IntPredicate.Ne,
            I32Value(43)
        )
        builder.branchCond(cmp1, b3, b2)

        builder.switchLabel(b3)
        builder.branch(b1)

        builder.switchLabel(b2)
        val cmp2 = builder.icmp(
            I32Value(12), IntPredicate.Ne,
            I32Value(43)
        )
        builder.branchCond(cmp2, b5, b4)

        builder.switchLabel(b5)
        builder.ret(I64Type, arrayOf(I64Value(0)))

        builder.switchLabel(b4)
        val cmp3 = builder.icmp(
            I32Value(12), IntPredicate.Ne,
            I32Value(43)
        )
        builder.branchCond(cmp3, b7, b6)

        builder.switchLabel(b6)
        builder.branch(b4)

        builder.switchLabel(b7)
        builder.branch(b1)

        return moduleBuilder.build()
    }

    @Test
    fun testLoopDetection() {
        val module = makeLoop()
        val df = module.findFunction("test")
        val loopInfo = df.analysis(LoopDetectionPassFabric)
        assertEquals(3, loopInfo.size)
        assertTrue { loopInfo[BlockViewer(1)] != null }
        assertTrue { loopInfo[BlockViewer(4)] != null }
    }

    @Test
    fun testLinearScanOrdering() {
        val module = makeLoop()
        val df = module.findFunction("test")
        val linearScanOrder = df.analysis(LinearScanOrderFabric)
        assertEquals(8, linearScanOrder.size)
        assertEquals(0, linearScanOrder[0].index)
        assertEquals(1, linearScanOrder[1].index)
        assertEquals(3, linearScanOrder[2].index)
        assertEquals(2, linearScanOrder[3].index)
        assertEquals(4, linearScanOrder[4].index)
        assertEquals(6, linearScanOrder[5].index)
        assertEquals(7, linearScanOrder[6].index)
        assertEquals(5, linearScanOrder[7].index)
    }
}