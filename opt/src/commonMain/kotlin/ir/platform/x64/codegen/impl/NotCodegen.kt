package ir.platform.x64.codegen.impl

import asm.Operand
import asm.x64.*
import ir.types.*
import ir.instruction.Not
import ir.platform.x64.CallConvention.temp1
import ir.platform.x64.codegen.X64MacroAssembler
import ir.platform.x64.codegen.visitors.GPOperandsVisitorUnaryOp


internal data class NotCodegen(val type: IntegerType, val asm: X64MacroAssembler): GPOperandsVisitorUnaryOp {
    private val size = type.sizeOf()

    operator fun invoke(dst: Operand, src: Operand) {
        GPOperandsVisitorUnaryOp.apply(dst, src, this)
    }

    override fun rr(dst: GPRegister, src: GPRegister) {
        asm.copy(size, src, dst)
        asm.not(size, dst)
    }

    override fun ra(dst: GPRegister, src: Address) {
        asm.mov(size, src, dst)
        asm.not(size, dst)
    }

    override fun ar(dst: Address, src: GPRegister) {
        asm.mov(size, src, dst)
        asm.not(size, dst)
    }

    override fun aa(dst: Address, src: Address) {
        if (dst == src) {
            asm.not(size, dst)
        } else {
            asm.mov(size, src, temp1)
            asm.not(size, temp1)
            asm.mov(size, temp1, dst)
        }
    }

    override fun ri(dst: GPRegister, src: Imm32) {
        asm.copy(size, Imm32.of(src.value().inv()), dst)
    }

    override fun ai(dst: Address, src: Imm32) {
        asm.mov(size, Imm64.of(src.value().inv()), dst)
    }

    override fun default(dst: Operand, src: Operand) {
        throw RuntimeException("Internal error: '${Not.NAME}' dst=$dst, src=$$src")
    }
}