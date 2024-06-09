package gen

import types.*
import ir.module.Module
import ir.module.builder.impl.ModuleBuilder
import ir.types.*
import parser.nodes.*


data class IRCodeGenError(override val message: String) : Exception(message)

class IRGen private constructor() {
    private val moduleBuilder = ModuleBuilder.create()
    private val typeHolder = TypeHolder.default()

    fun visit(programNode: ProgramNode) {
        for (node in programNode.nodes) {
            when (node) {
                is FunctionNode -> IrGenFunction(moduleBuilder, typeHolder, node)
                is Declaration ->  declare(node)
                else -> throw IRCodeGenError("Function expected")
            }
        }
    }

    private fun declare(node: Declaration) {
        val types = node.resolveType(typeHolder)
        for (type in types) {
            when (type) {
                is CFunctionType -> {
                    val argTypes = type.argsTypes.map { TypeConverter.toIRType<NonTrivialType>(it) }
                    val returnType = TypeConverter.toIRType<Type>(type.retType)
                    moduleBuilder.createExternFunction(type.name, returnType, argTypes)
                }
                else -> throw IRCodeGenError("Function or struct expected")
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