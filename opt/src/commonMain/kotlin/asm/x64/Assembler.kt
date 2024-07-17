package asm.x64

data class ObjFunctionCreationException(override val message: String): Exception(message)

private data class BuilderContext(var label: Label, var instructions: InstructionList)

private class InstructionList {
    private val list = arrayListOf<CPUInstruction>()

    fun add(inst: CPUInstruction) = list.add(inst)

    fun joinTo(builder: StringBuilder, prefix: String, separator: String) {
        // GNU as requires a newline after the last instruction
        list.joinTo(builder, separator, prefix, postfix = "\n")
    }
}


// X86 and amd64 instruction reference
// https://www.felixcloutier.com/x86/
abstract class Assembler(private val name: String) {
    private val codeBlocks: LinkedHashMap<Label, InstructionList>
    private val activeContext: BuilderContext

    init {
        val label = Label(name)
        val instructions = InstructionList()

        codeBlocks = linkedMapOf(Pair(label, instructions))
        activeContext = BuilderContext(label, instructions)
    }

    fun name(): String {
        return name
    }

    private fun add(inst: CPUInstruction) {
        activeContext.instructions.add(inst)
    }

    fun label(name: String) {
        val newLabel = Label(name)
        if (codeBlocks[newLabel] != null) {
            throw ObjFunctionCreationException("Label with name '$name' already exist")
        }

        val newInstructions = InstructionList()
        codeBlocks[newLabel] = newInstructions
        activeContext.label = newLabel
        activeContext.instructions = newInstructions
    }

    // Load Effective Address
    fun lea(size: Int, src: Address, dst: GPRegister) = add(Lea(size, src, dst))

    fun add(size: Int, first: GPRegister, destination: GPRegister) = add(Add(size, first, destination))
    fun add(size: Int, first: Imm32, destination: GPRegister)      = add(Add(size, first, destination))
    fun add(size: Int, first: GPRegister, destination: Address)    = add(Add(size, first, destination))
    fun add(size: Int, first: Address, destination: GPRegister)    = add(Add(size, first, destination))
    fun add(size: Int, first: Imm32, destination: Address)         = add(Add(size, first, destination)) //TODO Use THIS

    fun sub(size: Int, first: GPRegister, destination: GPRegister) = add(Sub(size, first, destination))
    fun sub(size: Int, first: Imm32, destination: GPRegister)      = add(Sub(size, first, destination))
    fun sub(size: Int, first: GPRegister, destination: Address)    = add(Sub(size, first, destination))
    fun sub(size: Int, first: Address, destination: GPRegister)    = add(Sub(size, first, destination))
    fun sub(size: Int, first: Imm32, destination: Address)         = add(Sub(size, first, destination))

    fun mul(size: Int, src: GPRegister, dst: GPRegister)              = add(iMull(size, null, src, dst))
    fun mul(size: Int, src: Imm32, dst: GPRegister)                   = add(iMull(size, null, src, dst))
    fun mul(size: Int, src: Address, dst: GPRegister)                 = add(iMull(size, null, src, dst))
    fun mul(size: Int, src1: Imm32, src: GPRegister, dst: GPRegister) = add(iMull(size, src1, src, dst))
    fun mul(size: Int, src1: Imm32, src: Address, dst: GPRegister)    = add(iMull(size, src1, src, dst))

    fun xor(size: Int, src: Address, dst: GPRegister)    = add(Xor(size, src, dst))
    fun xor(size: Int, imm32: Imm32, dst: Address)       = add(Xor(size, imm32, dst))
    fun xor(size: Int, src: GPRegister, dst: GPRegister) = add(Xor(size, src, dst))
    fun xor(size: Int, imm32: Imm32, dst: GPRegister)    = add(Xor(size, imm32, dst))
    fun xor(size: Int, src: GPRegister, dst: Address)    = add(Xor(size, src, dst))

    // Logical AND
    fun and(size: Int, src: Address, dst: GPRegister)    = add(And(size, src, dst))
    fun and(size: Int, imm32: Imm32, dst: Address)       = add(And(size, imm32, dst))
    fun and(size: Int, src: GPRegister, dst: GPRegister) = add(And(size, src, dst))
    fun and(size: Int, imm32: Imm32, dst: GPRegister)    = add(And(size, imm32, dst))
    fun and(size: Int, src: GPRegister, dst: Address)    = add(And(size, src, dst))

