package ir.instruction

import ir.*
import ir.module.block.Block
import ir.types.NonTrivialType

typealias Identity = Int

abstract class ValueInstruction(protected val id: Identity,
                                owner: Block,
                                protected val tp: NonTrivialType,
                                operands: Array<Value>):
    Instruction(owner, operands),
    LocalValue {
    private var usedIn: MutableList<Instruction> = arrayListOf()

    internal fun addUser(instruction: Instruction) {
        usedIn.add(instruction)
    }

    internal fun killUser(instruction: Instruction) {
        usedIn.remove(instruction)
    }

    fun usedIn(): List<Instruction> {
        return usedIn
    }

    override fun name(): String {
        return "${owner.index}x${id}"
    }

    override fun toString(): String {
        return "%${name()}"
    }

    override fun type(): NonTrivialType = tp

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ValueInstruction

        return id == other.id && owner.index == other.owner.index
    }

    override fun hashCode(): Int {
        return id + owner.index
    }

    companion object {
        fun replaceUsages(inst: ValueInstruction, toValue: Value) {
            val usedIn = inst.usedIn
            inst.usedIn = arrayListOf()
            for (user in usedIn) {
                for ((idxUse, use) in user.operands().withIndex()) {
                    if (use != inst) {
                        continue
                    }
                    // New value can use the old value
                    if (user == toValue) {
                        continue
                    }

                    user.update(idxUse, toValue)
                }
            }
        }
    }
}