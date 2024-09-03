package ir.pass.transform.auxiliary

import ir.types.*
import ir.value.*
import ir.module.*
import ir.instruction.*
import ir.instruction.Store.Companion.VALUE
import ir.module.block.Block
import ir.instruction.matching.*
import ir.pass.analysis.traverse.BfsOrderOrderFabric


class Lowering private constructor(private val cfg: FunctionData) {
    private fun replaceAllocLoadStores() {
        fun closure(bb: Block, inst: Instruction): Instruction? = match(inst) {
            store(generate(), nop()) { store ->
                val toValue = store.pointer().asValue<Generate>()
                bb.replace(store) { it.move(toValue, store.value()) }
            }
            load(generate()) { load ->
                val fromValue = load.operand().asValue<Generate>()
                bb.replace(load) { it.copy(fromValue) }
            }
            default()
        }

        for (bb in cfg) {
            bb.transform { inst -> closure(bb, inst) }
        }
    }

    private fun replaceGepToLea() {
        fun closure(bb: Block, inst: Instruction): Instruction? = match(inst) {
            gep(generate(), nop()) { gp ->
                when (val baseType = gp.basicType) {
                    is AggregateType -> {
                        val index = gp.index()
                        val offset = bb.insertBefore(inst) {
                            it.mul(index, Constant.of(index.asType(), baseType.sizeOf()))
                        }
                        bb.replace(inst) { it.leaStack(gp.source(), Type.I8, offset) }
                    }
                    is PrimitiveType -> {
                        bb.replace(inst) { it.leaStack(gp.source(), baseType, gp.index()) }
                    }
                }
            }
            gfp(generate()) { gf ->
                when (val base = gf.basicType) {
                    is ArrayType -> {
                        when (val tp = base.elementType()) {
                            is AggregateType -> {
                                val index = gf.index(0).toInt()
                                val offset = tp.offset(index)
                                bb.replace(inst) { it.leaStack(gf.source(), Type.I8, Constant.of(Type.U32, offset)) }
                            }
                            else -> {
                                tp as PrimitiveType
                                bb.replace(inst) { it.leaStack(gf.source(), tp, gf.index(0)) }
                            }
                        }
                    }
                    is StructType -> bb.replace(inst) { it.leaStack(gf.source(), Type.U8, Constant.of(Type.U32, base.offset(gf.index(0).toInt()))) }
                }
            }
            default()
        }

        for (bb in cfg) {
            bb.transform { inst -> closure(bb, inst) }
        }
    }

    private fun replaceGEPAndStore() {
        fun getSource(inst: Instruction): Value {
            return when (inst) {
                is GetElementPtr -> inst.source()
                is GetFieldPtr   -> inst.source()
                else             -> throw IllegalArgumentException("Expected GEP or GFP")
            }
        }

        fun getIndex(inst: Instruction): Value {
            return when (inst) {
                is GetElementPtr -> inst.index()
                is GetFieldPtr -> {
                    val index = inst.index(0).toInt()
                    val field = inst.basicType.field(index)
                    U64Value(inst.basicType.offset(index).toLong() / field.sizeOf())
                }
                else -> throw IllegalArgumentException("Expected GEP or GFP")
            }
        }

        fun closure(bb: Block, inst: Instruction): Instruction {
            when {
                store(gfpOrGep(generate().not(), nop()), nop()) (inst) -> { inst as Store
                    val pointer = inst.pointer().asValue<ValueInstruction>()
                    val move = bb.replace(inst) { it.move(getSource(pointer), getIndex(pointer), inst.value()) }
                    killOnDemand(bb, pointer)
                    return move
                }
                store(gfpOrGep(generate(), nop()), nop()) (inst) -> { inst as Store
                    val pointer = inst.pointer().asValue<ValueInstruction>()
                    val st = bb.replace(inst) { it.storeOnStack(getSource(pointer), getIndex(pointer), inst.value()) }
                    killOnDemand(bb, pointer)
                    return st
                }
                load(gfpOrGep(generate().not(), nop())) (inst) -> { inst as Load
                    val pointer = inst.operand().asValue<ValueInstruction>()
                    val copy = bb.replace(inst) { it.indexedLoad(getSource(pointer), inst.type(), getIndex(pointer)) }
                    killOnDemand(bb, pointer)
                    return copy
                }
                load(gfpOrGep(generate(), nop())) (inst) -> { inst as Load
                    val pointer = inst.operand().asValue<ValueInstruction>()
                    val index = getIndex(pointer)
                    val copy = bb.replace(inst) { it.loadFromStack(getSource(pointer), inst.type(), index) }
                    killOnDemand(bb, pointer)
                    return copy
                }
            }
            return inst
        }

        for (bb in cfg) {
            bb.transform { inst -> closure(bb, inst) }
        }
    }

