package ir.platform.x64.codegen.impl

import asm.x64.*
import ir.types.*
import ir.platform.x64.CallConvention.temp1
import ir.platform.x64.codegen.utils.GPOperandVisitorUnaryOp


data class NegCodegen(val type: PrimitiveType, val asm: Assembler): GPOperandVisitorUnaryOp {
    private val size: Int = type.size()

    operator fun invoke(dst: Operand, src: Operand) {
        when (type) {
            is IntegerType -> ir.platform.x64.codegen.utils.ApplyClosure(dst, src, this as GPOperandVisitorUnaryOp)
            else -> throw RuntimeException("Unknown type=$type, dst=$dst, src=$src")
        }
    }

    override fun rr(dst: GPRegister, src: GPRegister) {
        if (dst == src) {
            asm.neg(size, dst)
        } else {
            asm.mov(size, src, dst)
            asm.neg(size, dst)
        }
    }

    override fun ra(dst: GPRegister, src: Address) {
        asm.mov(size, src, dst)
        asm.neg(size, dst)
    }

    override fun ar(dst: Address, src: GPRegister) {
        asm.mov(size, src, dst)
        asm.neg(size, dst)
    }

    override fun aa(dst: Address, src: Address) {
        if (dst == src) {
            asm.neg(size, dst)
        } else {
            asm.mov(size, src, temp1)
            asm.neg(size, temp1)
            asm.mov(size, temp1, dst)
        }
    }

    override fun ri(dst: GPRegister, src: Imm32) {
        asm.mov(size, Imm32(-src.value()), dst)
    }

    override fun ai(dst: Address, src: Imm32) {
        asm.mov(size, Imm32(-src.value()), dst)
    }

    override fun default(dst: Operand, src: Operand) {
        throw RuntimeException("Internal error: ${ir.instruction.Neg.NAME} dst=$dst, src=$src")
    }
}