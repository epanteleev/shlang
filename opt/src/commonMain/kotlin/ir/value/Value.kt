package ir.value

import ir.types.*
import ir.value.constant.UndefinedValue
import ir.instruction.matching.ValueMatcher


interface Value {
    fun type(): Type

    fun isa(matcher: ValueMatcher): Boolean {
        return matcher(this)
    }

    fun match(matcher: ValueMatcher, action: (Value) -> Value?) {
        if (!isa(matcher)) {
            return
        }

        action(this)
    }

    companion object {
        val UNDEF: UndefinedValue = UndefinedValue
    }
}

inline fun<reified T> Value.asType(): T {
    val t = type()
    if (t !is T) {
        throw RuntimeException("Cannot cast $t to ${T::class}")
    }

    return t
}

inline fun<reified T: Value> Value.asValue(): T {
    val isValue = this is T
    if (!isValue) {
        throw RuntimeException("Cannot cast $this to ${T::class}")
    }

    return this
}