    // Logical OR
    fun or(size: Int, src: Address, dst: GPRegister)    = add(Or(size, src, dst))
    fun or(size: Int, imm32: Imm32, dst: Address)       = add(Or(size, imm32, dst))
    fun or(size: Int, src: GPRegister, dst: GPRegister) = add(Or(size, src, dst))
    fun or(size: Int, imm32: Imm32, dst: GPRegister)    = add(Or(size, imm32, dst))
    fun or(size: Int, src: GPRegister, dst: Address)    = add(Or(size, src, dst))

    // Unsigned logical Shift Right
    fun shr(size: Int, src: GPRegister, dst: GPRegister) = add(Shr(size, src, dst))
    fun shr(size: Int, src: Imm32, dst: GPRegister)      = add(Shr(size, src, dst))
    fun shr(size: Int, src: GPRegister, dst: Address)    = add(Shr(size, src, dst))
    fun shr(size: Int, src: Address, dst: GPRegister)    = add(Shr(size, src, dst))
    fun shr(size: Int, src: Imm32, dst: Address)         = add(Shr(size, src, dst))

    // Signed logical Shift Left
    fun sar(size: Int, src: GPRegister, dst: GPRegister) = add(Sar(size, src, dst))
    fun sar(size: Int, src: Imm32, dst: GPRegister)      = add(Sar(size, src, dst))
    fun sar(size: Int, src: GPRegister, dst: Address)    = add(Sar(size, src, dst))
    fun sar(size: Int, src: Address, dst: GPRegister)    = add(Sar(size, src, dst))
    fun sar(size: Int, src: Imm32, dst: Address)         = add(Sar(size, src, dst))

    // Unsigned logical Shift Left
    fun shl(size: Int, src: GPRegister, dst: GPRegister) = add(Shl(size, src, dst))
    fun shl(size: Int, src: Imm32, dst: GPRegister)      = add(Shl(size, src, dst))
    fun shl(size: Int, src: GPRegister, dst: Address)    = add(Shl(size, src, dst))
    fun shl(size: Int, src: Address, dst: GPRegister)    = add(Shl(size, src, dst))
    fun shl(size: Int, src: Imm32, dst: Address)         = add(Shl(size, src, dst))

    // Signed logical Shift Right
    fun sal(size: Int, src: GPRegister, dst: GPRegister) = add(Sal(size, src, dst))
    fun sal(size: Int, src: Imm32, dst: GPRegister)      = add(Sal(size, src, dst))
    fun sal(size: Int, src: GPRegister, dst: Address)    = add(Sal(size, src, dst))
    fun sal(size: Int, src: Address, dst: GPRegister)    = add(Sal(size, src, dst))
    fun sal(size: Int, src: Imm32, dst: Address)         = add(Sal(size, src, dst))

    fun neg(size: Int, dst: GPRegister) = add(Neg(size, dst))
    fun neg(size: Int, dst: Address)    = add(Neg(size, dst))

    fun not(size: Int, dst: GPRegister) = add(Not(size, dst))
    fun not(size: Int, dst: Address)    = add(Not(size, dst))

    // Unsigned Divide
    fun div(size: Int, divider: GPRegister) = add(Div(size, divider))
    fun div(size: Int, divider: Address)    = add(Div(size, divider))

    // Signed Divide
    fun idiv(size: Int, divider: GPRegister) = add(Idiv(size, divider))
    fun idiv(size: Int, divider: Address)    = add(Idiv(size, divider))

    // Convert Word to Doubleword/Convert Doubleword to Quadword
    fun cdq(size: Int) = when (size) {
        8, 4, 2, 1 -> add(Convert(size))
        else -> throw IllegalArgumentException("size=$size")
    }

    fun xor(size: Int, first: Operand, destination: GPRegister): Operand {
        add(Xor(size, first, destination))
        return destination
    }

    fun test(size: Int, first: GPRegister, second: Operand) {
        add(Test(size, first, second))
    }

    fun setcc(size: Int, tp: SetCCType, reg: GPRegister) {
        add(SetCc(size, tp, reg))
    }

