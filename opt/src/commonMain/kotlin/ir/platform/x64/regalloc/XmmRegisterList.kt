package ir.platform.x64.regalloc

import asm.x64.*
import ir.types.*
import ir.platform.x64.CallConvention


class XmmRegisterList(arguments: List<XmmRegister>) {
    private var freeRegisters = CallConvention.availableXmmRegisters(arguments).toMutableList()
    private val usedCalleeSaveRegisters = mutableSetOf<XmmRegister>()

    fun pickRegister(): XmmRegister? {
        if (freeRegisters.isEmpty()) {
            return null
        }
        val reg = freeRegisters.removeLast()
        if (CallConvention.xmmCalleeSaveRegs.contains(reg)) {
            usedCalleeSaveRegisters.add(reg)
        }

        return reg
    }

    fun returnRegister(reg: XmmRegister) {
        if (CallConvention.xmmArgumentRegister.contains(reg)) {
            return
        }
        if (freeRegisters.contains(reg)) {
            return
        }

        freeRegisters.add(reg)
    }
}