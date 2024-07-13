package ir.instruction

import ir.value.*
import ir.module.block.Block
import ir.types.NonTrivialType


abstract class ValueInstruction(id: Identity,
                                owner: Block,
                                protected val tp: NonTrivialType,
                                operands: Array<Value>):
    Instruction(id, owner, operands),
    LocalValue {
    override var usedIn: MutableList<Instruction> = arrayListOf()
    override fun name(): String {
        return "${owner.index}x${id}"
    }

    override fun toString(): String {
        return "%${name()}"
    }

    override fun type(): NonTrivialType = tp
}