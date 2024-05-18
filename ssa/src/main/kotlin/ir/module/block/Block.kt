package ir.module.block

import ir.*
import ir.Value
import ir.types.*
import ir.instruction.*
import ir.instruction.lir.*
import common.LeakedLinkedList
import ir.module.AnyFunctionPrototype
import ir.module.IndirectFunctionPrototype


class Block(override val index: Int):
    AnyInstructionFabric, AnyBlock, Iterable<Instruction> {
    private val instructions = InstructionList()
    private val predecessors = arrayListOf<Block>()
    private val successors   = arrayListOf<Block>()

    private var insertionStrategy: InsertionStrategy = InsertAfter(null)
    private var instructionIndex: Int = 0

    abstract inner class InsertionStrategy {
        abstract fun insert(instruction: Instruction);
    }

    inner class InsertBefore(private val before: Instruction?) : InsertionStrategy() {
        override fun insert(instruction: Instruction) {
            instructions.addBefore(before, instruction)
        }
    }

    inner class InsertAfter(private val after: Instruction?) : InsertionStrategy() {
        override fun insert(instruction: Instruction) {
            instructions.addAfter(after, instruction)
        }
    }

    override fun predecessors(): List<Block> {
        return predecessors
    }

    override fun successors(): List<Block> {
        return successors
    }

    override fun last(): TerminateInstruction {
        return lastOrNull() as TerminateInstruction
    }

    fun lastOrNull(): TerminateInstruction? {
        if (instructions.isEmpty()) {
            return null
        }

        val last = instructions.last()
        if (last !is TerminateInstruction) {
            return null
        }

        return last
    }

    override fun begin(): Instruction {
        assert(instructions.isNotEmpty()) {
            "bb=$this must have any instructions"
        }

        return instructions[0]
    }

    val size get(): Int = instructions.size

    private fun addPredecessor(bb: Block) {
        predecessors.add(bb)
    }

    private fun addSuccessor(bb: Block) {
        successors.add(bb)
    }

    private fun updateSuccessor(old: Block, new: Block) {
        val index = successors.indexOf(old)
        if (index == -1) {
            throw RuntimeException("Out of index: old=$old")
        }

        new.predecessors.add(this)
        successors[index] = new
    }

    private fun removePredecessors(old: Block) {
        predecessors.remove(old)
    }

    fun forEachInstruction(fn: (Instruction) -> Int) {
        var i = 0
        while (i < instructions.size) {
            i += fn(instructions[i]) + 1
        }
    }

    fun<T> prepend(builder: (AnyInstructionFabric) -> T): T {
        insertionStrategy = InsertBefore(null)
        return builder(this)
    }

    fun<T> insertAfter(after: Instruction, builder: (AnyInstructionFabric) -> T): T {
        insertionStrategy = InsertAfter(after)
        return builder(this)
    }

    fun<T> insertBefore(before: Instruction, builder: (AnyInstructionFabric) -> T): T {
        insertionStrategy = InsertBefore(before)
        return builder(this)
    }

    override fun contains(instruction: Instruction): Boolean {
        return instructions.contains(instruction)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true

        if (other is BlockViewer) {
            return index == other.index
        }

        if (javaClass != other?.javaClass) return false
        other as Block

        if (index != other.index) return false
        if (instructions != other.instructions) return false
        if (predecessors != other.predecessors) return false
        return successors == other.successors
    }

    override fun hashCode(): Int {
        return index
    }

    fun isEmpty(): Boolean {
        return instructions.isEmpty()
    }

    override operator fun iterator(): Iterator<Instruction> {
        return instructions.iterator()
    }

    fun valueInstructions(fn: (ValueInstruction) -> Unit) {
        instructions.forEach {
            if (it !is ValueInstruction) {
                return
            }

            fn(it)
        }
    }

    fun phis(fn: (Phi) -> Unit) {
        instructions.forEach {
            if (it !is Phi) {
                return // Assume that phi functions are in the beginning of bb.
            }

            fn(it)
        }
    }

    fun removeIf(filter: (Instruction) -> Boolean): Boolean {
        return instructions.removeIf { filter(it) }
    }

    fun kill(instruction: Instruction) {
        val removed = instructions.remove(instruction)
        removed.destroy()
    }

    override fun not(value: Value): Not {
        val valueType = value.type()
        require(valueType is IntegerType) {
            "should be integer type, but ty=$valueType"
        }

        return withOutput { Not.make(it, this, valueType, value) }
    }

    override fun neg(value: Value): Neg {
        val valueType = value.type()
        require(valueType is ArithmeticType) {
            "should be integer type, but ty=$valueType"
        }

        return withOutput { Neg.make(it, this, valueType, value) }
    }

    override fun arithmeticBinary(a: Value, op: ArithmeticBinaryOp, b: Value): ArithmeticBinary {
        val ty = a.type()
        require(ty is ArithmeticType) {
            "should be arithmetic type, but ty=$ty"
        }

        return withOutput { ArithmeticBinary.make(it, this, ty, a, op, b) }
    }

    override fun icmp(a: Value, predicate: IntPredicate, b: Value): SignedIntCompare {
        return withOutput { SignedIntCompare.make(it, this, a, predicate, b) }
    }

    override fun ucmp(a: Value, predicate: IntPredicate, b: Value): UnsignedIntCompare {
        return withOutput { UnsignedIntCompare.make(it, this, a, predicate, b) }
    }

    override fun pcmp(a: Value, predicate: IntPredicate, b: Value): PointerCompare {
        return withOutput { PointerCompare.make(it, this, a, predicate, b) }
    }

    override fun fcmp(a: Value, predicate: FloatPredicate, b: Value): FloatCompare {
        return withOutput { FloatCompare.make(it, this, a, predicate, b) }
    }

    override fun load(loadedType: PrimitiveType, ptr: Value): Load {
        return withOutput { Load.make(it, this, loadedType, ptr) }
    }

    override fun store(ptr: Value, value: Value) {
        val store = Store.make(this, ptr, value)
        append(store)
    }

    override fun call(func: AnyFunctionPrototype, args: List<Value>): Call {
        require(func.returnType() != Type.Void)
        return withOutput { Call.make(it, this, func, args) }
    }

    override fun vcall(func: AnyFunctionPrototype, args: List<Value>) {
        require(func.returnType() == Type.Void)
        append(VoidCall.make(this, func, args))
    }

    override fun icall(pointer: Value, func: IndirectFunctionPrototype, args: List<Value>): IndirectionCall {
        require(func.returnType() != Type.Void)
        return withOutput { IndirectionCall.make(it, this, pointer, func, args) }
    }

    override fun ivcall(pointer: Value, func: IndirectFunctionPrototype, args: List<Value>) {
        require(func.returnType() == Type.Void)
        append(IndirectionVoidCall.make(this, pointer, func, args))
    }

    override fun branch(target: Block) {
        addTerminate(Branch.make(this, target))
    }

    override fun branchCond(value: Value, onTrue: Block, onFalse: Block) {
        addTerminate(BranchCond.make(this, value, onTrue, onFalse))
    }

    override fun alloc(ty: NonTrivialType): Alloc {
        return withOutput { it: Int -> Alloc.make(it, this, ty) }
    }

    override fun ret(value: Value) {
        addTerminate(ReturnValue.make(this, value))
    }

    override fun retVoid() {
        addTerminate(ReturnVoid.make(this))
    }

    override fun gep(source: Value, elementType: PrimitiveType, index: Value): GetElementPtr {
        return withOutput { GetElementPtr.make(it, this, elementType, source, index) }
    }

    override fun gfp(source: Value, ty: AggregateType, index: IntegerConstant): GetFieldPtr {
        return withOutput { GetFieldPtr.make(it, this, ty, source, index) }
    }

    override fun flag2int(value: Value, ty: IntegerType): Flag2Int {
        return withOutput { Flag2Int.make(it, this, ty, value) }
    }

    override fun int2fp(value: Value, ty: FloatingPointType): Int2Float {
        return withOutput { Int2Float.make(it, this, ty, value) }
    }

    override fun bitcast(value: Value, ty: PrimitiveType): Bitcast {
        return withOutput { Bitcast.make(it, this, ty, value) }
    }

    override fun zext(value: Value, toType: UnsignedIntType): ZeroExtend {
        return withOutput { ZeroExtend.make(it, this, toType, value) }
    }

    override fun sext(value: Value, toType: SignedIntType): SignExtend {
        return withOutput { SignExtend.make(it, this, toType, value) }
    }

    override fun trunc(value: Value, toType: IntegerType): Truncate {
        return withOutput { Truncate.make(it, this, toType, value) }
    }

    override fun fptrunc(value: Value, toType: FloatingPointType): FpTruncate {
        return withOutput { FpTruncate.make(it, this, toType, value) }
    }

    override fun fpext(value: Value, toType: FloatingPointType): FpExtend {
        return withOutput { FpExtend.make(it, this, toType, value) }
    }

    override fun fp2Int(value: Value, toType: IntegerType): FloatToInt {
        return withOutput { FloatToInt.make(it,  this, toType, value) }
    }

    override fun select(cond: Value, type: PrimitiveType, onTrue: Value, onFalse: Value): Select {
        return withOutput { Select.make(it, this, type, cond, onTrue, onFalse) }
    }

    override fun phi(incoming: List<Value>, labels: List<Label>): Phi {
        val bbs = labels.map { it as Block }
        return withOutput { Phi.make(it, this, incoming[0].type() as PrimitiveType, bbs, incoming.toTypedArray()) }
    }

    override fun int2ptr(value: Value): Int2Pointer {
        return withOutput { Int2Pointer.make(it, this, value) }
    }

    override fun ptr2int(value: Value, toType: IntegerType): Pointer2Int {
        return withOutput { Pointer2Int.make(it, this, toType, value) }
    }

    override fun memcpy(dst: Value, src: Value, length: UnsignedIntegerConstant) {
        append(Memcpy.make(this, dst, src, length))
    }

    override fun downStackFrame(callable: Callable) {
        append(DownStackFrame(this, callable))
    }

    override fun upStackFrame(callable: Callable) {
        append(UpStackFrame(this, callable))
    }

    override fun uncompletedPhi(ty: PrimitiveType, incoming: Value): Phi {
        val blocks = predecessors()
        return withOutput { Phi.makeUncompleted(it, this, ty, incoming, blocks) }
    }

    override fun gen(ty: NonTrivialType): Generate {
        return withOutput { Generate.make(it, this, ty) }
    }

    override fun lea(generate: Value): Lea {
        return withOutput { Lea.make(it, this, generate) }
    }

    fun uncompletedPhi(incomingType: PrimitiveType, incoming: List<Value>, labels: List<Block>): Phi {
        return withOutput { Phi.make(it, this, incomingType, labels, incoming.toTypedArray()) }
    }

    override fun copy(value: Value): Copy {
        return withOutput { Copy.make(it, this, value) }
    }

    override fun move(dst: Generate, fromValue: Value) {
        append(Move.make(this, dst, fromValue))
    }

    override fun move(dst: Value, base: Value, index: Value) {
        append(MoveByIndex.make(this, dst, base, index))
    }

    override fun indexedLoad(origin: Value, loadedType: PrimitiveType, index: Value): IndexedLoad {
        return withOutput { IndexedLoad.make(it, this, loadedType, origin, index) }
    }

    override fun storeOnStack(destination: Value, index: Value, source: Value) {
        append(StoreOnStack.make(this, destination, index, source))
    }

    override fun loadFromStack(origin: Value, loadedType: PrimitiveType, index: Value): LoadFromStack {
        return withOutput { LoadFromStack.make(it, this, loadedType, origin, index) }
    }

    override fun leaStack(origin: Value, loadedType: PrimitiveType, index: Value): LeaStack {
        return withOutput { LeaStack.make(it, this, loadedType, origin, index) }
    }

    private fun addTerminate(instruction: TerminateInstruction) {
        fun makeEdge(to: Block) {
            addSuccessor(to)
            to.addPredecessor(this)
        }

        instruction.targets().forEach { makeEdge(it) }
        append(instruction)
    }

    private fun n(i: Int): String {
        return "${index}x$i"
    }

    private fun allocateValue(): Int {
        val currentValue = instructionIndex
        instructionIndex += 1
        return currentValue
    }

    private inline fun<reified T: ValueInstruction> withOutput(f: (Int) -> T): T {
        val value = allocateValue()
        val instruction = f(value)

        append(instruction)
        return instruction
    }

    private fun append(instruction: Instruction) {
        insertionStrategy.insert(instruction)
        insertionStrategy = InsertAfter(instructions.last())
    }

    override fun toString(): String {
        return if (index == 0) {
            "entry"
        } else {
            "L$index"
        }
    }

    companion object {
        fun insertBlock(block: Block, newBlock: Block, predecessor: Block) {
            predecessor.updateSuccessor(block, newBlock)
            block.removePredecessors(predecessor)
        }

        fun empty(blockIndex: Int): Block {
            return Block(blockIndex)
        }
    }
}

private class InstructionList: LeakedLinkedList<Instruction>()