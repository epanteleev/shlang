package ir.platform.x64.auxiliary

import asm.x64.Imm
import ir.global.AnyAggregateGlobalConstant
import ir.global.*
import ir.types.*
import ir.value.*
import ir.module.Module
import ir.instruction.*
import ir.value.constant.*
import ir.instruction.lir.*
import ir.module.block.Block
import ir.module.FunctionData
import ir.instruction.matching.*
import ir.instruction.utils.IRInstructionVisitor
import ir.module.ExternFunction
import ir.module.FunctionPrototype
import ir.pass.analysis.traverse.BfsOrderOrderFabric
import ir.platform.x64.CallConvention


internal class Lowering private constructor(private val cfg: FunctionData, private val module: Module, private val idx: Int): IRInstructionVisitor<Instruction?>() {
    private var bb: Block = cfg.begin()
    private var constantIndex = 0

    private fun constName(): String = "${PREFIX}.$idx.${constantIndex++}"

    private fun pass() {
        for (bb in cfg.analysis(BfsOrderOrderFabric)) {
            this.bb = bb
            bb.transform { it.accept(this) }
        }
    }

    private fun extern(): ValueMatcher = { it is ExternValue || it is ExternFunction }

    private fun gAggregate(): ValueMatcher = { it is AnyAggregateGlobalConstant || it is FunctionPrototype }

    private fun function(): ValueMatcher = { it is FunctionPrototype }

    private fun u64imm32(): ValueMatcher = { it is U64Value && !Imm.canBeImm32(it.u64.toLong()) }
    private fun i64imm32(): ValueMatcher = { it is I64Value && !Imm.canBeImm32(it.i64) }

    override fun visit(alloc: Alloc): Instruction {
        // Before:
        //  %res = alloc %type
        //
        // After:
        //  %res = gen %type

        return bb.replace(alloc, Generate.gen(alloc.allocatedType))
    }

    override fun visit(generate: Generate): Instruction = generate

    override fun visit(lea: Lea): Instruction = lea

    private fun lowerArithmeticOperands(binary: ArithmeticBinary) {
        val lhs = binary.lhs()
        if (lhs.isa(u64imm32())) {
            val operand = lhs.asValue<U64Value>()
            val constant = module.addConstant(U64ConstantValue(constName(), operand.u64))
            binary.lhs(constant)

        } else if (lhs.isa(i64imm32())) {
            val operand = lhs.asValue<I64Value>()
            val constant = module.addConstant(I64ConstantValue(constName(), operand.i64))
            binary.lhs(constant)

        } else if (lhs.isa(f32v())) {
            val operand = lhs.asValue<F32Value>()
            val constant = module.addConstant(F32ConstantValue(constName(), operand.f32))
            binary.lhs(constant)

        } else if (lhs.isa(f64v())) {
            val operand = lhs.asValue<F64Value>()
            val constant = module.addConstant(F64ConstantValue(constName(), operand.f64))
            binary.lhs(constant)
        }

        val rhs = binary.rhs()
        if (rhs.isa(u64imm32())) {
            val operand = rhs.asValue<U64Value>()
            val constant = module.addConstant(U64ConstantValue(constName(), operand.u64))
            binary.rhs(constant)

        } else if (rhs.isa(i64imm32())) {
            val operand = rhs.asValue<I64Value>()
            val constant = module.addConstant(I64ConstantValue(constName(), operand.i64))
            binary.rhs(constant)

        } else if (rhs.isa(f32v())) {
            val operand = rhs.asValue<F32Value>()
            val constant = module.addConstant(F32ConstantValue(constName(), operand.f32))
            binary.rhs(constant)

        } else if (rhs.isa(f64v())) {
            val operand = rhs.asValue<F64Value>()
            val constant = module.addConstant(F64ConstantValue(constName(), operand.f64))
            binary.rhs(constant)
        }
    }

    private fun lowerUnaryOperand(unary: Unary): Boolean {
        val operand = unary.operand()
        if (operand.isa(f32v())) {
            val value = operand.asValue<F32Value>()
            val constant = module.addConstant(F32ConstantValue(constName(), value.f32))
            unary.operand(constant)
            return true

        } else if (operand.isa(f64v())) {
            val value = operand.asValue<F64Value>()
            val constant = module.addConstant(F64ConstantValue(constName(), value.f64))
            unary.operand(constant)
            return true
        }
        return false
    }

    override fun visit(add: Add): Instruction {
        lowerArithmeticOperands(add)
        return add
    }

    override fun visit(and: And): Instruction {
        lowerArithmeticOperands(and)
        return and
    }

    override fun visit(sub: Sub): Instruction {
        lowerArithmeticOperands(sub)
        return sub
    }

    override fun visit(mul: Mul): Instruction {
        lowerArithmeticOperands(mul)
        return mul
    }

    override fun visit(or: Or): Instruction {
        lowerArithmeticOperands(or)
        return or
    }

    override fun visit(xor: Xor): Instruction {
        lowerArithmeticOperands(xor)
        return xor
    }

    override fun visit(fadd: Fxor): Instruction {
        return fadd
    }

