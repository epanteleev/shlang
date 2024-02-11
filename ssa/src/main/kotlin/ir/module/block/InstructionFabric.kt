package ir.module.block

import ir.Value
import ir.types.*
import ir.instruction.*
import ir.AnyFunctionPrototype
import ir.IndirectFunctionPrototype
import ir.IntegerConstant


interface InstructionFabric {
    fun neg(value: Value): Neg
    fun not(value: Value): Not
    fun arithmeticBinary(a: Value, op: ArithmeticBinaryOp, b: Value): ArithmeticBinary
    fun icmp(a: Value, predicate: IntPredicate, b: Value): SignedIntCompare
    fun ucmp(a: Value, predicate: IntPredicate, b: Value): UnsignedIntCompare
    fun pcmp(a: Value, predicate: IntPredicate, b: Value): PointerCompare
    fun fcmp(a: Value, predicate: FloatPredicate, b: Value): FloatCompare
    fun load(loadedType: PrimitiveType, ptr: Value): Load
    fun store(ptr: Value, value: Value)
    fun call(func: AnyFunctionPrototype, args: List<Value>): Call
    fun vcall(func: AnyFunctionPrototype, args: List<Value>)
    fun icall(pointer: Value, func: IndirectFunctionPrototype, args: List<Value>): IndirectionCall
    fun ivcall(pointer: Value, func: IndirectFunctionPrototype, args: List<Value>)
    fun branch(target: Block)
    fun branchCond(value: Value, onTrue: Block, onFalse: Block)
    fun alloc(ty: Type): Alloc
    fun ret(value: Value)
    fun retVoid()
    fun gep(source: Value, ty: PrimitiveType, index: Value): GetElementPtr
    fun gfp(source: Value, ty: AggregateType, index: IntegerConstant): GetFieldPtr
    fun bitcast(value: Value, ty: PrimitiveType): Bitcast
    fun zext(value: Value, toType: UnsignedIntType): ZeroExtend
    fun sext(value: Value, toType: SignedIntType): SignExtend
    fun trunc(value: Value, toType: IntegerType): Truncate
    fun fptrunc(value: Value, toType: FloatingPointType): FpTruncate
    fun fpext(value: Value, toType: FloatingPointType): FpExtend
    fun fptosi(value: Value, toType: SignedIntType): FloatToSigned
    fun select(cond: Value, type: PrimitiveType, onTrue: Value, onFalse: Value): Select
    fun phi(incoming: List<Value>, labels: List<Block>): Phi
}

interface InternalInstructionFabric {
    fun gen(ty: PrimitiveType): Generate
    fun uncompletedPhi(ty: PrimitiveType, incoming: Value): Phi
    fun copy(value: Value): Copy
    fun move(toValue: Generate, fromValue: Value)
    fun downStackFrame(callable: Callable)
    fun upStackFrame(callable: Callable)
}

interface AnyInstructionFabric : InstructionFabric, InternalInstructionFabric