    fun setcc(size: Int, tp: SetCCType, addr: Address) {
        add(SetCc(size, tp, addr))
    }

    fun push(size: Int, reg: GPRegister) = add(Push(size, reg))

    fun push(size: Int, imm: Imm32) = add(Push(size, imm))

    fun pop(size: Int, toReg: GPRegister) {
        add(Pop(size, toReg))
    }

    fun <T: Operand> movOld(size: Int, src: Operand, des: T): T {
        add(Mov(size, src, des))
        return des
    }

    fun mov(size: Int, src: GPRegister, dst: GPRegister) = add(Mov(size, src, dst))
    fun mov(size: Int, src: Address, dst: GPRegister)    = add(Mov(size, src, dst))
    fun mov(size: Int, src: GPRegister, dst: Address)    = add(Mov(size, src, dst))
    fun mov(size: Int, imm: ImmInt, dst: Address)        = add(Mov(size, imm, dst))
    fun mov(size: Int, imm: ImmInt, dst: GPRegister)     = add(Mov(size, imm, dst))

    // Move With Sign-Extension
    private fun movsx(fromSize: Int, toSize: Int, src: GPRegister, dst: GPRegister)  = add(Movsx(fromSize, toSize, src, dst))
    private fun movsx(fromSize: Int, toSize: Int, src: Address, dst: GPRegister)     = add(Movsx(fromSize, toSize, src, dst))
    private fun movsxd(fromSize: Int, toSize: Int, src: GPRegister, dst: GPRegister) = add(Movsxd(fromSize, toSize, src, dst))
    private fun movsxd(fromSize: Int, toSize: Int, src: Address, dst: GPRegister)    = add(Movsxd(fromSize, toSize, src, dst))

    fun movsext(fromSize: Int, toSize: Int, src: GPRegister, dst: GPRegister) = when (fromSize) {
        8, 4 -> movsxd(fromSize, toSize, src, dst)
        2, 1 -> movsx(fromSize, toSize, src, dst)
        else -> throw IllegalArgumentException("fromSize=$fromSize, toSize=$toSize, src=$src, dst=$dst")
    }

    fun movsext(fromSize: Int, toSize: Int, src: Address, dst: GPRegister) = when (fromSize) {
        8, 4 -> movsxd(fromSize, toSize, src, dst)
        2, 1 -> movsx(fromSize, toSize, src, dst)
        else -> throw IllegalArgumentException("fromSize=$fromSize, toSize=$toSize, src=$src, dst=$dst")
    }

    // Move With Zero-Extend
    private fun movzx(fromSize: Int, toSize: Int, src: GPRegister, dst: GPRegister) = add(Movzx(fromSize, toSize, src, dst))
    private fun movzx(fromSize: Int, toSize: Int, src: Address, dst: GPRegister)    = add(Movzx(fromSize, toSize, src, dst))

    fun movzext(fromSize: Int, toSize: Int, src: GPRegister, dst: GPRegister) = when (fromSize) {
        8, 4, 2, 1 -> movzx(fromSize, toSize, src, dst)
        else -> throw IllegalArgumentException("fromSize=$fromSize, toSize=$toSize, src=$src, dst=$dst")
    }

    fun movzext(fromSize: Int, toSize: Int, src: Address, dst: GPRegister) = when (fromSize) {
        8, 4, 2, 1 -> movzx(fromSize, toSize, src, dst)
        else -> throw IllegalArgumentException("fromSize=$fromSize, toSize=$toSize, src=$src, dst=$dst")
    }

    // Conditional Move
    fun cmovcc(size: Int, flag: CMoveFlag, src: Address, dst: GPRegister) = when (size) {
        2, 4, 8 -> add(CMOVcc(size, flag, src, dst))
        else -> throw IllegalArgumentException("size=$size, flag=$flag, src=$src, dst=$dst")
    }

    fun cmovcc(size: Int, flag: CMoveFlag, src: GPRegister, dst: GPRegister) = when (size) {
        2, 4, 8 -> add(CMOVcc(size, flag, src, dst))
        else -> throw IllegalArgumentException("size=$size, flag=$flag, src=$src, dst=$dst")
    }

    // Call Procedure
    fun call(name: String)    = add(Call(name))
    fun call(reg: GPRegister) = add(Call(reg))
    fun call(reg: Address)    = add(Call(reg))