    override fun visit(shl: Shl): Instruction {
        val operand = shl.rhs()
        if (operand.isa(constant().not())) {
            // Before:
            //  %res = shl %a, %b
            //
            // After:
            //  %copy = copy %b <-- rcx
            //  %res = shl %a, %copy

            val copy = bb.putBefore(shl, Copy.copy(shl.rhs()))
            shl.rhs(copy)
            return shl
        }
        return shl
    }

    override fun visit(shr: Shr): Instruction {
        val operand = shr.rhs()
        if (operand.isa(constant().not())) {
            // Before:
            //  %res = shr %a, %b
            //
            // After:
            //  %copy = copy %b <-- rcx
            //  %res = shr %a, %copy

            val copy = bb.putBefore(shr, Copy.copy(shr.rhs()))
            shr.rhs(copy)
            return shr
        }
        return shr
    }

    override fun visit(div: Div): Instruction {
        lowerArithmeticOperands(div)
        return div
    }

    override fun visit(neg: Neg): Instruction {
        lowerUnaryOperand(neg)
        val type = neg.type()
        if (type.isa(f32())) {
            val constant = module.addConstant(F32ConstantValue(constName(), F32_SUBZERO))
            return bb.replace(neg, Fxor.xor(neg.operand(), constant))

        } else if (type.isa(f64())) {
            val constant = module.addConstant(F64ConstantValue(constName(), F64_SUBZERO))
            return bb.replace(neg, Fxor.xor(neg.operand(), constant))
        }

        return neg
    }

    override fun visit(not: Not): Instruction {
        return not
    }

    override fun visit(branch: Branch): Instruction {
        return branch
    }

    override fun visit(branchCond: BranchCond): Instruction {
        return branchCond
    }

    override fun visit(call: Call): Instruction {
        return call
    }

    override fun visit(tupleCall: TupleCall): Instruction {
        return tupleCall
    }

    override fun visit(flag2Int: Flag2Int): Instruction {
        return flag2Int
    }

    override fun visit(bitcast: Bitcast): Instruction {
        return bitcast
    }

    override fun visit(itofp: Int2Float): Instruction {
        return itofp
    }

    override fun visit(utofp: Unsigned2Float): Instruction {
        return utofp
    }

    override fun visit(zext: ZeroExtend): Instruction {
        return zext
    }

    override fun visit(sext: SignExtend): Instruction {
        return sext
    }

    override fun visit(trunc: Truncate): Instruction {
        return trunc
    }

    override fun visit(fptruncate: FpTruncate): Instruction {
        lowerUnaryOperand(fptruncate)
        return fptruncate
    }

    override fun visit(fpext: FpExtend): Instruction {
        lowerUnaryOperand(fpext)
        return fpext
    }

    override fun visit(fptosi: Float2Int): Instruction {
        lowerUnaryOperand(fptosi)
        return fptosi
    }

    override fun visit(copy: Copy): Instruction {
        val operand = copy.operand()
        if (lowerUnaryOperand(copy)) {
            return copy
        } else if (operand.isa(generate())) {
            // Before:
            //  %res = copy %gen
            //
            // After:
            //  %lea = lea %gen
            //  %res = copy %lea

            return bb.replace(copy, Lea.lea(copy.operand().asValue()))

        } else if (operand.isa(extern())) {
            // Before:
            //  %res = copy @extern
            //
            // After:
            //  %lea = load PtrType, @extern
            //  %res = copy %lea


            return bb.replace(copy, Load.load(PtrType, copy.operand()))

        } else if (operand.isa(gAggregate())) {
            // Before:
            //  %res = copy %gAggregate
            //
            // After:
            //  %lea = lea %gAggregate
            //  %res = copy %lea

            return bb.replace(copy, Lea.lea(copy.operand()))

        }

        return copy
    }

    override fun visit(move: Move): Instruction {
        return move
    }

    override fun visit(move: MoveByIndex): Instruction {
        return move
    }

    override fun visit(downStackFrame: DownStackFrame): Instruction {
        return downStackFrame
    }

    private fun replaceGep(gep: GetElementPtr): Instruction {
        val ty = gep.accessType()
        if (ty.isa(primitive())) {
            // Before:
            //  %res = gep %gen, %idx
            //
            // After:
            //  %lea = leastv %gen, %idx

            val baseType = gep.basicType.asType<PrimitiveType>()
            return bb.replace(gep, LeaStack.lea(gep.source(), baseType, gep.index()))

        } else if (ty.isa(aggregate())) {
            // Before:
            //  %res = gep %gen, %idx
            //
            // After:
            //  %lea = mul %idx, %gen.size
            //  %res = least %gen, %lea

            val baseType = gep.accessType().asType<AggregateType>()
            when (val index = gep.index()) {
                is IntegerConstant -> {
                    // Before:
                    //  %res = gfp %gen, %idx
                    //
                    // After:
                    //  %lea = leastv %gen, %idx

                    val idx = U64Value.of(baseType.sizeOf() * index.toInt())
                    return bb.replace(gep, LeaStack.lea(gep.source(), U8Type, idx))
                }
                is LocalValue -> {
                    val mul = Mul.mul(index, IntegerConstant.of(index.asType(), baseType.sizeOf()))
                    val offset = bb.putBefore(gep, mul)
                    return bb.replace(gep, LeaStack.lea(gep.source(), I8Type, offset))
                }
                else -> throw IllegalStateException("Unsupported index type for gep: $index")
            }

        } else {
            throw IllegalStateException("Unsupported type for gep: $ty")
        }
    }

