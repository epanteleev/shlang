package ir.instruction

import ir.Value
import ir.types.*
import ir.instruction.utils.Visitor


enum class FloatPredicate {
    Oeq { // ordered and equal
        override fun toString(): String = "oeq"
    },
    Ogt { // ordered and greater than
        override fun toString(): String = "ogt"
    },
    Oge { // ordered and greater than or equal
        override fun toString(): String = "oge"
    },
    Olt { // ordered and less than
        override fun toString(): String = "olt"
    },
    Ole { // ordered and less than or equal
        override fun toString(): String = "ole"
    },
    One { // ordered and not equal
        override fun toString(): String = "one"
    },
    Ord { // ordered (no nans)
        override fun toString(): String = "ord"
    },
    Ueq { // unordered or equal
        override fun toString(): String = "ueq"
    },
    Ugt { // unordered or greater than
        override fun toString(): String = "ugt"
    },
    Uge { // unordered or greater than or equal
        override fun toString(): String = "uge"
    },
    Ult { // unordered or less than
        override fun toString(): String = "ult"
    },
    Ule { // unordered or less than or equal
        override fun toString(): String = "ule"
    },
    Uno { // unordered (either nans)
        override fun toString(): String = "uno"
    }
}

class FloatCompare private constructor(name: String, a: Value, private val predicate: FloatPredicate, b: Value) :
    ValueInstruction(name, Type.U1, arrayOf(a, b)) {
    override fun dump(): String {
        return "%$identifier = fcmp $predicate ${first().type()} ${first()}, ${second()}"
    }

    fun predicate(): FloatPredicate {
        return predicate
    }

    fun compareType(): FloatingPointType {
        return first().type() as FloatingPointType
    }

    fun first(): Value {
        assert(operands.size == 2) {
            "size should be 2 in $this instruction"
        }

        return operands[0]
    }

    fun second(): Value {
        assert(operands.size == 2) {
            "size should be 2 in $this instruction"
        }

        return operands[1]
    }

    override fun copy(newUsages: List<Value>): FloatCompare {
        assert(newUsages.size == 2) {
            "should be, but newUsages=$newUsages"
        }

        return make(identifier, newUsages[0], predicate, newUsages[1])
    }

    override fun visit(visitor: Visitor) {
        visitor.visit(this)
    }

    companion object {
        fun make(name: String, a: Value, predicate: FloatPredicate, b: Value): FloatCompare {
            val aType = a.type()
            val bType = b.type()
            require(isAppropriateType(aType, bType)) {
                "should be the same types, but a.type=$aType, b.type=$bType"
            }

            return registerUser(FloatCompare(name, a, predicate, b), a, b)
        }

        private fun isAppropriateType(aType: Type, bType: Type): Boolean {
            return aType == bType && aType is FloatingPointType
        }

        fun isCorrect(icmp: IntCompare): Boolean {
            return isAppropriateType(icmp.first().type(), icmp.second().type())
        }
    }
}