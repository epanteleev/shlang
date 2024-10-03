package ir.platform.x64.codegen.impl

import asm.x64.*
import ir.types.*
import ir.platform.x64.CallConvention.xmmTemp1
import ir.platform.x64.codegen.MacroAssembler
import ir.platform.x64.codegen.visitors.XmmOperandsVisitorBinaryOp


class FSubCodegen(val type: FloatingPointType, val asm: MacroAssembler): XmmOperandsVisitorBinaryOp {
    private val size: Int = type.sizeOf()

    operator fun invoke(dst: Operand, first: Operand, second: Operand) {
        XmmOperandsVisitorBinaryOp.apply(dst, first, second, this)
    }

    override fun rrr(dst: XmmRegister, first: XmmRegister, second: XmmRegister) {
        if (dst == first) {
            asm.subf(size, second, dst)
        } else {
            asm.movf(size, first, xmmTemp1)
            asm.subf(size, second, xmmTemp1)
            asm.movf(size, xmmTemp1, dst)
        }
    }

    override fun arr(dst: Address, first: XmmRegister, second: XmmRegister) {
        asm.movf(size, first, xmmTemp1)
        asm.subf(size, second, xmmTemp1)
        asm.movf(size, xmmTemp1, dst)
    }

    override fun rar(dst: XmmRegister, first: Address, second: XmmRegister) {
        TODO("Not yet implemented")
    }

    override fun rra(dst: XmmRegister, first: XmmRegister, second: Address) {
        if (dst == first) {
            asm.subf(size, second, dst)
        } else {
            asm.movf(size, first, xmmTemp1)
            asm.subf(size, second, xmmTemp1)
            asm.movf(size, xmmTemp1, dst)
        }
    }

    override fun raa(dst: XmmRegister, first: Address, second: Address) {
        TODO("Not yet implemented")
    }


    override fun ara(dst: Address, first: XmmRegister, second: Address) {
        TODO("Not yet implemented")
    }

    override fun aar(dst: Address, first: Address, second: XmmRegister) {
        asm.movf(size, first, xmmTemp1)
        asm.subf(size, second, xmmTemp1)
        asm.movf(size, xmmTemp1, dst)
    }

    override fun aaa(dst: Address, first: Address, second: Address) {
        TODO("Not yet implemented")
    }

    override fun default(dst: Operand, first: Operand, second: Operand) {
        throw RuntimeException("Internal error: '${ir.instruction.Sub.NAME}' dst=$dst, first=$first, second=$second")
    }
}