    override fun visit(gep: GetElementPtr): Instruction {
        val ptr = gep.source()
        if (ptr.isa(gValue(anytype()))) {
            // Before:
            //  %res = gep @global, %idx
            //
            // After:
            //  %lea = lea @global
            //  %res = gep %lea, %idx

            val lea = bb.putBefore(gep, Lea.lea(gep.source().asValue()))
            gep.source(lea)
            return lea

        } else if (ptr.isa(generate())) {
            return replaceGep(gep)

        } else if (ptr.isa(extern())) {
            // Before:
            //  %res = gep @extern, %idx
            //
            // After:
            //  %lea = load PtrType, @extern
            //  %res = gep %lea, %idx

            val lea = bb.putBefore(gep, Load.load(PtrType, gep.source()))
            gep.source(lea)
            return lea

        } else if (ptr.isa(gAggregate())) {
            // Before:
            //  %res = gep %gAggregate, %idx
            //
            // After:
            //  %lea = lea %gAggregate
            //  %res = gep %lea, %idx

            val lea = bb.putBefore(gep, Lea.lea(gep.source()))
            gep.source(lea)
            return lea
        }

        val index = gep.index()
        if (index.isa(extern())) {
            // Before:
            //  %res = gep %gen, @extern
            //
            // After:
            //  %lea = load PtrType, @extern
            //  %res = gep %gen, %lea

            val lea = bb.putBefore(gep, Load.load(PtrType, gep.index()))
            gep.index(lea)
            return lea
        }

        return gep
    }

    override fun visit(gfp: GetFieldPtr): Instruction {
        val ptr = gfp.source()
        if (ptr.isa(gValue(anytype()))) {
            // Before:
            //  %res = gfp @global, %idx
            //
            // After:
            //  %lea = lea @global
            //  %res = gfp %lea, %idx

            val lea = bb.putBefore(gfp, Lea.lea(gfp.source().asValue()))
            gfp.source(lea)
            return lea

        } else if (ptr.isa(generate())) {
            val basicType = gfp.basicType.asType<AggregateType>()
            val index = U64Value.of(basicType.offset(gfp.index().toInt()))
            return bb.replace(gfp, LeaStack.lea(gfp.source(), U8Type, index))

        } else if (ptr.isa(extern())) {
            // Before:
            //  %res = gfp @extern, %idx
            //
            // After:
            //  %lea = load PtrType, @extern
            //  %res = gep %lea, %idx

            val lea = bb.putBefore(gfp, Load.load(PtrType, gfp.source()))
            gfp.source(lea)
            return lea

        } else if (ptr.isa(gAggregate())) {
            // Before:
            //  %res = gfp %gAggregate, %idx
            //
            // After:
            //  %lea = lea %gAggregate
            //  %res = gfp %lea, %idx

            val lea = bb.putBefore(gfp, Lea.lea(gfp.source()))
            gfp.source(lea)
            return lea
        }

        return gfp
    }

    override fun visit(icmp: IntCompare): Instruction {
        val type = icmp.operandsType
        val lhs = icmp.lhs()
        if (lhs.isa(extern())) {
            val lea = bb.putBefore(icmp, Load.load(PtrType, lhs))
            icmp.lhs(lea)

        } else if (lhs.isa(gAggregate())) {
            val lea = bb.putBefore(icmp, Lea.lea(lhs))
            icmp.lhs(lea)

        } else if (lhs.isa(gValue(anytype()))) {
            val lea = bb.putBefore(icmp, Lea.lea(lhs))
            icmp.lhs(lea)

        } else if (type.isa(ptr()) && lhs.isa(generate())) {
            // Before:
            //  %res = icmp %pred, %gen
            //
            // After:
            //  %lea = lea %gen
            //  %res = icmp %pred, %lea

            val lea = bb.putBefore(icmp, Lea.lea(lhs))
            icmp.lhs(lea)
        }

        val rhs = icmp.rhs()
        if (rhs.isa(extern())) {
            val lea = bb.putBefore(icmp, Load.load(PtrType, rhs))
            icmp.rhs(lea)

        } else if (rhs.isa(gAggregate())) {
            val lea = bb.putBefore(icmp, Lea.lea(rhs))
            icmp.rhs(lea)

        } else if (rhs.isa(gValue(anytype()))) {
            val lea = bb.putBefore(icmp, Lea.lea(rhs))
            icmp.rhs(lea)

        } else if (type.isa(ptr()) && rhs.isa(generate())) {
            // Before:
            //  %res = icmp %pred, %gen
            //
            // After:
            //  %lea = lea %gen
            //  %res = icmp %pred, %lea

            val lea = bb.putBefore(icmp, Lea.lea(rhs))
            icmp.rhs(lea)
        }

        return icmp
    }

