package ir.module.builder.impl

import ir.attributes.ArgumentValueAttribute
import ir.attributes.ByValue
import ir.attributes.FunctionAttribute
import ir.types.*
import ir.value.*
import ir.module.*
import ir.value.Value
import ir.instruction.*
import ir.intrinsic.IntrinsicProvider
import ir.module.block.Block
import ir.module.block.Label
import ir.module.block.InstructionFabric
import ir.module.builder.AnyFunctionDataBuilder
import ir.value.constant.IntegerConstant
import ir.value.constant.UnsignedIntegerConstant


class FunctionDataBuilder private constructor(prototype: FunctionPrototype, argumentValues: List<ArgumentValue>):
    AnyFunctionDataBuilder(prototype, argumentValues), InstructionFabric {

    init {
        switchLabel(fd.begin())
    }

    override fun build(): FunctionData {
        normalizeBlocks()
        return fd
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

    override fun add(a: Value, b: Value): Add {
        return bb.add(a, b)
    }

    override fun xor(a: Value, b: Value): Xor {
        return bb.xor(a, b)
    }

    override fun sub(a: Value, b: Value): Sub {
        return bb.sub(a, b)
    }

    override fun mul(a: Value, b: Value): Mul {
        return bb.mul(a, b)
    }

    override fun shr(a: Value, b: Value): Shr {
        return bb.shr(a, b)
    }

    override fun or(a: Value, b: Value): Or {
        return bb.or(a, b)
    }

    override fun shl(a: Value, b: Value): Shl {
        return bb.shl(a, b)
    }

    override fun and(a: Value, b: Value): And {
        return bb.and(a, b)
    }

    override fun div(a: Value, b: Value): Div {
        return bb.div(a, b)
    }

    override fun tupleDiv(a: Value, b: Value): TupleDiv {
        return bb.tupleDiv(a, b)
    }

    override fun icmp(a: Value, predicate: IntPredicate, b: Value): IntCompare {
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

    override fun call(func: DirectFunctionPrototype, args: List<Value>, attributes: Set<FunctionAttribute>, target: Label): Call {
        return bb.call(func, args, attributes, target as Block)
    }

    override fun tupleCall(func: DirectFunctionPrototype, args: List<Value>, attributes: Set<FunctionAttribute>, target: Label): TupleCall {
        return bb.tupleCall(func, args, attributes, target)
    }

    override fun vcall(func: DirectFunctionPrototype, args: List<Value>, attributes: Set<FunctionAttribute>, target: Label): VoidCall {
        return bb.vcall(func, args, attributes, target as Block)
    }

    override fun icall(pointer: Value, func: IndirectFunctionPrototype, args: List<Value>, attributes: Set<FunctionAttribute>, target: Label): IndirectionCall {
        return bb.icall(pointer, func, args, attributes, target)
    }

    override fun ivcall(pointer: Value, func: IndirectFunctionPrototype, args: List<Value>, attributes: Set<FunctionAttribute>, target: Label): IndirectionVoidCall {
        return bb.ivcall(pointer, func, args, attributes, target)
    }

    fun branch(target: Label) {
        branch(fd.blocks.findBlock(target))
    }

    override fun branch(target: Block): Branch {
        return bb.branch(target)
    }

    override fun branchCond(value: Value, onTrue: Label, onFalse: Label): BranchCond {
        return bb.branchCond(value, onTrue, onFalse)
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

    override fun gfp(source: Value, ty: AggregateType, index: IntegerConstant): GetFieldPtr {
        return bb.gfp(source, ty, index)
    }

    override fun flag2int(value: Value, ty: IntegerType): Flag2Int {
        return bb.flag2int(value, ty)
    }

    override fun int2fp(value: Value, ty: FloatingPointType): Int2Float {
        return bb.int2fp(value, ty)
    }

    override fun uint2fp(value: Value, ty: FloatingPointType): Unsigned2Float {
        return bb.uint2fp(value, ty)
    }

    override fun bitcast(value: Value, ty: IntegerType): Bitcast {
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

    override fun select(cond: Value, type: IntegerType, onTrue: Value, onFalse: Value): Select {
        return bb.select(cond, type, onTrue, onFalse)
    }

    override fun fptrunc(value: Value, toType: FloatingPointType): FpTruncate {
        return bb.fptrunc(value, toType)
    }

    override fun fpext(value: Value, toType: FloatingPointType): FpExtend {
        return bb.fpext(value, toType)
    }

    override fun fp2Int(value: Value, toType: IntegerType): Float2Int {
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

    override fun memcpy(dst: Value, src: Value, length: UnsignedIntegerConstant): Memcpy {
        return bb.memcpy(dst, src, length)
    }

    override fun proj(tuple: Value, index: Int): Projection {
        return bb.proj(tuple, index)
    }

    override fun switch(value: Value, default: Label, table: List<IntegerConstant>, targets: List<Label>): Switch {
        return bb.switch(value, default, table, targets)
    }

    override fun intrinsic(inputs: List<Value>, implementor: IntrinsicProvider, target: Label): Intrinsic {
        return bb.intrinsic(inputs, implementor, target)
    }

    companion object {
        fun create(
            name: String,
            returnType: Type,
            arguments: List<NonTrivialType>,
            argumentValues: List<ArgumentValue>,
            attributes: Set<FunctionAttribute>
        ): FunctionDataBuilder {
            val prototype = FunctionPrototype(name, returnType, arguments, attributes)
            return FunctionDataBuilder(prototype, argumentValues)
        }

        fun create(name: String, returnType: Type, argumentTypes: List<NonTrivialType>, attributes: Set<FunctionAttribute>): FunctionDataBuilder {
            val argumentValues = arrayListOf<ArgumentValue>()
            val argAttributes = attributes.filterIsInstance<ArgumentValueAttribute>()
            for ((idx, arg) in argumentTypes.withIndex()) { //TODO simplify!??
                val byValue = argAttributes.find { it is ByValue && it.argumentIndex == idx }
                val argAttr = if (byValue != null) {
                    hashSetOf(byValue)
                } else {
                    hashSetOf()
                }
                argumentValues.add(ArgumentValue(idx, arg, argAttr))
            }

            return create(name, returnType, argumentTypes, argumentValues, attributes)
        }
    }
}