    private fun killOnDemand(bb: Block, instruction: LocalValue) {
        instruction as Instruction
        if (instruction.usedIn().isEmpty()) { //TODO Need DCE
            bb.kill(instruction, Value.UNDEF) // TODO bb may not contain pointer
        }
    }

    private fun replaceByteDiv() {
        fun closure(bb: Block, inst: Instruction): Instruction {
            when {
                tupleDiv(value(i8()), value(i8())) (inst) -> { inst as TupleDiv
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

                    val extFirst  = bb.insertBefore(inst) { it.sext(inst.first(), Type.I16) }
                    val extSecond = bb.insertBefore(inst) { it.sext(inst.second(), Type.I16) }
                    val newDiv    = bb.insertBefore(inst) { it.tupleDiv(extFirst, extSecond) }
                    var last: Instruction = newDiv

                    val divProj = inst.proj(0)
                    if (divProj != null) {
                        val proj     = bb.insertBefore(inst) { it.proj(newDiv, 0) }
                        val truncate = bb.updateUsages(divProj) {
                            bb.insertBefore(inst) { it.trunc(proj, Type.I8) }
                        }
                        killOnDemand(bb, divProj)
                        last = truncate
                    }

                    val remProj = inst.proj(1)
                    if (remProj != null) {
                        val proj     = bb.insertBefore(inst) { it.proj(newDiv, 1) }
                        val truncate = bb.updateUsages(remProj) {
                            bb.insertBefore(inst) { it.trunc(proj, Type.I8) }
                        }
                        killOnDemand(bb, remProj)
                        last = truncate
                    }

                    killOnDemand(bb, inst)
                    return last
                }
                div(constant().not(), nop()) (inst) -> { inst as ArithmeticBinary
                    // TODO temporal
                    val second = inst.second()
                    val copy = bb.insertBefore(inst) { it.copy(second) }
                    bb.updateDF(inst, ArithmeticBinary.SECOND, copy)
                    return inst
                }
                tupleDiv(constant().not(), nop()) (inst) -> { inst as TupleDiv
                    // TODO temporal
                    val second = inst.second()
                    val copy = bb.insertBefore(inst) { it.copy(second) }
                    bb.updateDF(inst, TupleDiv.SECOND, copy)
                    return inst
                }
                select(nop(), value(i8()), value(i8())) (inst) -> { inst as Select
                    // Before:
                    //  %res = select i1 %cond, i8 %onTrue, i8 %onFalse
                    //
                    // After:
                    //  %extOnTrue  = sext %onTrue to i16
                    //  %extOnFalse = sext %onFalse to i16
                    //  %newSelect = select i1 %cond, i16 %extOnTrue, i16 %extOnFalse
                    //  %res = trunc %newSelect to i8

                    val extOnTrue  = bb.insertBefore(inst) { it.sext(inst.onTrue(), Type.I16) }
                    val extOnFalse = bb.insertBefore(inst) { it.sext(inst.onFalse(), Type.I16) }
                    val newSelect  = bb.insertBefore(inst) { it.select(inst.condition(), Type.I16, extOnTrue, extOnFalse) }
                    return bb.replace(inst) { it.trunc(newSelect, Type.I8) }
                }
                select(nop(), value(u8()), value(u8())) (inst) -> { inst as Select
                    // Before:
                    //  %res = select i1 %cond, i8 %onTrue, i8 %onFalse
                    //
                    // After:
                    //  %extOnTrue  = zext %onTrue to i16
                    //  %extOnFalse = zext %onFalse to i16
                    //  %newSelect = select i1 %cond, i16 %extOnTrue, i16 %extOnFalse
                    //  %res = trunc %newSelect to i8

                    val extOnTrue  = bb.insertBefore(inst) { it.zext(inst.onTrue(), Type.U16) }
                    val extOnFalse = bb.insertBefore(inst) { it.zext(inst.onFalse(), Type.U16) }
                    val newSelect  = bb.insertBefore(inst) { it.select(inst.condition(), Type.U16, extOnTrue, extOnFalse) }
                    return bb.replace(inst) { it.trunc(newSelect, Type.U8) }
                }
            }
            return inst
        }

        for (bb in cfg.analysis(BfsOrderOrderFabric)) {
            bb.transform { inst -> closure(bb, inst) }
        }
    }