    override fun visit(fcmp: FloatCompare): Instruction {
        val lhs = fcmp.lhs()
        if (lhs.isa(f32v())) {
            val operand = lhs.asValue<F32Value>()
            val constant = module.addConstant(F32ConstantValue(constName(), operand.f32))
            fcmp.lhs(constant)

        } else if (lhs.isa(f64v())) {
            val operand = lhs.asValue<F64Value>()
            val constant = module.addConstant(F64ConstantValue(constName(), operand.f64))
            fcmp.lhs(constant)
        }

        val rhs = fcmp.rhs()
        if (rhs.isa(f32v())) {
            val operand = rhs.asValue<F32Value>()
            val constant = module.addConstant(F32ConstantValue(constName(), operand.f32))
            fcmp.rhs(constant)

        } else if (rhs.isa(f64v())) {
            val operand = rhs.asValue<F64Value>()
            val constant = module.addConstant(F64ConstantValue(constName(), operand.f64))
            fcmp.rhs(constant)
        }

        return fcmp
    }

    private fun getGfpIndex(gfp: GetFieldPtr, type: NonTrivialType): Value {
        val index = gfp.index().toInt()
        return U64Value.of(gfp.basicType.offset(index).toLong() / type.sizeOf())
    }

    override fun visit(load: Load): Instruction {
        load.match(load(generate(primitive()))) {
            val gen = load.operand().asValue<Generate>()
            if (gen.type() == load.type()) {
                // Before:
                //  %res = load %gen
                //
                // After:
                //  %lea = copy %gen

                return bb.replace(load, Copy.copy(load.operand()))
            } else {
                // Before:
                //  %res = load %gen
                //
                // After:
                //  %lea = lea %gen
                //  %res = load %lea

                val lea = bb.putBefore(load, Lea.lea(load.operand()))
                load.operand(lea)
                return lea
            }
        }

        load.match(load(extern())) {
            // Before:
            //  %res = load @extern
            //
            // After:
            //  %lea = load PtrType, @extern
            //  %res = load %lea

            val lea = bb.putBefore(load, Load.load(PtrType, load.operand()))
            load.operand(lea)
            return lea
        }

        load.match(load(gep(stackAlloc(), any()))) {
            val pointer = load.operand().asValue<GetElementPtr>()
            val loadFromStack = bb.replace(load, LoadFromStack.load(pointer.source(), load.type(), pointer.index()))
            killOnDemand(pointer)
            return loadFromStack
        }

        load.match(load(gfp(stackAlloc()))) {
            val pointer = load.operand().asValue<GetFieldPtr>()
            val index = getGfpIndex(pointer, load.type())
            val loadFromStack = bb.replace(load, LoadFromStack.load(pointer.source(), load.type(), index))
            killOnDemand(pointer)
            return loadFromStack
        }

        load.match(load(gep(stackAlloc().not(), any()))) {
            val pointer = load.operand().asValue<GetElementPtr>()
            val indexedLoad = bb.replace(load, IndexedLoad.load(pointer.source(), load.type(), pointer.index()))
            killOnDemand(pointer)
            return indexedLoad
        }

        load.match(load(gfp(stackAlloc().not()))) {
            val pointer = load.operand().asValue<GetFieldPtr>()
            val index = getGfpIndex(pointer, load.type())
            val indexedLoad = bb.replace(load, IndexedLoad.load(pointer.source(), load.type(), index))
            killOnDemand(pointer)
            return indexedLoad
        }

        return load
    }

    override fun visit(phi: Phi): Instruction {
        return phi
    }

    override fun visit(phi: UncompletedPhi): Instruction? {
        throw UnsupportedOperationException("UncompletedPhi is not supported in lowering")
    }

    private fun lowerReturnArg(returnValue: ReturnValue, index: Int) {
        val retVal = returnValue.returnValue(index)
        if (retVal.isa(f32v())) {
            val operand = retVal.asValue<F32Value>()
            val constant = module.addConstant(F32ConstantValue(constName(), operand.f32))
            returnValue.returnValue(index, constant)

        } else if (retVal.isa(f64v())) {
            val operand = retVal.asValue<F64Value>()
            val constant = module.addConstant(F64ConstantValue(constName(), operand.f64))
            returnValue.returnValue(index, constant)
        }
    }

    override fun visit(returnValue: ReturnValue): Instruction {
        lowerReturnArg(returnValue, 0)
        if (returnValue.operands().size == 2) {
            lowerReturnArg(returnValue, 1)
        }

        returnValue.match(ret(gValue(anytype()))) {
            // Before:
            //  ret @global
            //
            // After:
            //  %lea = lea @global
            //  ret %lea

            val toValue = returnValue.returnValue(0)
            val lea = bb.putBefore(returnValue, Lea.lea(toValue))
            returnValue.returnValue(0, lea)
            return lea
        }

        returnValue.match(ret(extern())) {
            // Before:
            //  ret @extern
            //
            // After:
            //  %lea = load PtrType, @extern
            //  ret %lea

            val toValue = returnValue.returnValue(0)
            val lea = bb.putBefore(returnValue, Load.load(PtrType, toValue))
            returnValue.returnValue(0, lea)
            return lea
        }

        returnValue.match(ret(gAggregate())) {
            // Before:
            //  ret %gAggregate
            //
            // After:
            //  %lea = lea %gAggregate
            //  ret %lea

            val toValue = returnValue.returnValue(0)
            val lea = bb.putBefore(returnValue, Lea.lea(toValue))
            returnValue.returnValue(0, lea)
            return lea
        }

        return returnValue
    }

    override fun visit(returnVoid: ReturnVoid): Instruction {
        return returnVoid
    }

