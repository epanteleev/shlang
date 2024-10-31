package intrinsic

import types.*
import asm.Operand
import asm.x64.Address
import asm.x64.Address2
import asm.x64.CondType
import asm.x64.GPRegister.*
import asm.x64.XmmRegister.*
import common.assertion
import ir.Definitions
import ir.Definitions.QWORD_SIZE
import ir.intrinsic.IntrinsicProvider
import ir.platform.MacroAssembler
import ir.platform.x64.codegen.X64MacroAssembler
import typedesc.TypeDesc


class VaInit(private val firstArgType: CType): IntrinsicProvider("va_init", listOf()) {
    override fun <Masm : MacroAssembler> implement(masm: Masm, inputs: List<Operand>) {
        assertion(inputs.size == 1) { "va_init must have 1 arguments" }

        val vaInit = inputs.first()
        assertion(vaInit is Address2) { "va_init must be Reg64" }
        vaInit as Address2

        when (masm) {
            is X64MacroAssembler -> implementX64(masm, vaInit)
            else -> error("Unsupported assembler: ${masm.platform()}")
        }
    }

    private fun implementX64(masm: X64MacroAssembler, vaInit: Address2) {
        masm.apply {
            test(Definitions.BYTE_SIZE, rax, rax)
            val currentLabel = masm.currentLabel()
            val gprBlock = masm.anonLabel()
            switchTo(currentLabel)

            jcc(CondType.JE, gprBlock)

            val isGPOperand = isGPOperand(firstArgType)
            if (isGPOperand) {
                movf(QWORD_SIZE, xmm0, Address.Companion.from(vaInit.base, vaInit.offset + 6 * QWORD_SIZE))
            }

            movf(QWORD_SIZE, xmm1, Address.Companion.from(vaInit.base, vaInit.offset + 7 * QWORD_SIZE))
            movf(QWORD_SIZE, xmm2, Address.Companion.from(vaInit.base, vaInit.offset + 8 * QWORD_SIZE))
            movf(QWORD_SIZE, xmm3, Address.Companion.from(vaInit.base, vaInit.offset + 9 * QWORD_SIZE))
            movf(QWORD_SIZE, xmm4, Address.Companion.from(vaInit.base, vaInit.offset + 10 * QWORD_SIZE))
            movf(QWORD_SIZE, xmm5, Address.Companion.from(vaInit.base, vaInit.offset + 11 * QWORD_SIZE))
            movf(QWORD_SIZE, xmm6, Address.Companion.from(vaInit.base, vaInit.offset + 12 * QWORD_SIZE))
            movf(QWORD_SIZE, xmm7, Address.Companion.from(vaInit.base, vaInit.offset + 13 * QWORD_SIZE))

            switchTo(gprBlock)
            if (!isGPOperand) {
                mov(QWORD_SIZE, rdi, Address.Companion.from(vaInit.base, vaInit.offset))
            }
            mov(QWORD_SIZE, rsi, Address.Companion.from(vaInit.base, vaInit.offset + QWORD_SIZE))
            mov(QWORD_SIZE, rdx, Address.Companion.from(vaInit.base, vaInit.offset + 2 * QWORD_SIZE))
            mov(QWORD_SIZE, rcx, Address.Companion.from(vaInit.base, vaInit.offset + 3 * QWORD_SIZE))
            mov(QWORD_SIZE, r8, Address.Companion.from(vaInit.base, vaInit.offset + 4 * QWORD_SIZE))
            mov(QWORD_SIZE, r9, Address.Companion.from(vaInit.base, vaInit.offset + 5 * QWORD_SIZE))
        }
    }

    companion object {
        fun isGPOperand(type: CType): Boolean = when (type) {
            is CHAR, is UCHAR, is SHORT, is USHORT, is INT, is UINT, is LONG, is ULONG, is CPointer -> true
            else -> false
        }

        fun isFPOperand(type: CType): Boolean = when (type) {
            is FLOAT, is DOUBLE -> true
            else -> false
        }

        val vaInit = CStructType("va_init", arrayListOf(
                FieldMember("xmm0", TypeDesc.Companion.from(DOUBLE, listOf())),
                FieldMember("xmm1", TypeDesc.Companion.from(DOUBLE, listOf())),
                FieldMember("xmm2", TypeDesc.Companion.from(DOUBLE, listOf())),
                FieldMember("xmm3", TypeDesc.Companion.from(DOUBLE, listOf())),
                FieldMember("xmm4", TypeDesc.Companion.from(DOUBLE, listOf())),
                FieldMember("xmm5", TypeDesc.Companion.from(DOUBLE, listOf())),
                FieldMember("xmm6", TypeDesc.Companion.from(DOUBLE, listOf())),
                FieldMember("xmm7", TypeDesc.Companion.from(DOUBLE, listOf())),
                FieldMember("rdi", TypeDesc.Companion.from(LONG, listOf())),
                FieldMember("rsi", TypeDesc.Companion.from(LONG, listOf())),
                FieldMember("rdx", TypeDesc.Companion.from(LONG, listOf())),
                FieldMember("rcx", TypeDesc.Companion.from(LONG, listOf())),
                FieldMember("r8", TypeDesc.Companion.from(LONG, listOf())),
                FieldMember("r9", TypeDesc.Companion.from(LONG, listOf())),
            )
        )
    }
}