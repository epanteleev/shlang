package ir.pass.analysis

import ir.value.*
import ir.instruction.*
import ir.module.FunctionData
import ir.pass.AnalysisResult
import ir.pass.FunctionAnalysisPass
import ir.pass.FunctionAnalysisPassFabric


enum class EscapeState {
    Local,    // Local means the value is local to the function
    Field,    // Field means the value is a field of a local value
    Argument, // Argument means the value is passed as an argument
    Constant, // Constant means the value is a constant
    Unknown;  // Unknown means the value is unknown

    fun union(other: EscapeState): EscapeState {
        if (this == Unknown || other == Unknown) {
            return Unknown
        }
        if (this == other) {
            return this
        }
        return when {
            this == Argument || other == Local -> Argument
            this == Local || other == Argument -> Argument
            this == Field || other == Local -> Field
            this == Local || other == Field -> Field
            else -> throw IllegalStateException("Cannot union $this and $other")
        }
    }
}

// Escape analysis pass
// A simple escape analysis pass that determines whether a value escapes the function
// The pass is based on the following rules:
// - If a value is allocated in the function, it is local
// - If a value is stored in the function, it is local
// - If a value is loaded in the function, it is local if the pointer is local
// - If a value is passed as an argument, it is an argument
// - If a value is a constant, it is a constant
// - Otherwise, the value is unknown
class EscapeAnalysis internal constructor(private val functionData: FunctionData): FunctionAnalysisPass<EscapeAnalysisResult>() {
    private val escapeState = hashMapOf<Value, EscapeState>()

    private fun union(operand: Value, newState: EscapeState): EscapeState {
        val state = escapeState[operand] ?: EscapeState.Unknown
        return state.union(newState)
    }

    private fun visitAlloc(alloc: Alloc) {
        escapeState[alloc] = EscapeState.Local
    }

    private fun visitStore(store: Store) {
        escapeState[store.pointer().asValue()] = union(store.pointer(), EscapeState.Local)
        when (val value = store.value()) {
            is Constant   -> escapeState[value] = EscapeState.Constant
            is LocalValue -> escapeState[value] = union(value, EscapeState.Field)
        }
    }

    private fun visitLoad(load: Load) {
        val operand = load.operand()
        if (operand is LocalValue) {
            escapeState[load] = union(operand, EscapeState.Field)
        }
    }

    private fun visitPointer2Int(pointer2Int: Pointer2Int) {
        escapeState[pointer2Int.value()] = union(pointer2Int.value(), EscapeState.Unknown)
    }

    private fun visitCall(call: Callable) {
        for (argument in call.arguments()) {
            escapeState[argument] = union(argument, EscapeState.Argument)
        }
    }

    override fun name(): String {
        return "EscapeAnalysis"
    }

    override fun run(): EscapeAnalysisResult {
        for (block in functionData.preorder()) {
            for (instruction in block) {
                when (instruction) {
                    is Alloc -> visitAlloc(instruction)
                    is Store -> visitStore(instruction)
                    is Load  -> visitLoad(instruction)
                    is Callable -> visitCall(instruction)
                    is Pointer2Int -> visitPointer2Int(instruction)
                }
            }
        }
        return EscapeAnalysisResult(escapeState)
    }
}

class EscapeAnalysisResult(private val escapeState: Map<Value, EscapeState>): AnalysisResult() {
    fun getEscapeState(value: Value): EscapeState {
        if (value is Constant) {
            return EscapeState.Constant
        }

        return escapeState[value] ?: EscapeState.Unknown
    }
}

object EscapeAnalysisPassFabric: FunctionAnalysisPassFabric<EscapeAnalysisResult>() {
    override fun create(functionData: FunctionData): EscapeAnalysis {
        return EscapeAnalysis(functionData)
    }
}