    override fun visit(indirectionCall: IndirectionCall): Instruction {
        indirectionCall.match(iCall(extern())) {
            // Before:
            //  %res = indirectionCall @global
            //
            // After:
            //  %lea = load @global
            //  %res = indirectionCall %lea

            val use = indirectionCall.pointer()
            val lea = bb.putBefore(indirectionCall, Load.load(PtrType, use))
            indirectionCall.pointer(lea)
            return lea
        }

        indirectionCall.match(iCall(function())) {
            // Before:
            //  %res = indirectionCall %gAggregate
            //
            // After:
            //  %lea = lea %gAggregate
            //  %res = indirectionCall %lea

            val use = indirectionCall.pointer()
            val lea = bb.putBefore(indirectionCall, Lea.lea(use))
            indirectionCall.pointer(lea)
            return lea
        }

        return indirectionCall
    }

    override fun visit(indirectionVoidCall: IndirectionVoidCall): Instruction {
        indirectionVoidCall.match(ivCall(extern())) {
            // Before:
            //  %res = indirectionCall @global
            //
            // After:
            //  %lea = load @global
            //  %res = indirectionCall %lea

            val use = indirectionVoidCall.pointer()
            val lea = bb.putBefore(indirectionVoidCall, Load.load(PtrType, use))
            indirectionVoidCall.pointer(lea)
            return lea
        }

        indirectionVoidCall.match(ivCall(function())) {
            // Before:
            //  %res = indirectionCall %gAggregate
            //
            // After:
            //  %lea = lea %gAggregate
            //  %res = indirectionCall %lea

            val use = indirectionVoidCall.pointer()
            val lea = bb.putBefore(indirectionVoidCall, Lea.lea(use))
            indirectionVoidCall.pointer(lea)
            return lea
        }

        return indirectionVoidCall
    }

    override fun visit(select: Select): Instruction {
        select.match(select(any(), value(i8()), value(i8()))) {
            // Before:
            //  %cond = icmp <predicate> i8 %a, %b
            //  %res = select i1 %cond, i8 %onTrue, i8 %onFalse
            //
            // After:
            //  %extOnTrue  = sext %onTrue to i16
            //  %extOnFalse = sext %onFalse to i16
            //  %cond = icmp <predicate> i8 %a, %b
            //  %newSelect = select i1 %cond, i16 %extOnTrue, i16 %extOnFalse
            //  %res = trunc %newSelect to i8

            val insertPos = when(val selectCond = select.condition()) {
                is CompareInstruction -> selectCond
                else                  -> select
            }

            val extOnTrue  = bb.putBefore(insertPos, SignExtend.sext(select.onTrue(), I16Type))
            val extOnFalse = bb.putBefore(insertPos, SignExtend.sext(select.onFalse(), I16Type))
            val newSelect  = bb.putBefore(select, Select.select(select.condition(), I16Type, extOnTrue, extOnFalse))
            return bb.replace(select, Truncate.trunc(newSelect, I8Type))
        }

        select.match(select(any(), value(u8()), value(u8()))) {
            // Before:
            //  %cond = icmp <predicate> u8 %a, %b
            //  %res = select i1 %cond, u8 %onTrue, u8 %onFalse
            //
            // After:
            //  %extOnTrue  = zext %onTrue to u16
            //  %extOnFalse = zext %onFalse to u16
            //  %cond = icmp <predicate> u8 %a, %b
            //  %newSelect = select i1 %cond, u16 %extOnTrue, u16 %extOnFalse
            //  %res = trunc %newSelect to u8

            val insertPos = when(val selectCond = select.condition()) {
                is CompareInstruction -> selectCond
                else                  -> select
            }

            val extOnTrue  = bb.putBefore(insertPos, ZeroExtend.zext(select.onTrue(), U16Type))
            val extOnFalse = bb.putBefore(insertPos, ZeroExtend.zext(select.onFalse(), U16Type))
            val newSelect  = bb.putBefore(select, Select.select(select.condition(), U16Type, extOnTrue, extOnFalse))
            return bb.replace(select, Truncate.trunc(newSelect, U8Type))
        }

        return select
    }

    private fun killOnDemand(instruction: LocalValue) {
        instruction as Instruction
        if (instruction.usedIn().isEmpty()) { //TODO Need DCE
            instruction.owner().kill(instruction, UndefValue)
        }
    }

