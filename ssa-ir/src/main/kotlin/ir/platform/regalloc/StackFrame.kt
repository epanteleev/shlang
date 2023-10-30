package ir.platform.regalloc

import asm.x64.*
import ir.instruction.Alloc
import ir.types.PrimitiveType
import ir.Value
import ir.instruction.ValueInstruction
import ir.types.Type

data class StackFrameException(override val message: String): Exception(message)

interface StackFrame {
    fun takeSlot(value: Value): Mem
    fun returnSlot(slot: Mem)
    fun size(): Long

    companion object {
        fun create(isBasePointerAddressed: Boolean = true): StackFrame {
            assert(isBasePointerAddressed) { "unsupported other approach" }
            return BasePointerAddressedStackFrame()
        }
    }
}

private class BasePointerAddressedStackFrame : StackFrame {
    private var frameSize: Long = 0
    private val freeStackSlots = linkedMapOf<Int, Mem>()

    private fun getTypeSize(ty: Type): Int {
        return if (ty != Type.U1) {
            ty.size()
        } else {
            1
        }
    }

    private fun withAlignment(alignment: Int, value: Long): Long {
        return ((value + (alignment * 2 - 1)) / alignment) * alignment
    }

    private fun stackSlotAlloc(value: Alloc): Mem {
        val typeSize = getTypeSize(value.type().dereference())

        frameSize = withAlignment(typeSize, frameSize)
        return Mem.mem(Rbp.rbp, -frameSize, typeSize)
    }

    /** Spilled value. */
    private fun valueInstructionAlloc(value: ValueInstruction): Mem {
        val typeSize = getTypeSize(value.type())

        val freeSlot = freeStackSlots[typeSize]
        if (freeSlot != null) {
            freeStackSlots.remove(typeSize)
            return freeSlot
        }

        frameSize = withAlignment(typeSize, frameSize)
        return Mem.mem(Rbp.rbp, -frameSize, typeSize)
    }

    override fun takeSlot(value: Value): Mem {
        return when (value) {
            is Alloc -> stackSlotAlloc(value)
            is ValueInstruction -> valueInstructionAlloc(value)
            else -> throw StackFrameException("Cannot alloc slot for this value=$value")
        }
    }

    override fun returnSlot(slot: Mem) {
        freeStackSlots[slot.size] = slot
    }

    override fun size(): Long {
        return frameSize
    }
}