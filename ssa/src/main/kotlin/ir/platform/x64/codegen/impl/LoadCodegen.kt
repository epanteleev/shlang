package ir.platform.x64.codegen.impl

import asm.x64.*
import ir.types.*
import ir.instruction.Load
import ir.platform.x64.CallConvention.POINTER_SIZE
import ir.platform.x64.CallConvention.temp1
import ir.platform.x64.codegen.utils.*


data class LoadCodegen(val type: PrimitiveType, val asm: Assembler): GPOperandVisitorUnaryOp,
    XmmOperandVisitorUnaryOp {
    private val size = type.size()

    operator fun invoke(value: Operand, pointer: Operand) {
        when (type) {
            is FloatingPointType           -> ApplyClosure(value, pointer, this as XmmOperandVisitorUnaryOp)
            is IntegerType, is PointerType -> ApplyClosure(value, pointer, this as GPOperandVisitorUnaryOp)
            else -> throw RuntimeException("Unknown type=$type, value=$value, pointer=$pointer")
        }
    }

    override fun rr(dst: GPRegister, src: GPRegister) {
        asm.mov(size, Address.from(src, 0), dst)
    }

    override fun ra(dst: GPRegister, src: Address) {
        when (src) {
            is AddressLiteral -> asm.mov(size, src, dst)
            else -> TODO()
        }
    }

    override fun ar(dst: Address, src: GPRegister) {
        TODO("Not yet implemented")
    }

    override fun aa(dst: Address, src: Address) {
        if (dst == src) {
            return
        }

        asm.mov(POINTER_SIZE, src, temp1)
        asm.mov(size, Address.from(temp1, 0), temp1)
        asm.mov(size, temp1, dst)
    }

    override fun ri(dst: GPRegister, src: Imm32) {
        TODO("Not yet implemented")
    }

    override fun ai(dst: Address, src: Imm32) {
        TODO("Not yet implemented")
    }

    override fun rrF(dst: XmmRegister, src: XmmRegister) {
        TODO("Not yet implemented")
    }

    override fun raF(dst: XmmRegister, src: Address) {
        when (src) {
            is AddressLiteral -> asm.movf(size, src, dst)
            else -> TODO()
        }
    }

    override fun arF(dst: Address, src: XmmRegister) {
        TODO("Not yet implemented")
    }

    override fun aaF(dst: Address, src: Address) {
        TODO("Not yet implemented")
    }

    override fun default(dst: Operand, src: Operand) {
        throw RuntimeException("Internal error: '${Load.NAME}' dst=$dst, pointer=$src")
    }
}