package ir.instruction.lir

import ir.instruction.*
import ir.instruction.utils.IRInstructionVisitor
import ir.module.block.Block
import ir.types.ArithmeticType
import ir.types.FloatingPointType
import ir.types.Type
import ir.types.asType
import ir.value.Value


class Fxor private constructor(id: Identity, owner: Block, tp: ArithmeticType, a: Value, b: Value) : ArithmeticBinary(id, owner, tp, a, b) {
    override fun dump(): String {
        return "%${name()} = $NAME $tp ${lhs()}, ${rhs()}"
    }

    override fun type(): FloatingPointType = tp.asType()

    override fun <T> accept(visitor: IRInstructionVisitor<T>): T {
        return visitor.visit(this)
    }

    companion object {
        const val NAME = "fxor"
        const val FIRST = 0
        const val SECOND = 1

        fun xor(a: Value, b: Value): InstBuilder<Fxor> = { id: Identity, owner: Block ->
            make(id, owner, a, b)
        }

        private fun make(id: Identity, owner: Block, a: Value, b: Value): Fxor {
            val aType = a.type()
            val bType = b.type()
            require(isAppropriateTypes(aType, aType, bType)) {
                "incorrect types in '$id' a=$a:$aType, b=$b:$bType"
            }

            return registerUser(Fxor(id, owner, aType.asType(), a, b), a, b)
        }

        private fun isAppropriateTypes(tp: Type, aType: Type, bType: Type): Boolean {
            if (tp !is FloatingPointType) {
                return false
            }

            return aType == tp && bType == tp
        }

        fun typeCheck(binary: ArithmeticBinary): Boolean {
            return isAppropriateTypes(binary.type(), binary.lhs().type(), binary.rhs().type())
        }
    }
}