    fun cmp(size: Int, first: Operand, second: Operand) = add(Cmp(size, first, second))

    fun jcc(jmpType: CondType, label: String) = add(Jcc(jmpType, label))
    fun jump(label: String) = add(Jump(label))

    fun ret() = add(Ret)
    fun leave() = add(Leave)

    // Add Scalar Double-Precision Floating-Point Values
    fun addsd(src: Address, dst: XmmRegister) = add(Addsd(16, src, dst))
    fun addsd(src: XmmRegister, dst: XmmRegister) = add(Addsd(16, src, dst))

    // Add Scalar Single-Precision Floating-Point Values
    fun addss(src: Address, dst: XmmRegister) = add(Addss(16, src, dst))
    fun addss(src: XmmRegister, dst: XmmRegister) = add(Addss(16, src, dst))

    fun addf(size: Int, src: Address, dst: XmmRegister) = when (size) {
        4 -> addss(src, dst)
        8 -> addsd(src, dst)
        else -> throw IllegalArgumentException("size=$size, src=$src, dst=$dst")
    }
    fun addf(size: Int, src: XmmRegister, dst: XmmRegister) = when (size) {
        4 -> addss(src, dst)
        8 -> addsd(src, dst)
        else -> throw IllegalArgumentException("size=$size, src=$src, dst=$dst")
    }

    // Subtract Scalar Single-Precision Floating-Point Values
    fun subss(src: Address, dst: XmmRegister) = add(Subss(16, src, dst))
    fun subss(src: XmmRegister, dst: XmmRegister) = add(Subss(16, src, dst))

    // Subtract Scalar Double-Precision Floating-Point Values
    fun subsd(src: Address, dst: XmmRegister) = add(Subsd(16, src, dst))
    fun subsd(src: XmmRegister, dst: XmmRegister) = add(Subsd(16, src, dst))

    fun subf(size: Int, src: Address, dst: XmmRegister) = when (size) {
        4 -> subss(src, dst)
        8 -> subsd(src, dst)
        else -> throw IllegalArgumentException("size=$size, src=$src, dst=$dst")
    }
    fun subf(size: Int, src: XmmRegister, dst: XmmRegister) = when (size) {
        4 -> subss(src, dst)
        8 -> subsd(src, dst)
        else -> throw IllegalArgumentException("size=$size, src=$src, dst=$dst")
    }

    // Multiply Scalar Single-Precision Floating-Point Values
    fun mulss(src: Address, dst: XmmRegister) = add(Mulss(16, src, dst))
    fun mulss(src: XmmRegister, dst: XmmRegister) = add(Mulss(16, src, dst))

    // Multiply Scalar Double-Precision Floating-Point Values
    fun mulsd(src: Address, dst: XmmRegister) = add(Mulsd(16, src, dst))
    fun mulsd(src: XmmRegister, dst: XmmRegister) = add(Mulsd(16, src, dst))

    fun mulf(size: Int, src: Address, dst: XmmRegister) = when (size) {
        4 -> mulss(src, dst)
        8 -> mulsd(src, dst)
        else -> throw IllegalArgumentException("size=$size, src=$src, dst=$dst")
    }
    fun mulf(size: Int, src: XmmRegister, dst: XmmRegister) = when (size) {
        4 -> mulss(src, dst)
        8 -> mulsd(src, dst)
        else -> throw IllegalArgumentException("size=$size, src=$src, dst=$dst")
    }

    // Divide Scalar Single-Precision Floating-Point Values
    fun divss(src: Address, dst: XmmRegister) = add(Divss(16, src, dst))
    fun divss(src: XmmRegister, dst: XmmRegister) = add(Divss(16, src, dst))

    // Divide Scalar Double-Precision Floating-Point Values
    fun divsd(src: Address, dst: XmmRegister) = add(Divsd(16, src, dst))
    fun divsd(src: XmmRegister, dst: XmmRegister) = add(Divsd(16, src, dst))

