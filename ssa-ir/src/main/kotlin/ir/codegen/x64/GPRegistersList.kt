package ir.codegen.x64

import asm.x64.GPRegister
import asm.x64.Rbp
import asm.x64.Register
import ir.StackAlloc
import ir.Type
import ir.ValueInstruction

class GPRegistersList {
    private var freeRegisters = CallConvention.availableRegisters.toMutableList().asReversed()
    private val usedCalleeSaveRegisters = mutableSetOf<GPRegister>(Rbp.rbp)

    fun pickRegister(value: ValueInstruction): Register? {
        require(value.type() == Type.U1 ||
                value.type().isArithmetic() ||
                value.type().isPointer()) {
            "found ${value.type()}"
        }
        require(value !is StackAlloc) { "cannot be" }

        if (freeRegisters.isEmpty()) {
            return null
        }
        val reg = freeRegisters.removeLast()
        if (CallConvention.gpCalleeSaveRegs.contains(reg)) {
            usedCalleeSaveRegisters.add(reg)
        }
        return reg
    }

    fun returnRegister(reg: GPRegister) {
        freeRegisters.add(reg)
    }

    fun usedCalleeSaveRegisters(): Set<GPRegister> {
        return usedCalleeSaveRegisters
    }
}