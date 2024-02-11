package ir.utils

import ir.instruction.ValueInstruction
import ir.module.BasicBlocks

data class CreationInfoException(override val message: String): Exception(message)

class CreationInfo private constructor(private val creationInfo: MutableMap<ValueInstruction, Location>) {
    fun get(instruction: ValueInstruction): Location {
        return creationInfo[instruction] ?:
            throw CreationInfoException("value doesn't exist: instruction=$instruction")
    }

    companion object {
        fun create(basicBlocks: BasicBlocks): CreationInfo {
            val creationInfo = hashMapOf<ValueInstruction, Location>()

            for (bb in basicBlocks) {
                for ((idx, instruction) in bb.iterator().withIndex()) {
                    if (instruction !is ValueInstruction) {
                        continue
                    }

                    creationInfo[instruction] = Location(bb, idx)
                }
            }

            return CreationInfo(creationInfo)
        }
    }
}