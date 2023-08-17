package ir.codegen

import asm.*
import ir.*
import ir.utils.LiveIntervals
import ir.utils.Location
import java.lang.StringBuilder

class RegisterAllocation(private var stackSize: Long,
                         private val registerMap: MutableMap<LocalValue, Operand>,
                         private val liveness: LiveIntervals) {
    val calleeSaveRegisters: Set<GPRegister> by lazy { calleeSaveRegistersInternal() }

    fun reservedStackSize(): Long {
        return stackSize
    }

    private fun calleeSaveRegistersInternal(): Set<GPRegister> {
        val registers = linkedSetOf<GPRegister>(Rbp.rbp)
        for (reg in registerMap.values) {
            if (reg !is GPRegister) {
                continue
            }
            if (CallConvention.gpNonVolatileRegs.contains(reg)) {
                if (registers.contains(reg)) {
                    continue
                }
                registers.add(reg)
            }
        }

        return registers
    }

    fun callerSaveRegisters(loc: Location): Set<GPRegister> {
        val registers = linkedSetOf<GPRegister>()
        for ((value, reg) in registerMap) {
            if (reg is GPRegister && CallConvention.gpVolatileRegs.contains(reg) && !liveness[value].isDiedHere(loc)) {
                registers.add(reg)
            }
        }

        return registers
    }

    fun get(value: Value): AnyOperand {
        return when (value) {
            is ValueInstruction, is ArgumentValue -> registerMap[value] as Operand
            is U8Value  -> Imm(value.u8.toLong(), 1)
            is I8Value  -> Imm(value.i8.toLong(), 1)
            is U16Value -> Imm(value.u16.toLong(), 2)
            is I16Value -> Imm(value.i16.toLong(), 2)
            is U32Value -> Imm(value.u32.toLong(), 4)
            is I32Value -> Imm(value.i32.toLong(), 4)
            is I64Value -> Imm(value.i64, 8)
            is U64Value -> Imm(value.u64, 8)
            else -> throw RuntimeException("expect $value:${value.type()}")
        }
    }

    override fun toString(): String {
        val builder = StringBuilder()
        for ((v, operand) in registerMap) {
            builder.append("$v -> $operand ")
        }

        return builder.toString()
    }
}