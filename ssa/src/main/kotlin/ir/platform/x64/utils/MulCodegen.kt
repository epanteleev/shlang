package ir.platform.x64.utils

import asm.x64.*
import ir.instruction.ArithmeticBinaryOp
import ir.platform.x64.CallConvention.temp1
import ir.platform.x64.CodeEmitter
import ir.platform.x64.utils.Utils.case

object MulCodegen {
    operator fun invoke(objFunc: ObjFunction, dst: AnyOperand, first: AnyOperand, second: AnyOperand, size: Int) {
        when {
            case<GPRegister, GPRegister, GPRegister>(dst, first, second) -> {
                first as GPRegister
                dst as GPRegister
                second as GPRegister
                if (first == dst) {
                    objFunc.mul(size, second, dst)
                } else if (second == dst) {
                    objFunc.mul(size, first, dst)
                } else {
                    objFunc.mov(size, first, dst)
                    objFunc.mul(size, second, dst)
                }
            }

            case<Address, GPRegister, GPRegister>(dst, first, second) -> {
                dst as Address
                second as Register
                objFunc.mov(size, first, dst)
                objFunc.mul(size, second, dst)
            }

            case<Address, Address, GPRegister>(dst, first, second) -> {
                dst as Address
                first as Address
                second as GPRegister
                objFunc.mov(size, first, temp1)
                objFunc.mul(size, second, CodeEmitter.temp1)
                objFunc.mov(size, temp1, dst)
            }

            case<GPRegister, Imm, GPRegister>(dst, first, second) -> {
                dst as GPRegister
                first as Imm
                second as GPRegister

                if (first.value == 2L) {
                    // Todo implement mul as shl
                }

                if (dst == second) {
                    objFunc.mul(size, first, dst)
                } else {
                    objFunc.lea(size, Address.mem(null, 0, second, first.value), dst)
                }
            }

            case<GPRegister, GPRegister, Imm>(dst, first, second) -> {
                dst as GPRegister
                first as GPRegister
                second as Imm

                if (second.value == 2L) {
                    // Todo implement mul as shl
                }

                if (dst == first) {
                    objFunc.mul(size, second, dst)
                } else {
                    objFunc.lea(size, Address.mem(null, 0, first, second.value), dst)
                }
            }

            case<Address, Address, Address>(dst, first, second) -> {
                dst as Address
                first as Address
                second as Address
                objFunc.mov(size, first, CodeEmitter.temp1)
                objFunc.mul(size, second, CodeEmitter.temp1)
                objFunc.mov(size, CodeEmitter.temp1, dst)
            }

            case<GPRegister, Address, GPRegister>(dst, first, second) -> {
                dst as GPRegister
                first as Address
                second as GPRegister

                if (dst == second) {
                    objFunc.add(size, first, second)
                } else {
                    objFunc.mov(size, first, CodeEmitter.temp1)
                    objFunc.mul(size, second, CodeEmitter.temp1)
                    objFunc.mov(size, CodeEmitter.temp1, dst)
                }
            }

            case<Address, GPRegister, GPRegister>(dst, first, second) -> {
                dst as Address
                second as Register
                objFunc.mov(size, first, dst)
                objFunc.mul(size, second, dst)
            }

            else -> throw RuntimeException("Unimplemented: '${ArithmeticBinaryOp.Mul}' dst=$dst, first=$first, second=$second")
        }
    }
}