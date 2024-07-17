package ir.module.builder.impl

import ir.instruction.*
import ir.module.*
import ir.module.block.Block
import ir.module.block.Label
import ir.module.block.InstructionFabric
import ir.module.builder.AnyFunctionDataBuilder
import ir.types.*
import ir.value.ArgumentValue
import ir.value.IntegerConstant
import ir.value.UnsignedIntegerConstant
import ir.value.Value


class FunctionDataBuilder private constructor(
    prototype: FunctionPrototype,
    argumentValues: List<ArgumentValue>,
    blocks: BasicBlocks
): AnyFunctionDataBuilder(prototype, argumentValues, blocks), InstructionFabric {
    override fun build(): FunctionData {
        normalizeBlocks()
        return FunctionData.create(prototype, blocks, argumentValues)
    }

    fun switchLabel(label: Label) {
        bb = blocks.findBlock(label)
    }

    //TODO bad design???
    fun last(): Instruction? {
        return bb.lastOrNull()
    }

    override fun not(value: Value): Not {
        return bb.not(value)
    }

    override fun neg(value: Value): Neg {
        return bb.neg(value)
    }

    override fun arithmeticBinary(a: Value, op: ArithmeticBinaryOp, b: Value): ArithmeticBinary {
        return bb.arithmeticBinary(a, op, b)
    }

    override fun tupleDiv(a: Value, b: Value): TupleDiv {
        return bb.tupleDiv(a, b)
    }

    override fun icmp(a: Value, predicate: IntPredicate, b: Value): SignedIntCompare {
        return bb.icmp(a, predicate, b)
    }

    override fun fcmp(a: Value, predicate: FloatPredicate, b: Value): FloatCompare {
        return bb.fcmp(a, predicate, b)
    }

    override fun load(loadedType: PrimitiveType, ptr: Value): Load {
        return bb.load(loadedType, ptr)
    }

    override fun store(ptr: Value, value: Value): Store {
        return bb.store(ptr, value)
    }

    override fun call(func: AnyFunctionPrototype, args: List<Value>, target: Label): Call {
        return bb.call(func, args, target as Block)
    }

    override fun tupleCall(func: AnyFunctionPrototype, args: List<Value>, target: Block): TupleCall {
        return bb.tupleCall(func, args, target)
    }

    override fun vcall(func: AnyFunctionPrototype, args: List<Value>, target: Label): VoidCall {
        return bb.vcall(func, args, target as Block)
    }

    override fun icall(pointer: Value, func: IndirectFunctionPrototype, args: List<Value>, target: Label): IndirectionCall {
        return bb.icall(pointer, func, args, target)
    }

    override fun ivcall(pointer: Value, func: IndirectFunctionPrototype, args: List<Value>, target: Block): IndirectionVoidCall {
        return bb.ivcall(pointer, func, args, target)
    }

    fun branch(target: Label) {
        branch(blocks.findBlock(target))
    }

    override fun branch(target: Block): Branch {
        return bb.branch(target)
    }

    override fun branchCond(value: Value, onTrue: Block, onFalse: Block): BranchCond {
        return bb.branchCond(value, onTrue, onFalse)
    }

    fun branchCond(value: Value, onTrue: Label, onFalse: Label) {
        branchCond(value, blocks.findBlock(onTrue), blocks.findBlock(onFalse))
    }

    override fun alloc(ty: NonTrivialType): Alloc {
        return bb.alloc(ty)
    }

    override fun ret(returnType: Type, values: Array<Value>): Return {
        return bb.ret(returnType, values)
    }

    override fun retVoid(): ReturnVoid {
        return bb.retVoid()
    }

    override fun gep(source: Value, elementType: NonTrivialType, index: Value): GetElementPtr {
        return bb.gep(source, elementType, index)
    }

    override fun gfp(source: Value, ty: AggregateType, indexes: Array<IntegerConstant>): GetFieldPtr {
        return bb.gfp(source, ty, indexes)
    }

    override fun flag2int(value: Value, ty: IntegerType): Flag2Int {
        return bb.flag2int(value, ty)
    }

    override fun int2fp(value: Value, ty: FloatingPointType): Int2Float {
        return bb.int2fp(value, ty)
    }

    override fun bitcast(value: Value, ty: PrimitiveType): Bitcast {
        return bb.bitcast(value, ty)
    }

    override fun zext(value: Value, toType: UnsignedIntType): ZeroExtend {
        return bb.zext(value, toType)
    }

    override fun sext(value: Value, toType: SignedIntType): SignExtend {
        return bb.sext(value, toType)
    }

    override fun trunc(value: Value, toType: IntegerType): Truncate {
        return bb.trunc(value, toType)
    }

    override fun select(cond: Value, type: PrimitiveType, onTrue: Value, onFalse: Value): Select {
        return bb.select(cond, type, onTrue, onFalse)
    }

    override fun fptrunc(value: Value, toType: FloatingPointType): FpTruncate {
        return bb.fptrunc(value, toType)
    }

    override fun fpext(value: Value, toType: FloatingPointType): FpExtend {
        return bb.fpext(value, toType)
    }

    override fun fp2Int(value: Value, toType: IntegerType): FloatToInt {
        return bb.fp2Int(value, toType)
    }

    override fun phi(incoming: List<Value>, labels: List<Label>): Phi {
        return bb.phi(incoming, labels)
    }

    override fun int2ptr(value: Value): Int2Pointer {
        return bb.int2ptr(value)
    }

    override fun ptr2int(value: Value, toType: IntegerType): Pointer2Int {
        return bb.ptr2int(value, toType)
    }

    override fun memcpy(dst: Value, src: Value, length: UnsignedIntegerConstant) {
        bb.memcpy(dst, src, length)
    }

    override fun proj(tuple: Value, index: Int): Projection {
        return bb.proj(tuple, index)
    }

    override fun switch(value: Value, default: Label, table: List<IntegerConstant>, targets: List<Label>): Switch {
        return bb.switch(value, default, table, targets)
    }

    companion object {
        fun create(
            name: String,
            returnType: Type,
            arguments: List<Type>,
            argumentValues: List<ArgumentValue>,
            isVararg: Boolean = false
        ): FunctionDataBuilder {
            val prototype = FunctionPrototype(name, returnType, arguments, isVararg)
            val basicBlocks = BasicBlocks.create()

            val builder = FunctionDataBuilder(prototype, argumentValues, basicBlocks)
            builder.switchLabel(basicBlocks.begin())
            return builder
        }

        fun create(name: String, returnType: Type, argumentTypes: List<Type>, isVararg: Boolean = false): FunctionDataBuilder {
            val argumentValues = arrayListOf<ArgumentValue>()
            for ((idx, arg) in argumentTypes.withIndex()) {
                if (arg !is PrimitiveType) { //TODO not coeer
                    continue
                }

                argumentValues.add(ArgumentValue(idx, arg))
            }

            return create(name, returnType, argumentTypes, argumentValues, isVararg)
        }
    }
}