    private fun replaceEscaped() {
        fun closure(bb: Block, inst: Instruction): Instruction? {
            when {
                alloc() (inst) -> { inst as Alloc
                    return bb.replace(inst) { it.gen(inst.allocatedType) }
                }
                store(nop(), generate()) (inst) -> { inst as Store
                    val lea = bb.insertBefore(inst) { it.lea(inst.value().asValue()) }
                    bb.updateDF(inst, VALUE, lea)
                    return inst
                }
                store(gValue(primitive()), nop()) (inst) -> { inst as Store //TODO inefficient lowering
                    // Before:
                    //  %res = store i8 @global, %ptr
                    //
                    // After:
                    //  %lea = lea @global
                    //  %res = store i8 %val, %lea

                    val lea = bb.insertBefore(inst) { it.lea(inst.pointer().asValue()) }
                    bb.updateDF(inst, Store.DESTINATION, lea)
                    return lea
                }
                inst is Value && gfp(gValue(anytype())) (inst) -> { inst as GetFieldPtr //TODO inefficient lowering
                    // Before:
                    //  %res = gfp @global, %idx
                    //
                    // after:
                    //  %lea = lea @global
                    //  %res = gfp %lea, %idx

                    val lea = bb.insertBefore(inst) { it.lea(inst.source().asValue()) }
                    bb.updateDF(inst, GetFieldPtr.SOURCE, lea)
                    return lea
                }
                inst is Value && gep(gValue(anytype()), nop()) (inst) -> { inst as GetElementPtr //TODO inefficient lowering
                    // Before:
                    //  %res = gep @global, %idx
                    //
                    // after:
                    //  %lea = lea @global
                    //  %res = gep %lea, %idx

                    val lea = bb.insertBefore(inst) { it.lea(inst.source().asValue()) }
                    bb.updateDF(inst, GetElementPtr.SOURCE, lea)
                    return lea
                }
                copy(generate()) (inst) -> { inst as Copy
                    return bb.replace(inst) { it.lea(inst.origin().asValue()) }
                }
                ptr2int(generate()) (inst) -> { inst as Pointer2Int
                    val lea = bb.insertBefore(inst) { it.lea(inst.value().asValue()) }
                    bb.updateDF(inst, Pointer2Int.SOURCE, lea)
                    return inst
                }
                // TODO memcpy can be replaced with moves in some cases
                memcpy(nop(), generate(), nop()) (inst) -> { inst as Memcpy
                    val src = bb.insertBefore(inst) { it.lea(inst.source().asValue<Generate>()) }
                    bb.updateDF(inst, Memcpy.SOURCE, src)
                    return inst.prev()
                }
                memcpy(generate(), nop(), nop()) (inst) -> { inst as Memcpy
                    val dst = bb.insertBefore(inst) { it.lea(inst.destination().asValue<Generate>()) }
                    bb.updateDF(inst, Memcpy.DESTINATION, dst)
                    return inst.prev()
                }
            }
            return inst
        }

        for (bb in cfg.analysis(BfsOrderOrderFabric)) {
            bb.transform { inst -> closure(bb, inst) }
        }
    }

    private fun pass() {
        replaceByteDiv()
        replaceEscaped()
        replaceAllocLoadStores()
        replaceGEPAndStore()
        replaceGepToLea()
    }

    companion object {
        fun run(module: Module): Module {
            for (fn in module.functions()) {
                Lowering(fn).pass()
            }
            return module
        }
    }
}