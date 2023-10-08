package ir.codegen.x64

import asm.x64.*
import ir.*
import ir.block.Label
import ir.Module
import ir.block.Block
import ir.codegen.x64.regalloc.LinearScan
import ir.instruction.Call
import ir.instruction.Callable
import ir.pass.ana.VerifySSA
import ir.pass.transform.CopyInsertion
import ir.pass.transform.SplitCriticalEdge
import ir.utils.OrderedLocation

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

class CodeEmitter(val data: FunctionData, val functionCounter: Int, private val objFunc: ObjFunction) {
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

    private fun emitArithmeticBinary(binary: ir.instruction.ArithmeticBinary) {
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
            ir.instruction.ArithmeticBinaryOp.Add -> {
                objFunc.add(second, first as Register)
            }
            ir.instruction.ArithmeticBinaryOp.Sub -> {
                objFunc.sub(second, first as Register)
            }
            ir.instruction.ArithmeticBinaryOp.Xor -> {
                objFunc.xor(second, first as Register)
            }
            ir.instruction.ArithmeticBinaryOp.Mul -> {
                objFunc.mul(second, first as Register)
            }
            ir.instruction.ArithmeticBinaryOp.Div -> {
                objFunc.div(second, first as Register)
            }
            else -> {
                println("Unimplemented: ${binary.op}")
            }
        }

