package ir.platform.x64.codegen.impl

import asm.x64.*
import ir.types.*
import asm.x64.Operand
import ir.Definitions.POINTER_SIZE
import ir.instruction.lir.MoveByIndex
import ir.platform.x64.CallConvention.temp1
import ir.platform.x64.CallConvention.temp2
import ir.platform.x64.codegen.visitors.GPOperandsVisitorBinaryOp


internal class MoveIntByIndexCodegen(val type: PrimitiveType, indexType: NonTrivialType, val asm: Assembler) : GPOperandsVisitorBinaryOp {
    private val size = type.sizeOf()
    private val indexSize = indexType.sizeOf()

    operator fun invoke(dst: Operand, source: Operand, index: Operand) = when (type) {
        is IntegerType, is PtrType -> GPOperandsVisitorBinaryOp.apply(dst, source, index, this)
        else -> throw RuntimeException("Unknown type=$type, dst=$dst, source=$source, index=$index")
    }

    override fun rrr(dst: GPRegister, first: GPRegister, second: GPRegister) {
        asm.mov(size, first, Address.from(dst, 0, second, ScaleFactor.from(size)))
    }

    override fun arr(dst: Address, first: GPRegister, second: GPRegister) {
        asm.mov(POINTER_SIZE, dst, temp1)
        asm.mov(size, first, Address.from(temp1, 0, second, ScaleFactor.from(size)))
    }

    override fun rar(dst: GPRegister, first: Address, second: GPRegister) {
        asm.mov(size, first, temp1)
        asm.mov(size, temp1, Address.from(dst, 0, second, ScaleFactor.from(size)))
    }

    override fun rir(dst: GPRegister, first: Imm, second: GPRegister) {
        if (Imm.canBeImm32(first.value())) {
            asm.mov(size, first.asImm32(), Address.from(dst, 0, second, ScaleFactor.from(size)))
        } else {
            asm.mov(size, first, temp1)
            asm.mov(size, temp1, Address.from(dst, 0, second, ScaleFactor.from(size)))
        }
    }

    override fun rra(dst: GPRegister, first: GPRegister, second: Address) {
        asm.mov(indexSize, second, temp1)
        asm.mov(size, first, Address.from(dst, 0, temp1, ScaleFactor.from(size)))
    }

    override fun rri(dst: GPRegister, first: GPRegister, second: Imm) {
        asm.mov(size, first, Address.from(dst, second.value().toInt() * size))
    }

    override fun raa(dst: GPRegister, first: Address, second: Address) {
        asm.mov(size, first, temp1)
        asm.mov(indexSize, second, temp2)
        asm.mov(size, temp1, Address.from(dst, 0, temp2, ScaleFactor.from(size)))
    }

    override fun rii(dst: GPRegister, first: Imm, second: Imm) {
        if (Imm.canBeImm32(first.value())) {
            asm.mov(size, first.asImm32(), Address.from(dst, second.value().toInt() * size))
        } else {
            asm.mov(size, first, temp1)
            asm.mov(size, temp1, Address.from(dst, second.value().toInt() * size))
        }
    }

    override fun ria(dst: GPRegister, first: Imm, second: Address) {
        asm.mov(indexSize, second, temp1)
        if (Imm.canBeImm32(first.value())) {
            asm.mov(size, first.asImm32(), Address.from(dst, 0, temp1, ScaleFactor.from(size)))
        } else {
            asm.mov(size, first, temp2)
            asm.mov(size, temp2, Address.from(dst, 0, temp1, ScaleFactor.from(size)))
        }
    }

    override fun rai(dst: GPRegister, first: Address, second: Imm) {
        asm.mov(size, first, temp1)
        asm.mov(size, temp1, Address.from(dst, second.value().toInt() * size))
    }

    override fun ara(dst: Address, first: GPRegister, second: Address) {
        asm.mov(POINTER_SIZE, dst, temp1)
        asm.mov(indexSize, second, temp2)
        asm.mov(size, first, Address.from(temp1, 0, temp2, ScaleFactor.from(size)))
    }

    override fun aii(dst: Address, first: Imm, second: Imm) {
        asm.mov(POINTER_SIZE, dst, temp1)
        if (Imm.canBeImm32(first.value())) {
            asm.mov(size, first.asImm32(), Address.from(temp1, second.value().toInt() * size))
        } else {
            asm.mov(size, first, temp2)
            asm.mov(size, temp2, Address.from(temp1, second.value().toInt() * size))
        }
    }

    override fun air(dst: Address, first: Imm, second: GPRegister) {
        asm.mov(POINTER_SIZE, dst, temp1)
        if (Imm.canBeImm32(first.value())) {
            asm.mov(size, first.asImm32(), Address.from(temp1, 0, second, ScaleFactor.from(size)))
        } else {
            asm.mov(size, first, temp2)
            asm.mov(size, temp2, Address.from(temp1, 0, second, ScaleFactor.from(size)))
        }
    }

    override fun aia(dst: Address, first: Imm, second: Address) {
        asm.mov(POINTER_SIZE, dst, temp1)
        asm.mov(indexSize, second, temp2)
        if (Imm.canBeImm32(first.value())) {
            asm.mov(size, first.asImm32(), Address.from(temp1, 0, temp2, ScaleFactor.from(size)))
        } else {
            asm.lea(POINTER_SIZE, Address.from(temp1, 0, temp2, ScaleFactor.from(size)), temp1)
            asm.mov(size, first, temp2)
            asm.mov(size, temp2, Address.from(temp1, 0))
        }
    }

    override fun ari(dst: Address, first: GPRegister, second: Imm) {
        asm.mov(POINTER_SIZE, dst, temp1)
        asm.mov(size, first, Address.from(temp1, second.value().toInt() * size))
    }

    override fun aai(dst: Address, first: Address, second: Imm) {
        asm.mov(POINTER_SIZE, dst, temp1)
        asm.mov(size, first, temp2)
        asm.mov(size, temp2, Address.from(temp1, second.value().toInt() * size))
    }

    override fun aar(dst: Address, first: Address, second: GPRegister) {
        asm.mov(POINTER_SIZE, dst, temp1)
        asm.lea(POINTER_SIZE, Address.from(temp1, 0, second, ScaleFactor.from(size)), temp1)
        asm.mov(size, first, temp2)
        asm.mov(size, temp2, Address.from(temp1, 0))
    }

    override fun aaa(dst: Address, first: Address, second: Address) {
        asm.mov(POINTER_SIZE, dst, temp1)
        asm.mov(indexSize, second, temp2)
        asm.lea(POINTER_SIZE, Address.from(temp1, 0, temp2, ScaleFactor.from(size)), temp1)
        asm.mov(size, first, temp2)
        asm.mov(size, temp2, Address.from(temp1, 0))
    }

    override fun default(dst: Operand, first: Operand, second: Operand) {
        throw RuntimeException("Internal error: '${MoveByIndex.NAME}' dst=$dst, first=$first, second=$second")
    }
}