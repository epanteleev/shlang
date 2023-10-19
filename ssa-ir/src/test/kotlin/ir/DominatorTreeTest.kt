package ir

import ir.module.block.BlockViewer
import ir.module.builder.ModuleBuilder
import ir.instruction.IntPredicate
import ir.module.Module
import ir.pass.ana.VerifySSA
import ir.pass.transform.Mem2Reg
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DominatorTreeTest {
    private fun withBasicBlocks(): Module {
        val moduleBuilder = ModuleBuilder.create()
        val builder = moduleBuilder.createFunction("hello", Type.U16, arrayListOf(Type.U16.ptr()))
        val b1 = builder.createLabel()
        builder.branch(b1)

        builder.switchLabel(b1)

        val b2 = builder.createLabel()
        val b3 = builder.createLabel()
        val cmp1 = builder.intCompare(I32Value(12), IntPredicate.Ne, I32Value(43))
        builder.branchCond(cmp1, b2, b3)

        builder.switchLabel(b3)

        val b4 = builder.createLabel()
        val b5 = builder.createLabel()
        val b6 = builder.createLabel()
        val exit = builder.createLabel()

        builder.branch(b4)
        builder.switchLabel(b4)
        val cmp4 = builder.intCompare(I32Value(152), IntPredicate.Ne, I32Value(443))
        builder.branchCond(cmp4, b6, b5)

        builder.switchLabel(b6)
        builder.branch(b4)

        builder.switchLabel(b5)
        builder.branch(exit)

        builder.switchLabel(b2)
        builder.branch(exit)

        builder.switchLabel(exit)
        builder.ret(U16Value(0))

        return moduleBuilder.build()
    }

    @Test
    fun testDominator() {
        val module = withBasicBlocks()
        val prototype = FunctionPrototype("hello", Type.U16, arrayListOf(Type.U16.ptr()))
        val domTree = module.findFunction(prototype).blocks.dominatorTree()

        assertTrue(domTree.dominates(BlockViewer(0), BlockViewer(1)))
        assertTrue(domTree.dominates(BlockViewer(1), BlockViewer(2)))
        assertTrue(domTree.dominates(BlockViewer(1), BlockViewer(3)))
        assertTrue(domTree.dominates(BlockViewer(1), BlockViewer(7)))
        assertTrue(domTree.dominates(BlockViewer(3), BlockViewer(4)))
        assertTrue(domTree.dominates(BlockViewer(4), BlockViewer(5)))
        assertTrue(domTree.dominates(BlockViewer(4), BlockViewer(6)))
    }

    @Test
    fun testCopy() {
        val module = withBasicBlocks()
        val moduleCopy = module.copy()
        assertEquals(module.toString(), moduleCopy.toString())

        val originalMem2Reg = VerifySSA.run(Mem2Reg.run(module))
        val copyMem2Reg     = VerifySSA.run(Mem2Reg.run(module))
        println(originalMem2Reg.toString())
        assertEquals(originalMem2Reg.toString(), copyMem2Reg.toString())
    }
}