    fun divf(size: Int, src: Address, dst: XmmRegister) = when (size) {
        4 -> divss(src, dst)
        8 -> divsd(src, dst)
        else -> throw IllegalArgumentException("size=$size, src=$src, dst=$dst")
    }
    fun divf(size: Int, src: XmmRegister, dst: XmmRegister) = when (size) {
        4 -> divss(src, dst)
        8 -> divsd(src, dst)
        else -> throw IllegalArgumentException("size=$size, src=$src, dst=$dst")
    }

    // Move Scalar Single-Precision Floating-Point Values
    private fun movss(src: Address, dst: XmmRegister) = add(Movss(16, src, dst))
    private fun movss(src: XmmRegister, dst: XmmRegister) = add(Movss(16, src, dst))
    private fun movss(src: XmmRegister, dst: Address) = add(Movss(16, src, dst))

    // Move Scalar Double-Precision Floating-Point Values
    private fun movsd(src: Address, dst: XmmRegister) = add(Movsd(16, src, dst))
    private fun movsd(src: XmmRegister, dst: XmmRegister) = add(Movsd(16, src, dst))
    private fun movsd(src: XmmRegister, dst: Address) = add(Movsd(16, src, dst))

    fun movf(size: Int, src: XmmRegister, dst: XmmRegister) = when (size) {
        4 -> movss(src, dst)
        8 -> movsd(src, dst)
        else -> throw IllegalArgumentException("size=$size, src=$src, dst=$dst")
    }
    fun movf(size: Int, src: Address, dst: XmmRegister) = when (size) {
        4 -> movss(src, dst)
        8 -> movsd(src, dst)
        else -> throw IllegalArgumentException("size=$size, src=$src, dst=$dst")
    }
    fun movf(size: Int, src: XmmRegister, dst: Address) = when (size) {
        4 -> movss(src, dst)
        8 -> movsd(src, dst)
        else -> throw IllegalArgumentException("size=$size, src=$src, dst=$dst")
    }

    // Bitwise Logical XOR of Packed Floating-Point Values
    fun xorpd(src: XmmRegister, dst: XmmRegister) = add(Xorpd(16, src, dst))
    fun xorpd(src: Address, dst: XmmRegister) = add(Xorpd(16, src, dst))
    fun xorps(src: XmmRegister, dst: XmmRegister) = add(Xorps(16, src, dst))
    fun xorps(src: Address, dst: XmmRegister) = add(Xorps(16, src, dst))
    fun xorpf(size: Int, src: Address, dst: XmmRegister) = when (size) {
        4 -> xorps(src, dst)
        8 -> xorpd(src, dst)
        else -> throw IllegalArgumentException("size=$size, src=$src, dst=$dst")
    }
    fun xorpf(size: Int, src: XmmRegister, dst: XmmRegister) = when (size) {
        4 -> xorps(src, dst)
        8 -> xorpd(src, dst)
        else -> throw IllegalArgumentException("size=$size, src=$src, dst=$dst")
    }

    // Unordered Compare Scalar Single-Precision Floating-Point Values and set EFLAGS
    fun ucomiss(src: Address, dst: XmmRegister) = add(Ucomiss(16, src, dst))
    fun ucomiss(src: XmmRegister, dst: XmmRegister) = add(Ucomiss(16, src, dst))

    // Unordered Compare Scalar Double-Precision Floating-Point Values and set EFLAGS
    fun ucomisd(src: Address, dst: XmmRegister) = add(Ucomisd(16, src, dst))
    fun ucomisd(src: XmmRegister, dst: XmmRegister) = add(Ucomisd(16, src, dst))

    fun cmpf(size: Int, src: Address, dst: XmmRegister) = when (size) {
        4 -> ucomiss(src, dst)
        8 -> ucomisd(src, dst)
        else -> throw IllegalArgumentException("size=$size, src=$src, dst=$dst")
    }

    fun cmpf(size: Int, src: XmmRegister, dst: XmmRegister) = when (size) {
        4 -> ucomiss(src, dst)
        8 -> ucomisd(src, dst)
        else -> throw IllegalArgumentException("size=$size, src=$src, dst=$dst")
    }

    // Convert Scalar Double-Precision Floating-Point Value to Scalar Single-Precision Floating-Point Value
    fun cvtsd2ss(src: XmmRegister, dst: XmmRegister) = add(Cvtsd2ss(src, dst))
    fun cvtsd2ss(src: Address, dst: XmmRegister) = add(Cvtsd2ss(src, dst))