    override fun visit(store: Store): Instruction {
        val value = store.value()
        if (value.isa(f32v())) {
            val operand = value.asValue<F32Value>()
            val constant = module.addConstant(F32ConstantValue(constName(), operand.f32))
            store.value(constant)

        } else if (value.isa(f64v())) {
            val operand = value.asValue<F64Value>()
            val constant = module.addConstant(F64ConstantValue(constName(), operand.f64))
            store.value(constant)

        }

        store.match(store(extern(), any())) {
            // Before:
            //  store @global, %val
            //
            // After:
            //  %lea = load PtrType, @global
            //  store %lea, %val

            val lea = bb.putBefore(store, Load.load(PtrType, store.pointer()))
            store.pointer(lea)
            return lea
        }

        store.match(store(any(), extern())) {
            // Before:
            //  store %ptr, @global
            //
            // After:
            //  %lea = load PtrType, @global
            //  store %ptr, %lea

            val lea = bb.putBefore(store, Load.load(PtrType, store.value()))
            store.value(lea)
            return lea
        }

        store.match(store(any(), gAggregate())) {
            // Before:
            //  store %ptr, %gAggregate
            //
            // After:
            //  %lea = lea %gAggregate
            //  store %ptr, %lea

            val lea = bb.putBefore(store, Lea.lea(store.value()))
            store.value(lea)
            return lea
        }

        store.match(store(any(), generate())) {
            // Before:
            //  store %ptr, %val
            //
            // After:
            //  %res = lea %ptr
            //  store %res, %val

            val lea = bb.putBefore(store, Lea.lea(store.value().asValue()))
            store.value(lea)
            return lea
        }

        store.match(store(gValue(primitive()), any())) {
            // Before:
            //  %res = store i8 @global, %ptr
            //
            // After:
            //  %lea = lea @global
            //  %res = store i8 %val, %lea

            val lea = bb.putBefore(store, Lea.lea(store.pointer()))
            store.pointer(lea)
            return lea
        }

        store.match(store(generate(), gValue(anytype()))) {
            // Before:
            //  store %gen, @global
            //
            // After:
            //  %lea = lea @global
            //  move %gen, %lea

            val toValue = store.pointer().asValue<Generate>()
            val value = bb.putBefore(store, Lea.lea(store.value()))
            return bb.replace(store, Move.move(toValue, value))
        }

        store.match(store(any(), gValue(anytype()))) {
            // Before:
            //  store %ptr, @global
            //
            // After:
            //  %lea = lea @global
            //  move %ptr, %lea

            val lea = bb.putBefore(store, Lea.lea(store.value()))
            store.value(lea)
            return lea
        }

        store.match(store(generate(primitive()), any())) {
            // Before:
            //  store %gen, %ptr
            //
            // After:
            //  move %gen, %ptr

            val toValue = store.pointer().asValue<Generate>()
            return bb.replace(store, Move.move(toValue, store.value()))
        }

        store.match(store(gep(stackAlloc(), any()), any())) {
            // Before:
            //  %gep = gep %stackAlloc, %idx
            //  store %gep, %val
            //
            // After:
            //  movst %gen, %idx, %val

            val pointer = store.pointer().asValue<GetElementPtr>()
            val storeOnSt = StoreOnStack.store(pointer.source(), pointer.index(), store.value())
            val st = bb.replace(store, storeOnSt)
            killOnDemand(pointer)
            return st
        }

        store.match(store(gfp(stackAlloc()), any())) {
            // Before:
            //  %gfp = gfp %stackAlloc, %idx
            //  store %gfp, %val
            //
            // After:
            //  movst %gen, %idx, %val

            val pointer = store.pointer().asValue<GetFieldPtr>()
            val index = getGfpIndex(pointer, store.value().asType())
            val storeOnSt = StoreOnStack.store(pointer.source(), index, store.value())
            val st = bb.replace(store, storeOnSt)
            killOnDemand(pointer)
            return st
        }

        store.match(store(gep(stackAlloc().not(), any()), any())) {
            // Before:
            //  %gep = gep %anyVal, %idx
            //  store %gep, %val
            //
            // After:
            //  move %gep, %idx, %val

            val pointer = store.pointer().asValue<GetElementPtr>()
            val moveBy = MoveByIndex.move(pointer.source(), pointer.index(), store.value())
            val move = bb.replace(store, moveBy)
            killOnDemand(pointer)
            return move
        }

        store.match(store(gfp(stackAlloc().not()), any())) {
            // Before:
            //  %gfp = gfp %anyVal, %idx
            //  store %gfp, %val
            //
            // After:
            //  move %gfp, %idx, %val

            val pointer = store.pointer().asValue<GetFieldPtr>()
            val index = getGfpIndex(pointer, store.value().asType())
            val moveBy = MoveByIndex.move(pointer.source(), index, store.value())
            val move = bb.replace(store, moveBy)
            killOnDemand(pointer)
            return move
        }

        return store
    }

    override fun visit(upStackFrame: UpStackFrame): Instruction {
        return upStackFrame
    }

    override fun visit(voidCall: VoidCall): Instruction {
        return voidCall
    }

    override fun visit(int2ptr: Int2Pointer): Instruction {
        return int2ptr
    }

    override fun visit(ptr2Int: Pointer2Int): Instruction {
        ptr2Int.match(ptr2int(generate())) {
            // Before:
            //  %res = ptr2int %gen
            //
            // After:
            //  %lea = lea %gen
            //  %res = ptr2int %lea

            val lea = bb.putBefore(ptr2Int, Lea.lea(ptr2Int.operand().asValue()))
            ptr2Int.operand(lea)
            return lea
        }

        ptr2Int.match(ptr2int(gValue(anytype()))) {
            // Before:
            //  %res = ptr2int @global
            //
            // After:
            //  %lea = lea @global
            //  %res = ptr2int %lea

            val lea = bb.putBefore(ptr2Int, Lea.lea(ptr2Int.operand()))
            ptr2Int.operand(lea)
            return lea
        }

        ptr2Int.match(ptr2int(extern())) {
            // Before:
            //  %res = ptr2int @extern
            //
            // After:
            //  %lea = load PtrType, @extern
            //  %res = ptr2int %lea

            val use = ptr2Int.operand()
            val lea = bb.putBefore(ptr2Int, Load.load(PtrType, use))
            ptr2Int.operand(lea)
        }

        ptr2Int.match(ptr2int(gAggregate())) {
            // Before:
            //  %res = ptr2int %gAggregate
            //
            // After:
            //  %lea = lea %gAggregate
            //  %res = ptr2int %lea

            val use = ptr2Int.operand()
            val lea = bb.putBefore(ptr2Int, Lea.lea(use))
            ptr2Int.operand(lea)
        }

        return ptr2Int
    }

