package ir.instruction

import ir.Value
import ir.types.*
import ir.module.block.Block
import collections.forEachWith
import ir.instruction.utils.Visitor


class Phi private constructor(name: String, ty: PrimitiveType, private var incoming: List<Block>, incomingValue: Array<Value>):
    ValueInstruction(name, ty, incomingValue) {

    override fun dump(): String {
        val builder = StringBuilder()
        builder.append("%$identifier = phi $tp [")
        operands.zip(incoming).joinTo(builder) {//todo
            "${it.first}: ${it.second}"
        }
        builder.append(']')
        return builder.toString()
    }

    override fun type(): PrimitiveType {
        return tp as PrimitiveType
    }

    fun incoming(): List<Block> {
        return incoming
    }

    fun forAllIncoming(closure: (Block, Value) -> Unit) {
        incoming().forEachWith(operands().asIterable()) { bb, value ->
            closure(bb, value)
        }
    }

    override fun copy(newUsages: List<Value>): Instruction {
        return make(identifier, type(), incoming, newUsages.toTypedArray())
    }

    fun copy(newUsages: Array<Value>, incoming: List<Block>): Phi {
        return make(identifier, type(), incoming, newUsages.clone())
    }

    override fun visit(visitor: Visitor) {
        visitor.visit(this)
    }

    fun update(newUsages: List<Value>, newIncoming: List<Block>): Phi {
        update(newUsages)
        incoming = newIncoming
        return this
    }

    companion object {
        fun make(name: String, ty: PrimitiveType): Phi {
            return Phi(name, ty, arrayListOf(), arrayOf())
        }

        fun make(name: String, ty: PrimitiveType, incoming: List<Block>, incomingValue: Array<Value>): Phi {
            return registerUser(Phi(name, ty, incoming, incomingValue), incomingValue.iterator())
        }

        fun makeUncompleted(name: String, type: PrimitiveType, incoming: Value, predecessors: List<Block>): Phi {
            val incomingType = incoming.type()
            require(incomingType is PointerType) {
                "should be pointer type, but incoming.type=$incomingType"
            }

            val values = predecessors.mapTo(arrayListOf()) { incoming }.toTypedArray() //Todo
            return Phi(name, type, predecessors, values)
        }

        private fun isAppropriateTypes(type: PrimitiveType, incomingValue: Array<Value>): Boolean {
            for (use in incomingValue) {
                if (type != use.type()) {
                    return false
                }
            }

            return true
        }

        fun isCorrect(phi: Phi): Boolean {
            return isAppropriateTypes(phi.type(), phi.operands())
        }
    }
}