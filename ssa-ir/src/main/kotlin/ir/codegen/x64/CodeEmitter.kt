package ir.codegen.x64

import asm.x64.*
import ir.*
import ir.Call
import ir.Label
import ir.Module
import ir.utils.DefUseInfo
import ir.utils.Location

private class ArgumentEmitter(val objFunc: ObjFunction) {
    private var index: Long = 0

    fun emit(value: AnyOperand) {
        val size = CallConvention.gpArgumentRegisters.size
        val old = index
        index += 1

        if (old < size) {
            objFunc.mov(value, CallConvention.gpArgumentRegisters[old.toInt()])
            return
        }

        when (value) {
            is Mem -> {
                objFunc.mov(value, CodeEmitter.temp1(value.size))
                objFunc.push(CodeEmitter.temp1(value.size))
            }
            is GPRegister -> objFunc.push(value)
            is Imm -> objFunc.push(value)
            else -> throw RuntimeException("Internal error")
        }
    }
}

class CodeEmitter(val data: FunctionData, private val objFunc: ObjFunction) {
    private val valueToRegister = LinearScan.alloc(data)

    private fun emitPrologue() {
        val stackSize = valueToRegister.reservedStackSize()
        val calleeSaveRegisters = valueToRegister.calleeSaveRegisters
        for (reg in calleeSaveRegisters) {
            objFunc.push(reg)
            if (reg == Rbp.rbp) {
                objFunc.mov(Rsp.rsp, Rbp.rbp)
            }
        }

        if (stackSize != 0L) {
            objFunc.sub(Imm(stackSize, 8), Rsp.rsp)
        }
    }

    private fun emitEpilogue() {
        val stackSize = valueToRegister.reservedStackSize()
        if (stackSize != 0L) {
            objFunc.add(Imm(stackSize, 8), Rsp.rsp)
        }

        val calleeSaveRegisters = valueToRegister.calleeSaveRegisters
        for (reg in calleeSaveRegisters) {
            objFunc.pop(reg)
        }
    }

    private fun emitArithmeticBinary(binary: ArithmeticBinary) {
        var first       = valueToRegister.operand(binary.first())
        var second      = valueToRegister.operand(binary.second())
        val destination = valueToRegister.operand(binary) as Operand

        if (first is Mem) {
            first = objFunc.mov(first, temp1(first.size))
        }

        second = if (destination is Mem) {
            objFunc.mov(second, temp2(second.size))
        } else {
            objFunc.mov(second, destination)
        }

        when (binary.op) {
            ArithmeticBinaryOp.Add -> {
                objFunc.add(first, second as Register)
            }
            ArithmeticBinaryOp.Sub -> {
                objFunc.sub(first, second as Register)
            }
            ArithmeticBinaryOp.Xor -> {
                objFunc.xor(first, second as Register)
            }
            ArithmeticBinaryOp.Mul -> {
                objFunc.mul(first, second as Register)
            }
            else -> {
                TODO()
            }
        }

        if (destination is Mem) {
            objFunc.mov(second, destination)
        }
    }

    private fun emitReturn(ret: Return) {
        val returnType = data.prototype.type()
        if (returnType.isArithmetic() || returnType.isPointer()) {
            val value = valueToRegister.operand(ret.value())
            objFunc.mov(value, temp1(value.size))
        }

        emitEpilogue()
        objFunc.ret()
    }

    private fun emitArithmeticUnary(unary: ArithmeticUnary) {
        val operand = valueToRegister.operand(unary.operand())
        val result  = valueToRegister.operand(unary)

        if (unary.op == ArithmeticUnaryOp.Neg) {
            val second = if (operand is Mem) {
                objFunc.mov(operand, temp1(8))
            } else {
                operand as Register
            }
            objFunc.xor(Imm(-1, 8), second)
            objFunc.mov(second, result as Operand)

        } else if (unary.op == ArithmeticUnaryOp.Not) {
            val second = if (result is Mem) {
                objFunc.mov(result, temp1(result.size))
            } else {
                result as Register
            }
            objFunc.xor(second, second)
            objFunc.sub(operand, second)

            if (result is Mem) {
                objFunc.mov(temp1(result.size), result)
            }

        } else {
            throw RuntimeException("Internal error")
        }
    }

    private fun emitCall(call: Callable, location: Location) {
        val arguments = valueToRegister.callerSaveRegisters(location)
        for (arg in arguments) {
            objFunc.push(arg)
        }

        val argEmitter = ArgumentEmitter(objFunc)

        for (arg in call.arguments()) {
            argEmitter.emit(valueToRegister.operand(arg))
        }

        objFunc.call(call.prototype().name)

        for (arg in arguments.reversed()) {
            objFunc.pop(arg)
        }
    }