    override fun visit(memcpy: Memcpy): Instruction {
        memcpy.match(memcpy(extern(), any(), any())) {
            // Before:
            //  memcpy @extern, %dst, %size
            //
            // After:
            //  %srcLea = load PtrType, @extern
            //  memcpy %srcLea, %dst, %size

            val dst = bb.putBefore(memcpy, Load.load(PtrType, memcpy.destination()))
            memcpy.destination(dst)
            return dst
        }

        memcpy.match(memcpy(any(), extern(), any())) {
            // Before:
            //  memcpy @extern, %dst, %size
            //
            // After:
            //  %srcLea = load PtrType, @extern
            //  memcpy %srcLea, %dst, %size

            val src = bb.putBefore(memcpy, Load.load(PtrType, memcpy.source()))
            memcpy.source(src)
            return src
        }

        memcpy.match(memcpy(gAggregate(), any(), any())) {
            // Before:
            //  memcpy %src, %dst, %size
            //
            // After:
            //  %srcLea = lea %src
            //  memcpy %srcLea, %dst, %size

            val dst = bb.putBefore(memcpy, Lea.lea(memcpy.destination()))
            memcpy.destination(dst)
            return dst
        }

        memcpy.match(memcpy(any(), gAggregate(), any())) {
            // Before:
            //  memcpy %src, %dst, %size
            //
            // After:
            //  %dstLea = lea %dst
            //  memcpy %src, %dstLea, %size

            val src = bb.putBefore(memcpy, Lea.lea(memcpy.source()))
            memcpy.source(src)
            return src
        }

        memcpy.match(memcpy(stackAlloc(), stackAlloc(), any())) {
            // Before:
            //  memcpy %src, %dst
            //
            // After:
            //  %srcLea = lea %src
            //  %dstLea = lea %dst
            //  memcpy %srcLea, %dstLea

            val src = bb.putBefore(memcpy, Lea.lea(memcpy.source()))
            memcpy.source(src)

            val dst = bb.putBefore(memcpy, Lea.lea(memcpy.destination()))
            memcpy.destination(dst)
            return memcpy
        }

        memcpy.match(memcpy(any(), stackAlloc(), any())) {
            // Before:
            //  memcpy %src, %dst
            //
            // After:
            //  %srcLea = lea %src
            //  memcpy %srcLea, %dst

            val src = bb.putBefore(memcpy, Lea.lea(memcpy.source()))
            memcpy.source(src)

            val copyDst = bb.putBefore(memcpy, Copy.copy(memcpy.destination()))
            memcpy.destination(copyDst)
            return memcpy
        }

        memcpy.match(memcpy(stackAlloc(), any(), any())) {
            // Before:
            //  memcpy %src, %dst
            //
            // After:
            //  %dstLea = lea %dst
            //  memcpy %src, %dstLea

            val copySrc = bb.putBefore(memcpy, Copy.copy(memcpy.source()))
            memcpy.source(copySrc)

            val dst = bb.putBefore(memcpy, Lea.lea(memcpy.destination()))
            memcpy.destination(dst)
            return memcpy
        }

        memcpy.match(memcpy(any(), any(), any())) {
            // Before:
            //  memcpy %src, %dst
            //
            // After:
            //  %srcLea = copy %src
            //  %dstLea = copy %dst
            //  memcpy %srcLea, %dstLea

            val copySrc = bb.putBefore(memcpy, Copy.copy(memcpy.source()))
            memcpy.source(copySrc)

            val copyDst = bb.putBefore(memcpy, Copy.copy(memcpy.destination()))
            memcpy.destination(copyDst)
            return memcpy
        }

        return memcpy
    }

    override fun visit(indexedLoad: IndexedLoad): Instruction {
        return indexedLoad
    }

    override fun visit(store: StoreOnStack): Instruction {
        return store
    }

    override fun visit(loadst: LoadFromStack): Instruction {
        return loadst
    }

    override fun visit(leaStack: LeaStack): Instruction {
        return leaStack
    }

    private fun truncateProjections(tupleDiv: TupleDiv, newDiv: TupleDiv, type: IntegerType): Instruction {
        //  %projDiv = proj %newDiv, 0
        //  %projRem = proj %newDiv, 1
        //  %res = trunc %projDiv to u8
        //  %rem = trunc %projRem to u8

        val divProj = tupleDiv.quotient()
        val quotient = bb.putBefore(tupleDiv, Projection.proj(newDiv, 0))
        val quotientTrunc = bb.putBefore(tupleDiv, Truncate.trunc(quotient, type))
        divProj.updateUsages(quotientTrunc)
        killOnDemand(divProj)

        val remainder = tupleDiv.remainder()
        val proj      = bb.putBefore(tupleDiv, Projection.proj(newDiv, 1))
        val remainderTruncate = bb.putBefore(tupleDiv, Truncate.trunc(proj, type))
        remainder.updateUsages(remainderTruncate)
        killOnDemand(remainder)

        killOnDemand(tupleDiv)
        return remainderTruncate
    }

