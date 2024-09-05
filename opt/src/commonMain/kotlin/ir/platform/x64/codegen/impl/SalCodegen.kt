package ir.platform.x64.codegen.impl

import asm.x64.*
import ir.types.*
import ir.platform.x64.codegen.visitors.GPOperandsVisitorBinaryOp
import ir.platform.x64.codegen.visitors.XmmOperandsVisitorBinaryOp


class SalCodegen(val type: ArithmeticType, val asm: Assembler): GPOperandsVisitorBinaryOp {
    private val size: Int = type.sizeOf()

    operator fun invoke(dst: Operand, first: Operand, second: Operand) {
        GPOperandsVisitorBinaryOp.apply(dst, first, second, this)
    }

    override fun rrr(dst: GPRegister, first: GPRegister, second: GPRegister) {
        when (dst) {
            first -> asm.sal(size, second, dst)
            else -> {
                asm.mov(size, first, dst)
                asm.sal(size, second, dst)
            }
        }
    }

    override fun arr(dst: Address, first: GPRegister, second: GPRegister) {
        TODO("Not yet implemented")
    }

    override fun rar(dst: GPRegister, first: Address, second: GPRegister) {
        asm.mov(size, first, dst)
        asm.sal(size, second, dst)
    }

    override fun rir(dst: GPRegister, first: Imm32, second: GPRegister) {
        asm.mov(size, first, dst)
        asm.sal(size, second, dst)
    }

    override fun rra(dst: GPRegister, first: GPRegister, second: Address) {
        TODO("Not yet implemented")
    }

    override fun rri(dst: GPRegister, first: GPRegister, second: Imm32) {
        if (dst == first) {
            asm.sal(size, second, dst)
        } else {
            asm.mov(size, first, dst)
            asm.sal(size, second, dst)
        }
    }

    override fun raa(dst: GPRegister, first: Address, second: Address) {
        TODO("Not yet implemented")
    }

    override fun rii(dst: GPRegister, first: Imm32, second: Imm32) {
        val res = first.value() shl second.value().toInt()
        asm.mov(size, Imm32.of(res), dst)
    }

    override fun ria(dst: GPRegister, first: Imm32, second: Address) {
        TODO("Not yet implemented")
    }

    override fun rai(dst: GPRegister, first: Address, second: Imm32) {
        asm.mov(size, first, dst)
        asm.sal(size, second, dst)
    }

    override fun ara(dst: Address, first: GPRegister, second: Address) {
        TODO("Not yet implemented")
    }

    override fun aii(dst: Address, first: Imm32, second: Imm32) {
        TODO("Not yet implemented")
    }

    override fun air(dst: Address, first: Imm32, second: GPRegister) {
        TODO("Not yet implemented")
    }

    override fun aia(dst: Address, first: Imm32, second: Address) {
        TODO("Not yet implemented")
    }

    override fun ari(dst: Address, first: GPRegister, second: Imm32) {
        asm.mov(size, first, dst)
        asm.sal(size, second, dst)
    }

    override fun aai(dst: Address, first: Address, second: Imm32) {
        TODO("Not yet implemented")
    }

    override fun aar(dst: Address, first: Address, second: GPRegister) {
        TODO("Not yet implemented")
    }

    override fun aaa(dst: Address, first: Address, second: Address) {
        TODO("Not yet implemented")
    }

    override fun default(dst: Operand, first: Operand, second: Operand) {
        throw RuntimeException("Internal error: '${ir.instruction.Shl.NAME}' dst=$dst, first=$first, second=$second")
    }
}