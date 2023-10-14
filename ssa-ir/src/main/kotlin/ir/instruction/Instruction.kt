package ir.instruction

import ir.*


abstract class Instruction(protected val tp: Type, protected val operands: Array<Value>) {
    fun usedInstructions(): List<ValueInstruction> {
        return operands.filterIsInstanceTo<ValueInstruction, MutableList<ValueInstruction>>(arrayListOf())
    }

    fun usages(): Array<Value> {
        return operands
    }

    fun update(newUsages: Collection<Value>) {
        for ((i, v) in newUsages.withIndex()) { //Todo find nice API
            operands[i] = v
        }
    }

    abstract fun copy(newUsages: List<Value>): Instruction
    abstract override fun equals(other: Any?): Boolean
    abstract override fun hashCode(): Int
    abstract fun dump(): String
}