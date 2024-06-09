package ir.instruction

import common.assertion
import ir.Value
import ir.asType
import ir.types.*
import ir.instruction.utils.IRInstructionVisitor
import ir.module.block.Block


class Store private constructor(id: Identity, owner: Block, pointer: Value, value: Value, private val valueType: NonTrivialType):
    Instruction(id, owner, arrayOf(pointer, value)) {
    override fun dump(): String {
        return "$NAME ptr ${pointer()}, ${value().type()} ${value()}"
    }

    fun pointer(): Value {
        assertion(operands.size == 2) {
            "size should be 2 in $this instruction"
        }

        return operands[0]
    }

    fun value(): Value {
        assertion(operands.size == 2) {
            "size should be 2 in $this instruction"
        }

        return operands[1]
    }

    fun valueType(): NonTrivialType = valueType

    override fun<T> visit(visitor: IRInstructionVisitor<T>): T {
        return visitor.visit(this)
    }

    override fun hashCode(): Int {
        return valueType.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Store
        return pointer() == other.pointer() && value() == other.value() // TODO Unsafe
    }

    companion object {
        const val NAME = "store"

        fun make(id: Identity, owner: Block, pointer: Value, value: Value): Store {
            val pointerType = pointer.type()
            val valueType   = value.type()
            require(isAppropriateTypes(pointerType, valueType)) {
                "inconsistent types: pointer=$pointer:$pointerType, value=$value:$valueType"
            }

            return registerUser(Store(id, owner, pointer, value, value.asType()), pointer, value)
        }

        private fun isAppropriateTypes(pointerType: Type, valueType: Type): Boolean {
            if (valueType !is PrimitiveType) {
                return false
            }

            if (pointerType !is PointerType) {
                return false
            }

            return true
        }

        fun typeCheck(store: Store): Boolean {
            return isAppropriateTypes(store.pointer().type(), store.value().type())
        }
    }
}