        if (destination is Mem) {
            objFunc.mov(first, destination)
        }
    }

    private fun emitReturn(ret: ir.instruction.Return) {
        val returnType = data.prototype.type()
        if (returnType.isArithmetic() || returnType.isPointer()) {
            val value = valueToRegister.operand(ret.value())
            objFunc.mov(value, temp1(value.size))
        }

        emitEpilogue()
        objFunc.ret()
    }

    private fun emitArithmeticUnary(unary: ir.instruction.ArithmeticUnary) {
        val operand = valueToRegister.operand(unary.operand())
        val result  = valueToRegister.operand(unary)

        if (unary.op == ir.instruction.ArithmeticUnaryOp.Neg) {
            val second = if (operand is Mem) {
                objFunc.mov(operand, temp1(8))
            } else {
                operand as Register
            }
            objFunc.xor(Imm(-1, 8), second)
            objFunc.mov(second, result as Operand)

        } else if (unary.op == ir.instruction.ArithmeticUnaryOp.Not) {
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

    private fun emitCall(call: Callable, location: OrderedLocation) {
        val savedRegisters = valueToRegister.callerSaveRegisters(location)
        for (arg in savedRegisters) {
            objFunc.push(arg)
        }

        val totalStackSize = (savedRegisters.size + valueToRegister.calleeSaveRegisters.size + /** include retaddr and rbp **/ 2) * 8 +
                valueToRegister.reservedStackSize()
        if (totalStackSize % 16L != 0L) {
            objFunc.sub(Imm(8, 8), Rsp.rsp)
        }

        ArgumentEmitter(objFunc).doIt(call.arguments()
            .mapTo(arrayListOf()) {
                valueToRegister.operand(it)
            }
        )

        objFunc.call(call.prototype().name)

        if (totalStackSize % 16L != 0L) {
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

    private fun emitStore(instruction: ir.instruction.Store) {
        val pointer = valueToRegister.operand(instruction.pointer()) as Operand
        var value   = valueToRegister.operand(instruction.value())

        if (value is Mem) {
            value = objFunc.mov(value, temp2(value.size))
        }

        objFunc.mov(value, pointer)
    }

    private fun emitLoad(instruction: ir.instruction.Load) {
        val pointer = valueToRegister.operand(instruction.operand())
        val value   = valueToRegister.operand(instruction) as Operand

        val operand = if (value is Mem) {
            objFunc.mov(value, temp1(value.size))
        } else {
            value
        }

        objFunc.mov(pointer, operand)

        if (value is Mem) {
            objFunc.mov(temp1(value.size), value)
        }
    }

    private fun emitIntCompare(isMultiplyUsages: Boolean, intCompare: ir.instruction.IntCompare) {
        var first = valueToRegister.operand(intCompare.first())
        val second = valueToRegister.operand(intCompare.second())

        first = if (first is Mem) {
            objFunc.mov(first, temp1(first.size))
        } else {
            first
        }

        objFunc.cmp(first as GPRegister, second)
        if (isMultiplyUsages) {
            println("multiply usages $intCompare")
        }
    }

    private fun emitBranch(branch: ir.instruction.Branch) {
        objFunc.jump(JmpType.JMP, ".L$functionCounter.${branch.target().index}")
    }

    private fun emitBranchCond(branchCond: ir.instruction.BranchCond) {
        val cond = branchCond.condition()
        if (cond is ir.instruction.IntCompare) {
            val jmpType = when (cond.predicate().invert()) {
                ir.instruction.IntPredicate.Eq -> JmpType.JE
                ir.instruction.IntPredicate.Ne -> JmpType.JNE
                ir.instruction.IntPredicate.Ugt -> JmpType.JG
                ir.instruction.IntPredicate.Uge -> JmpType.JGE
                ir.instruction.IntPredicate.Ult -> JmpType.JL
                ir.instruction.IntPredicate.Ule -> JmpType.JLE
                ir.instruction.IntPredicate.Sgt -> JmpType.JG
                ir.instruction.IntPredicate.Sge -> JmpType.JGE
                ir.instruction.IntPredicate.Slt -> JmpType.JL
                ir.instruction.IntPredicate.Sle -> JmpType.JLE
            }

            objFunc.jump(jmpType, ".L$functionCounter.${branchCond.onFalse().index}")
        } else {
            println("unsupported $branchCond")
        }
    }

    private fun emitCopy(copy: ir.instruction.Copy) {
        val result  = valueToRegister.operand(copy)
        val operand = valueToRegister.operand(copy.origin())

        if (result is Mem && operand is Mem) {
            val temp = temp1(operand.size)
            objFunc.mov(operand, temp)
            objFunc.mov(temp, result)
        } else {
            objFunc.mov(operand, result as Operand)
        }
    }

    private fun emitBasicBlock(bb: Block, map: Map<Callable, OrderedLocation>) {
        for (instruction in bb) {
            when (instruction) {
                is ir.instruction.ArithmeticBinary -> emitArithmeticBinary(instruction)
                is ir.instruction.Store -> emitStore(instruction)
                is ir.instruction.Return -> emitReturn(instruction)
                is ir.instruction.Load -> emitLoad(instruction)
                is Call -> emitCall(instruction, map[instruction]!!)
                is ir.instruction.ArithmeticUnary -> emitArithmeticUnary(instruction)
                is ir.instruction.IntCompare -> emitIntCompare(false, instruction)
                is ir.instruction.Branch -> emitBranch(instruction)
                is ir.instruction.BranchCond -> emitBranchCond(instruction)
                is ir.instruction.VoidCall -> emitCall(instruction, map[instruction]!!)
                is ir.instruction.Copy -> emitCopy(instruction)
                is ir.instruction.Phi -> {/* skip */}
                is ir.instruction.StackAlloc -> {/* skip */}
                else                -> println("Unsupported: $instruction")
            }
        }
    }

    private fun emit() {
        val orderedLocation = hashMapOf<Callable, OrderedLocation>()
        var order = 0
        for (bb in data.blocks.linearScanOrder()) {
            for ((idx, call) in bb.instructions().withIndex()) {
                if (call is Call || call is ir.instruction.VoidCall) {
                    call as Callable
                    orderedLocation[call] = OrderedLocation(bb, order)
                }
                order += 1
            }
        }

        emitPrologue()
        for (bb in data.blocks.preorder()) {
            if (!bb.equals(Label.entry)) {
                objFunc.label(".L$functionCounter.${bb.index}")
            }

            emitBasicBlock(bb, orderedLocation)
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
            val opt = VerifySSA.run(CopyInsertion.run(SplitCriticalEdge.run(module)))
            val asm = Assembler()

            for ((idx, data) in opt.functions().withIndex()) {
                CodeEmitter(data, idx, asm.mkFunction(data.prototype.name)).emit()
            }

            return asm
        }
    }
}