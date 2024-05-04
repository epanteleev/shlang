package ir.platform.x64.codegen.impl

import asm.x64.*
import ir.types.IntegerType
import ir.types.SignedIntType
import ir.types.FloatingPointType
import ir.instruction.Int2Float
import ir.platform.x64.CallConvention.temp1
import ir.platform.x64.CallConvention.xmmTemp1
import ir.platform.x64.codegen.utils.ApplyClosure
import ir.platform.x64.codegen.utils.GPOperandToXmmVisitor
import ir.types.Type


class Int2FloatCodegen(val toType: FloatingPointType, val fromType: IntegerType, val asm: Assembler) : GPOperandToXmmVisitor {
    private val toSize   = toType.size()
    private val fromSize = fromType.size()

    operator fun invoke(dst: Operand, src: Operand) {
        ApplyClosure(dst, src, this)
    }

    override fun rx(dst: XmmRegister, src: GPRegister) {
        val isConverted = convertOnDemand(src)
        if (isConverted) {
            asm.cvtint2fp(toSize, TEMP_SIZE, src, dst)
        } else {
            asm.cvtint2fp(toSize, fromSize, src, dst)
        }
    }

    override fun ax(dst: XmmRegister, src: Address) {
        val temp = convertOnDemand(src)
        if (temp != null) {
            asm.cvtint2fp(toSize, TEMP_SIZE, temp, dst)
        } else {
            asm.cvtint2fp(toSize, fromSize, src, dst)
        }
    }

    override fun ar(dst: Address, src: GPRegister) {
        val isConverted = convertOnDemand(src)
        if (isConverted) {
            asm.cvtint2fp(toSize, TEMP_SIZE, src, xmmTemp1)
            asm.movf(toSize, xmmTemp1, dst)
        } else {
            asm.cvtint2fp(toSize, fromSize, src, xmmTemp1)
            asm.movf(toSize, xmmTemp1, dst)
        }
    }

    override fun aa(dst: Address, src: Address) {
        val temp = convertOnDemand(src)
        if (temp != null) {
            asm.cvtint2fp(toSize, TEMP_SIZE, temp, xmmTemp1)
            asm.movf(toSize, xmmTemp1, dst)
        } else {
            asm.cvtint2fp(toSize, fromSize, src, xmmTemp1)
            asm.movf(toSize, xmmTemp1, dst)
        }
    }

    override fun default(dst: Operand, src: Operand) {
        throw RuntimeException("Internal error: '${Int2Float.NAME}' dst=$dst, src=$src")
    }

    private fun convertOnDemand(src: GPRegister): Boolean {
        if (fromType !is SignedIntType) {
            return false
        }

        if (fromSize == Type.I32.size() || fromSize == Type.I64.size()) {
           return false
        }

        asm.movsext(fromSize, TEMP_SIZE, src, src)
        return true
    }

    private fun convertOnDemand(src: Address): GPRegister? {
        if (fromType !is SignedIntType) {
            return null
        }

        if (fromSize == Type.I32.size() || fromSize == Type.I64.size()) {
           return null
        }

        asm.movsext(fromSize, TEMP_SIZE, src, temp1)
        return temp1
    }

    companion object {
        private val TEMP_SIZE = Type.I32.size()
    }
}