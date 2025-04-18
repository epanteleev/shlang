package ir.platform.x64.codegen.visitors

import asm.x64.Address
import asm.x64.GPRegister
import asm.x64.Imm
import asm.x64.Operand

interface GPOperandsVisitorBinaryOp {
    fun rrr(dst: GPRegister, first: GPRegister, second: GPRegister)
    fun arr(dst: Address, first: GPRegister, second: GPRegister)
    fun rar(dst: GPRegister, first: Address, second: GPRegister)
    fun rir(dst: GPRegister, first: Imm, second: GPRegister)
    fun rra(dst: GPRegister, first: GPRegister, second: Address)
    fun rri(dst: GPRegister, first: GPRegister, second: Imm)
    fun raa(dst: GPRegister, first: Address, second: Address)
    fun rii(dst: GPRegister, first: Imm, second: Imm)
    fun ria(dst: GPRegister, first: Imm, second: Address)
    fun rai(dst: GPRegister, first: Address, second: Imm)
    fun ara(dst: Address, first: GPRegister, second: Address)
    fun aii(dst: Address, first: Imm, second: Imm)
    fun air(dst: Address, first: Imm, second: GPRegister)
    fun aia(dst: Address, first: Imm, second: Address)
    fun ari(dst: Address, first: GPRegister, second: Imm)
    fun aai(dst: Address, first: Address, second: Imm)
    fun aar(dst: Address, first: Address, second: GPRegister)
    fun aaa(dst: Address, first: Address, second: Address)
    fun default(dst: Operand, first: Operand, second: Operand)

    companion object {
        fun apply(dst: Operand, first: Operand, second: Operand, closure: GPOperandsVisitorBinaryOp) {
            when (dst) {
                is GPRegister -> {
                    when (first) {
                        is GPRegister -> {
                            when (second) {
                                is GPRegister -> closure.rrr(dst, first, second)
                                is Address    -> closure.rra(dst, first, second)
                                is Imm        -> closure.rri(dst, first, second)
                                else -> closure.default(dst, first, second)
                            }
                        }
                        is Address -> {
                            when (second) {
                                is GPRegister -> closure.rar(dst, first, second)
                                is Address    -> closure.raa(dst, first, second)
                                is Imm        -> closure.rai(dst, first, second)
                                else -> closure.default(dst, first, second)
                            }
                        }
                        is Imm -> {
                            when (second) {
                                is GPRegister -> closure.rir(dst, first, second)
                                is Address    -> closure.ria(dst, first, second)
                                is Imm        -> closure.rii(dst, first, second)
                                else -> closure.default(dst, first, second)
                            }
                        }
                        else -> closure.default(dst, first, second)
                    }
                }
                is Address -> {
                    when (first) {
                        is GPRegister -> {
                            when (second) {
                                is GPRegister -> closure.arr(dst, first, second)
                                is Address    -> closure.ara(dst, first, second)
                                is Imm        -> closure.ari(dst, first, second)
                                else -> closure.default(dst, first, second)
                            }
                        }
                        is Address -> {
                            when (second) {
                                is GPRegister -> closure.aar(dst, first, second)
                                is Address    -> closure.aaa(dst, first, second)
                                is Imm        -> closure.aai(dst, first, second)
                                else -> closure.default(dst, first, second)
                            }
                        }
                        is Imm -> {
                            when (second) {
                                is GPRegister -> closure.air(dst, first, second)
                                is Address    -> closure.aia(dst, first, second)
                                is Imm        -> closure.aii(dst, first, second)
                                else -> closure.default(dst, first, second)
                            }
                        }
                        else -> closure.default(dst, first, second)
                    }
                }
                else -> closure.default(dst, first, second)
            }
        }
    }
}