    // Convert Scalar Single-Precision Floating-Point Value to Scalar Double-Precision Floating-Point Value
    fun cvtss2sd(src: XmmRegister, dst: XmmRegister) = add(Cvtss2sd(src, dst))
    fun cvtss2sd(src: Address, dst: XmmRegister) = add(Cvtss2sd(src, dst))

    // Convert Scalar Single Precision Floating-Point Value to Doubleword Integer
    private fun cvtss2si(toSize: Int, src: XmmRegister, dst: GPRegister) = when (toSize) {
        8, 4 -> add(Cvtss2si(toSize, src, dst))
        else -> throw IllegalArgumentException("toSize=$toSize, src=$src, dst=$dst")
    }

    private fun cvtss2si(toSize: Int, src: Address, dst: GPRegister) = when (toSize) {
        8, 4 -> add(Cvtss2si(toSize, src, dst))
        else -> throw IllegalArgumentException("toSize=$toSize, src=$src, dst=$dst")
    }

    // Convert Double Single Precision Floating-Point Value to Doubleword Integer
    private fun cvtsd2si(toSize: Int, src: XmmRegister, dst: GPRegister) = when (toSize) {
        8, 4 -> add(Cvtsd2si(toSize, src, dst))
        else -> throw IllegalArgumentException("toSize=$toSize, src=$src, dst=$dst")
    }

    private fun cvtsd2si(toSize: Int, src: Address, dst: GPRegister) = when (toSize) {
        8, 4 -> add(Cvtsd2si(toSize, src, dst))
        else -> throw IllegalArgumentException("toSize=$toSize, src=$src, dst=$dst")
    }

    fun cvtfp2int(toSize: Int, fromSize: Int, src: Address, dst: GPRegister) = when (fromSize) {
        8 -> cvtsd2si(toSize, src, dst)
        4 -> cvtss2si(toSize, src, dst)
        else -> throw IllegalArgumentException("toSize=$toSize, src=$src, dst=$dst")
    }

    fun cvtfp2int(toSize: Int, fromSize: Int, src: XmmRegister, dst: GPRegister) = when (fromSize) {
        8 -> cvtsd2si(toSize, src, dst)
        4 -> cvtss2si(toSize, src, dst)
        else -> throw IllegalArgumentException("toSize=$toSize, src=$src, dst=$dst")
    }

    // Convert Doubleword Integer to Scalar Single Precision Floating-Point Value
    private fun cvtsi2ss(fromSize: Int, dst: XmmRegister, src: GPRegister) = add(Cvtsi2ss(fromSize, src, dst))
    private fun cvtsi2ss(fromSize: Int, dst: XmmRegister, src: Address) = add(Cvtsi2ss(fromSize, src, dst))

    // Convert Doubleword Integer to Scalar Double Precision Floating-Point Value
    private fun cvtsi2sd(fromSize: Int, src: GPRegister, dst: XmmRegister) = add(Cvtsi2sd(fromSize, src, dst))
    private fun cvtsi2sd(fromSize: Int, src: Address, dst: XmmRegister) = add(Cvtsi2sd(fromSize, src, dst))

    fun cvtint2fp(fromSize: Int, toSize: Int, src: GPRegister, dst: XmmRegister) = when (toSize) {
        8 -> cvtsi2sd(fromSize, src, dst)
        4 -> cvtsi2ss(fromSize, dst, src)
        else -> throw IllegalArgumentException("toSize=$toSize, src=$src, dst=$dst")
    }

    fun cvtint2fp(fromSize: Int, toSize: Int, src: Address, dst: XmmRegister) = when (toSize) {
        8 -> cvtsi2sd(fromSize, src, dst)
        4 -> cvtsi2ss(fromSize, dst, src)
        else -> throw IllegalArgumentException("toSize=$toSize, src=$src, dst=$dst")
    }

    fun comment(message: String) = add(Comment(message))

    override fun toString(): String {
        val builder = StringBuilder()
        var count = 0
        for ((label, instructions) in codeBlocks) {
            builder.append("$label:\n")
            instructions.joinTo(builder, "    ", "\n    ")

            if (count < codeBlocks.size - 1) {
                builder.append('\n')
            }
            count += 1
        }

        return builder.toString()
    }
}