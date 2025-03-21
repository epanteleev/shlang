package ir.instruction

import ir.instruction.utils.IRInstructionVisitor
import ir.types.*
import ir.value.Value
import ir.module.block.Block


class Shl private constructor(id: Identity, owner: Block, tp: IntegerType, a: Value, b: Value) : ArithmeticBinary(id, owner, tp, a, b) {
    override fun dump(): String = "%${name()} = $NAME $tp ${lhs()}, ${rhs()}"

    override fun type(): IntegerType = tp.asType()

    override fun <T> accept(visitor: IRInstructionVisitor<T>): T {
        return visitor.visit(this)
    }

    companion object {
        const val NAME = "shl"
        const val FIRST = 0
        const val OFFSET = 1

        fun shl(a: Value, b: Value): InstBuilder<Shl> = { id: Identity, owner: Block ->
            make(id, owner, a, b)
        }

        private fun make(id: Identity, owner: Block, a: Value, b: Value): Shl {
            val aType = a.type()
            val bType = b.type()
            require(isAppropriateTypes(aType, aType, bType)) {
                "incorrect types in '$id' a=$a:$aType, b=$b:$bType"
            }

            return registerUser(Shl(id, owner, aType.asType(), a, b), a, b)
        }

        private fun isAppropriateTypes(tp: Type, aType: Type, bType: Type): Boolean {
            if (tp !is IntegerType) {
                return false
            }
            return aType == tp && bType == tp
        }

        fun typeCheck(binary: ArithmeticBinary): Boolean {
            return isAppropriateTypes(binary.type(), binary.lhs().type(), binary.rhs().type())
        }
    }
}