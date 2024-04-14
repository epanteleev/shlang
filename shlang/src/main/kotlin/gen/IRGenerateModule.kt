package gen

import gen.TypeConverter.convertToType
import ir.*
import ir.instruction.Alloc
import ir.instruction.ArithmeticBinaryOp
import ir.instruction.FloatPredicate
import ir.instruction.IntPredicate
import types.*
import ir.module.Module
import ir.module.block.Label
import ir.module.builder.impl.FunctionDataBuilder
import ir.module.builder.impl.ModuleBuilder
import ir.types.*
import parser.nodes.*
import java.lang.Exception


data class IRCodeGenError(override val message: String) : Exception(message)

class IRGen private constructor() {
    private val moduleBuilder = ModuleBuilder.create()

    fun visit(programNode: ProgramNode) {
        for (node in programNode.nodes) {
            when (node) {
                is FunctionNode -> IrGenFunction(moduleBuilder, node)
                else -> throw IRCodeGenError("Function expected")
            }
        }
    }

    companion object {
        fun apply(node: ProgramNode): Module {
            //println(node)
            val irGen = IRGen()
            irGen.visit(node)
            val module = irGen.moduleBuilder.build()
            return module
        }
    }
}