package ir.instruction

import common.assertion
import ir.value.Value
import ir.types.Type
import ir.module.block.Block
import ir.module.AnyFunctionPrototype
import ir.instruction.utils.IRInstructionVisitor


class VoidCall private constructor(id: Identity, owner: Block, private val func: AnyFunctionPrototype, args: Array<Value>, target: Block):
    TerminateInstruction(id, owner, args, arrayOf(target)), Callable {
    init {
        assertion(func.returnType() == Type.Void) { "Must be ${Type.Void}" }
    }

    override fun arguments(): Array<Value> {
        return operands
    }

    override fun prototype(): AnyFunctionPrototype {
        return func
    }

    override fun target(): Block {
        assertion(targets.size == 1) {
            "should be only one target, but '$targets' found"
        }

        return targets[0]
    }

    override fun dump(): String {
        val builder = StringBuilder()
        builder.append("call ${Type.Void} @${func.name}(")
        operands.joinTo(builder) { "$it:${it.type()}"}
        builder.append(") br label ${target()}")
        return builder.toString()
    }

    override fun type(): Type = Type.Void

    override fun<T> visit(visitor: IRInstructionVisitor<T>): T {
        return visitor.visit(this)
    }

    companion object {
        fun make(id: Identity, owner: Block, func: AnyFunctionPrototype, args: List<Value>, target: Block): VoidCall {
            val argsArray = args.toTypedArray()
            require(Callable.isAppropriateTypes(func, argsArray)) {
                args.joinToString(prefix = "inconsistent types, prototype='${func.shortName()}', ")
                { "$it: ${it.type()}" }
            }

            return registerUser(VoidCall(id, owner, func, argsArray, target), args.iterator())
        }
    }
}