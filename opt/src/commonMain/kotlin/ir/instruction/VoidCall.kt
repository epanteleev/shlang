package ir.instruction

import common.arrayWrapperOf
import common.assertion
import ir.attributes.FunctionAttribute
import ir.value.Value
import ir.types.Type
import ir.module.block.Block
import ir.module.AnyFunctionPrototype
import ir.instruction.utils.IRInstructionVisitor
import ir.module.DirectFunctionPrototype


class VoidCall private constructor(id: Identity,
                                   owner: Block,
                                   private val func: DirectFunctionPrototype,
                                   private val attributes: Set<FunctionAttribute>,
                                   args: Array<Value>,
                                   target: Block):
    TerminateInstruction(id, owner, args, arrayOf(target)), Callable {
    init {
        assertion(func.returnType() == Type.Void) { "Must be ${Type.Void}" }
    }

    override fun arguments(): List<Value> {
        return arrayWrapperOf(operands)
    }

    override fun prototype(): DirectFunctionPrototype {
        return func
    }

    override fun attributes(): Set<FunctionAttribute> = attributes

    override fun target(): Block {
        assertion(targets.size == 1) {
            "should be only one target, but '$targets' found"
        }

        return targets[0]
    }

    override fun dump(): String {
        val builder = StringBuilder()
        builder.append("call ${Type.Void} @${func.name}")
        printArguments(builder)
        return builder.toString()
    }

    override fun type(): Type = Type.Void

    override fun<T> visit(visitor: IRInstructionVisitor<T>): T {
        return visitor.visit(this)
    }

    companion object {
        fun make(id: Identity, owner: Block, func: DirectFunctionPrototype, attributes: Set<FunctionAttribute>, args: List<Value>, target: Block): VoidCall {
            require(Callable.isAppropriateTypes(func, args)) {
                args.joinToString(prefix = "inconsistent types, prototype='${func.shortDescription()}', ")
                { "$it: ${it.type()}" }
            }
            val argsArray = args.toTypedArray()
            return registerUser(VoidCall(id, owner, func, attributes, argsArray, target), args.iterator())
        }
    }
}