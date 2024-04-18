package ir.instruction

import ir.Value
import ir.instruction.utils.IRInstructionVisitor
import ir.types.ArithmeticType
import ir.types.Type

enum class ArithmeticBinaryOp {
    Add {
        override fun toString(): String {
            return "add"
        }
    },
    Sub {
        override fun toString(): String {
            return "sub"
        }
    },
    Mul {
        override fun toString(): String {
            return "mul"
        }
    },
    Mod {
        override fun toString(): String {
            return "mod"
        }
    },
    Div {
        override fun toString(): String {
            return "div"
        }
    },
    Shr {
        override fun toString(): String {
            return "shr"
        }
    },
    Shl {
        override fun toString(): String {
            return "shl"
        }
    },
    And {
        override fun toString(): String {
            return "and"
        }
    },
    Or {
        override fun toString(): String {
            return "or"
        }
    },
    Xor {
        override fun toString(): String {
            return "xor"
        }
    };
}

class ArithmeticBinary private constructor(name: String, tp: ArithmeticType, a: Value, val op: ArithmeticBinaryOp, b: Value) :
    ValueInstruction(name, tp, arrayOf(a, b)) {
    override fun dump(): String {
        return "%$identifier = $op $tp ${first()}, ${second()}"
    }

    override fun type(): ArithmeticType {
        return tp as ArithmeticType
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

    override fun<T> visit(visitor: IRInstructionVisitor<T>): T {
        return visitor.visit(this)
    }

    companion object {
        fun make(name: String, type: ArithmeticType, a: Value, op: ArithmeticBinaryOp, b: Value): ArithmeticBinary {
            val aType = a.type()
            val bType = b.type()
            require(isAppropriateTypes(type, aType, bType)) {
                "incorrect types in '$name' but type=$type, a=$a:$aType, b=$b:$bType"
            }

            return registerUser(ArithmeticBinary(name, type, a, op, b), a, b)
        }

        private fun isAppropriateTypes(tp: ArithmeticType, aType: Type, bType: Type): Boolean {
            return aType == tp && bType == tp
        }

        fun typeCheck(binary: ArithmeticBinary): Boolean {
            return isAppropriateTypes(binary.type(), binary.first().type(), binary.second().type())
        }
    }
}