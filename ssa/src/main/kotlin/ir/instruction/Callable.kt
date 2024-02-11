package ir.instruction

import ir.Value
import ir.AnyFunctionPrototype
import ir.types.Type


interface Callable: Value {
    fun arguments(): Array<Value>
    fun prototype(): AnyFunctionPrototype
    fun shortName(): String {
        return prototype().shortName()
    }

    companion object {
        internal fun isAppropriateTypes(func: AnyFunctionPrototype, args: Array<Value>): Boolean {
            for ((expectedType, value) in func.arguments() zip args) { //TODO allocation!!!!!
                if (expectedType != value.type()) {
                    return false
                }
            }

            return true
        }

        internal fun isAppropriateTypes(func: AnyFunctionPrototype, pointer: Value, args: Array<Value>): Boolean {
            if (pointer.type() != Type.Ptr) {
                return false
            }

            return isAppropriateTypes(func, args)
        }

        fun isCorrect(call: Callable): Boolean {
            return when(call) {
                is IndirectionVoidCall -> isAppropriateTypes(call.prototype(), call.pointer(), call.arguments())
                is IndirectionCall -> isAppropriateTypes(call.prototype(), call.pointer(), call.arguments())
                else -> isAppropriateTypes(call.prototype(), call.arguments())
            }
        }
    }
}