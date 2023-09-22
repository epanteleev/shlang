package ir.codegen.x64

import asm.x64.*
import ir.*
import ir.Call
import ir.Label
import ir.Module
import ir.utils.Location

private class ArgumentEmitter(val objFunc: ObjFunction) {
    private interface Place
    private val Memory = object : Place {}
    private data class RealRegister(val registerIndex: Int): Place

    private fun emit(index: Int): Place {
        return if (index < registers.size) {
            RealRegister(index)
        } else {
            Memory
        }
    }

    fun doIt(arguments: List<AnyOperand>) {
        val places = arguments.indices.mapTo(arrayListOf()) { emit(it) }

        for ((pos, value) in places.zip(arguments).asReversed()) {
            if (pos is RealRegister) {
                objFunc.mov(value, registers[pos.registerIndex].invoke(value.size))
                continue
            }

            when (value) {
                is Mem -> {
                    objFunc.mov(value, CodeEmitter.temp1(value.size))
                    objFunc.push(CodeEmitter.temp1(8))
                }
                is GPRegister -> objFunc.push(value)
                is Imm -> objFunc.push(value)
                else -> throw RuntimeException("Internal error: value=$value")
            }
        }
    }

    companion object {
        private val registers = CallConvention.gpArgumentRegisters
    }
}

class CodeEmitter(val data: FunctionData, private val objFunc: ObjFunction) {
    private val valueToRegister = LinearScan.alloc(data)

    private fun emitPrologue() {
        val stackSize = valueToRegister.reservedStackSize()
        val calleeSaveRegisters = valueToRegister.calleeSaveRegisters

        objFunc.push(Rbp.rbp)
        objFunc.mov(Rsp.rsp, Rbp.rbp)

        if (stackSize != 0L) {
            objFunc.sub(Imm(stackSize, 8), Rsp.rsp)
        }
        for (reg in calleeSaveRegisters) {
            objFunc.push(reg)
        }
    }

    private fun emitEpilogue() {
        val calleeSaveRegisters = valueToRegister.calleeSaveRegisters
        for (reg in calleeSaveRegisters.reversed()) {
            objFunc.pop(reg)
        }

        objFunc.leave()
    }

    private fun emitArithmeticBinary(binary: ArithmeticBinary) {
        var first       = valueToRegister.operand(binary.first())
        var second      = valueToRegister.operand(binary.second())
        val destination = valueToRegister.operand(binary) as Operand

        if (first is Mem) {
            first = objFunc.mov(first, temp1(first.size))
        }

        first = if (destination is Mem) {
            objFunc.mov(first, temp2(second.size))
        } else {
            objFunc.mov(first, destination)
        }

        when (binary.op) {
            ArithmeticBinaryOp.Add -> {
                objFunc.add(second, first as Register)
            }
            ArithmeticBinaryOp.Sub -> {
                objFunc.sub(second, first as Register)
            }
            ArithmeticBinaryOp.Xor -> {
                objFunc.xor(second, first as Register)
            }
            ArithmeticBinaryOp.Mul -> {
                objFunc.mul(second, first as Register)
            }
            else -> {
                TODO()
            }
        }

        if (destination is Mem) {
            objFunc.mov(first, destination)
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
        val savedRegisters = valueToRegister.callerSaveRegisters(location)
        for (arg in savedRegisters) {
            objFunc.push(arg)
        }

        val totalStackSize = savedRegisters.size * 8 + valueToRegister.reservedStackSize() + Rsp.rsp.size
        if (totalStackSize % 16L == 0L) {
            objFunc.sub(Imm(8, 8), Rsp.rsp)
        }

        ArgumentEmitter(objFunc).doIt(call.arguments().mapTo(arrayListOf()) { valueToRegister.operand(it) })

        objFunc.call(call.prototype().name)

        if (totalStackSize % 16L == 0L) {
            objFunc.add(Imm(8, 8), Rsp.rsp)
        }
        for (arg in savedRegisters.reversed()) {
            objFunc.pop(arg)
        }

        val retType = call.type()
        if (retType == Type.Void) {
            return
        }

        if (retType.isArithmetic() || retType.isPointer() || retType == Type.U1) {
            objFunc.mov(Rax.rax(call.type().size()), valueToRegister.operand(call) as Operand)
        } else if (retType.isFloat()) {
            TODO()
        } else {
            throw RuntimeException("unknown value type=$retType")
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