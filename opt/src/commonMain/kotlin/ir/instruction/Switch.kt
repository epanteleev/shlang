package ir.instruction

import common.forEachWith
import ir.value.Value
import ir.instruction.utils.IRInstructionVisitor
import ir.module.block.Block
import ir.types.IntegerType
import ir.value.IntegerConstant


class Switch private constructor(id: Identity, owner: Block,
                                 private val value: Value,
                                 private val default: Block,
                                 private val table: Array<IntegerConstant>,
                                 targets: Array<Block>):
    TerminateInstruction(id, owner, arrayOf(value), targets + arrayOf(default)) {
    override fun dump(): String {
        val builder = StringBuilder()
        builder.append("$NAME ")
            .append(value.type().toString())
            .append(' ')
            .append(value.toString())
            .append(", label ")
            .append(default)

        builder.append(" [")
        table.forEachWith(targets) { value, bb, i ->
            if (bb == default) {
                return@forEachWith
            }

            builder.append("$value: $bb")
            if (i < table.size - 1) {
                builder.append(", ")
            }
        }
        builder.append(']')
        return builder.toString()
    }

    fun value(): Value = value
    fun default(): Block = default
    fun table(): Array<IntegerConstant> = table

    override fun<T> visit(visitor: IRInstructionVisitor<T>): T {
        return visitor.visit(this)
    }

    companion object {
        const val NAME = "switch"

        fun make(id: Identity, owner: Block, value: Value, default: Block, table: Array<IntegerConstant>, targets: Array<Block>): Switch {
            require(isAppropriateType(value)) {
                "inconsistent types in '$id': value='${value}:${value.type()}', table='${table.joinToString { it.type().toString() }}'"
            }

            return registerUser(Switch(id, owner, value, default, table, targets), value)
        }

        private fun isAppropriateType(value: Value): Boolean {
            return value.type() is IntegerType
        }

        fun typeCheck(switch: Switch): Boolean {
            return true
        }
    }
}