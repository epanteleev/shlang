package ir.instruction

import ir.module.block.Block
import ir.types.TrivialType
import ir.types.TupleType
import ir.value.LocalValue
import ir.value.Value


abstract class TupleInstruction(id: Identity,
                                owner: Block,
                                tp: TrivialType,
                                operands: Array<Value>):
    ValueInstruction(id, owner, tp, operands), LocalValue {

    fun proj(index: Int): Projection? {
        for (user in usedIn()) {
            user as Projection
            if (user.index() == index) {
                return user
            }
        }
        return null
    }

    abstract override fun type(): TupleType
}