    private fun isolateProjections(tupleDiv: TupleDiv) {
        val rem = tupleDiv.remainder()
        rem.updateUsages(bb.putAfter(rem, Copy.copy(rem)))

        val div = tupleDiv.quotient()
        div.updateUsages(bb.putAfter(div, Copy.copy(div)))
    }

    override fun visit(tupleDiv: TupleDiv): Instruction {
        tupleDiv.match(tupleDiv(value(i8()), value(i8()))) {
            // Before:
            //  %resANDrem = div i8 %a, %b
            //
            // After:
            //  %extFirst  = sext %a to i16
            //  %extSecond = sext %b to i16
            //  %newDiv = div i16 %extFirst, %extSecond
            //  %projDiv = proj %newDiv, 0
            //  %projRem = proj %newDiv, 1
            //  %res = trunc %projDiv to i8
            //  %rem = trunc %projRem to i8

            val extFirst  = bb.putBefore(tupleDiv, SignExtend.sext(tupleDiv.lhs(), I16Type))
            val extSecond = bb.putBefore(tupleDiv, SignExtend.sext(tupleDiv.rhs(), I16Type))
            val newDiv    = bb.putBefore(tupleDiv, TupleDiv.div(extFirst, extSecond))

            return truncateProjections(tupleDiv, newDiv, I8Type)
        }

        tupleDiv.match(tupleDiv(value(u8()), value(u8()))) {
            // Before:
            //  %resANDrem = div u8 %a, %b
            //
            // After:
            //  %extFirst  = zext %a to u16
            //  %extSecond = zext %b to u16
            //  %newDiv = div u16 %extFirst, %extSecond
            //  %projDiv = proj %newDiv, 0
            //  %projRem = proj %newDiv, 1
            //  %res = trunc %projDiv to u8
            //  %rem = trunc %projRem to u8

            val extFirst  = bb.putBefore(tupleDiv, ZeroExtend.zext(tupleDiv.lhs(), U16Type))
            val extSecond = bb.putBefore(tupleDiv, ZeroExtend.zext(tupleDiv.rhs(), U16Type))
            val newDiv    = bb.putBefore(tupleDiv, TupleDiv.div(extFirst, extSecond))

            return truncateProjections(tupleDiv, newDiv, U8Type)
        }

        tupleDiv.match(tupleDiv(any(), local())) {
            // Before:
            //  %resANDrem = div %a, %b
            //  %projDiv = proj %resANDrem, 0
            //  %projRem = proj %resANDrem, 1
            //
            // After:
            //  %copy = copy %b
            //  %resANDrem = div %a, %copy
            //  %projDiv = proj %resANDrem, 0
            //  %projRem = proj %resANDrem, 1 <-- rdx
            //  %res = copy %projDiv
            //  %rem = copy %projRem

            isolateProjections(tupleDiv)
            val divider = tupleDiv.rhs().asValue<LocalValue>()
            val copy = bb.putBefore(tupleDiv, Copy.copy(divider))
            tupleDiv.rhs(copy)
            return tupleDiv
        }

        tupleDiv.match(tupleDiv(any(), any())) {
            // Before:
            //  %resANDrem = div %a, %b
            //  %projDiv = proj %resANDrem, 0
            //  %projRem = proj %resANDrem, 1
            //
            // After:
            //  %resANDrem = div %a, %b
            //  %projDiv = proj %resANDrem, 0
            //  %projRem = proj %resANDrem, 1 <-- rdx
            //  %res = copy %projDiv
            //  %rem = copy %projRem

            isolateProjections(tupleDiv)
            return tupleDiv
        }

        return tupleDiv
    }

    override fun visit(proj: Projection): Instruction {
        return proj
    }

    override fun visit(switch: Switch): Instruction {
        return switch
    }

    override fun visit(tupleCall: IndirectionTupleCall): Instruction {
        tupleCall.match(itCall(extern())) {
            // Before:
            //  %res = indirectionCall @global
            //
            // After:
            //  %lea = load @global
            //  %res = indirectionCall %lea

            val use = tupleCall.pointer()
            val lea = bb.putBefore(tupleCall, Load.load(PtrType, use))
            tupleCall.pointer(lea)
            return lea
        }

        tupleCall.match(itCall(function())) {
            // Before:
            //  %res = indirectionCall %gAggregate
            //
            // After:
            //  %lea = lea %gAggregate
            //  %res = indirectionCall %lea

            val use = tupleCall.pointer()
            val lea = bb.putBefore(tupleCall, Lea.lea(use))
            tupleCall.pointer(lea)
            return lea
        }

        return tupleCall
    }

    override fun visit(intrinsic: Intrinsic): Instruction {
        return intrinsic
    }

    companion object {
        private const val F32_SUBZERO: Float = -0.0f
        private const val F64_SUBZERO: Double = -0.0
        private const val PREFIX = CallConvention.CONSTANT_POOL_PREFIX

        fun run(module: Module): Module {
            for ((idx, fn) in module.functions().withIndex()) {
                Lowering(fn, module, idx).pass()
            }

            return module
        }
    }
}