    private fun emitStore(instruction: Store) {
        val pointer = valueToRegister.operand(instruction.pointer()) as Operand
        var value   = valueToRegister.operand(instruction.value())

        if (value is Mem) {
            value = objFunc.mov(value, temp2(value.size))
        }

        objFunc.mov(value, pointer)
    }

    private fun emitLoad(instruction: Load) {
        val pointer = valueToRegister.operand(instruction.operand())
        val value   = valueToRegister.operand(instruction) as Operand

        objFunc.mov(pointer, value)
    }

    private fun emitIntCompare(isMultiplyUsages: Boolean, intCompare: IntCompare) {
        var first = valueToRegister.operand(intCompare.first())
        val second = valueToRegister.operand(intCompare.second())

        first = if (first is Mem && second is Mem) {
            objFunc.mov(first, temp1(first.size))
        } else {
            first
        }

        objFunc.cmp(first as GPRegister, second)
        if (isMultiplyUsages) {
            println("multiply usages $intCompare")
        }
    }

    private fun emitBranch(branch: Branch) {
        objFunc.jump(JmpType.JMP, "L${branch.target().index()}")
    }

    private fun emitBranchCond(branchCond: BranchCond) {
        val cond = branchCond.condition()
        if (cond is IntCompare) {
            val jmpType = when (cond.predicate().invert()) {
                IntPredicate.Eq -> JmpType.JE
                IntPredicate.Ne -> JmpType.JNE
                IntPredicate.Ugt -> JmpType.JG
                IntPredicate.Uge -> JmpType.JGE
                IntPredicate.Ult -> JmpType.JL
                IntPredicate.Ule -> JmpType.JLE
                IntPredicate.Sgt -> JmpType.JG
                IntPredicate.Sge -> JmpType.JGE
                IntPredicate.Slt -> JmpType.JL
                IntPredicate.Sle -> JmpType.JLE
            }

            objFunc.jump(jmpType, "L${branchCond.onFalse().index()}")
        } else {
            println("unsupported $branchCond")
        }
    }

    private fun emitBasicBlock(bb: BasicBlock) {
        for ((index, instruction) in bb.withIndex()) {
            when (instruction) {
                is ArithmeticBinary -> emitArithmeticBinary(instruction)
                is Store            -> emitStore(instruction)
                is Return           -> emitReturn(instruction)
                is Load             -> emitLoad(instruction)
                is Call             -> emitCall(instruction, Location(bb, index))
                is ArithmeticUnary  -> emitArithmeticUnary(instruction)
                is IntCompare       -> emitIntCompare(false, instruction)
                is Branch           -> emitBranch(instruction)
                is BranchCond       -> emitBranchCond(instruction)
                is VoidCall         -> emitCall(instruction, Location(bb, index))
                is Phi              -> {/* skip */}
                is StackAlloc       -> {/* skip */}
                else                -> println("Unsupported: $instruction")
            }
        }
    }

    private fun emitPhi(phi: Phi) {
        val savedLabel = objFunc.currentLabel()
        val resultValue = valueToRegister.operand(phi)

        for ((bb, value) in phi.incoming().zip(phi.usedValues())) {
            objFunc.switchLabel("L${bb.index()}")

            val valueOperand = valueToRegister.operand(value)
            if (valueOperand == resultValue) {
                continue
            }
            val resultReg = if (resultValue is Mem) {
                objFunc.mov(resultValue, temp1(resultValue.size))
            } else {
                resultValue as GPRegister
            }
            objFunc.mov(valueOperand, resultReg)

            if (resultValue is Mem) {
                objFunc.mov(temp1(resultValue.size), resultValue)
            }
        }
        objFunc.switchLabel(savedLabel)
    }

    private fun emit() {
        emitPrologue()
        for (bb in data.blocks.preorder()) {
            if (!bb.equals(Label.entry)) {
                objFunc.label("L${bb.index()}")
            }
            emitBasicBlock(bb)
        }

        for (bb in data.blocks) {
            for (instruction in bb) {
                if (instruction !is Phi) {
                    continue
                }

                emitPhi(instruction)
            }
        }
    }

    companion object {
        fun temp1(size: Int): GPRegister {
            return CallConvention.temp1(size)
        }

        fun temp2(size: Int): GPRegister {
            return CallConvention.temp2(size)
        }

        fun codegen(module: Module): Assembler {
            val asm = Assembler()

            for (data in module.functions()) {
                CodeEmitter(data, asm.mkFunction(data.prototype.name)).emit()
            }
            return asm
        }
    }
}