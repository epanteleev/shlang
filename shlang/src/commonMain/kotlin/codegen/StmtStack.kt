package codegen

import ir.instruction.Alloc
import ir.module.block.Label
import ir.module.builder.impl.FunctionDataBuilder
import ir.types.IntegerType
import ir.value.Value
import ir.value.constant.IntegerConstant


class StmtStack {
    private val stack = mutableListOf<StmtInfo>()

    private fun<T: StmtInfo> push(stmtInfo: T): T {
        stack.add(stmtInfo)
        return stmtInfo
    }

    fun<T: StmtInfo> scoped(stmtInfo: T, closure: (T) -> Unit) {
        push(stmtInfo)
        closure(stmtInfo)
        pop()
    }

    private fun pop() {
        stack.removeAt(stack.size - 1)
    }

    fun top(): StmtInfo {
        return stack[stack.size - 1]
    }

    fun root(): FunctionStmtInfo {
        return stack[0] as FunctionStmtInfo
    }

    fun topLoop(): AnyLoopStmtInfo? {
        return stack.findLast { it is AnyLoopStmtInfo } as AnyLoopStmtInfo?
    }

    fun topSwitchOrLoop(): StmtInfo? {
        return stack.findLast { it is AnyLoopStmtInfo || it is SwitchStmtInfo }
    }
}

sealed class StmtInfo {
    private var exitBB: Label? = null

    fun exit(): Label? {
        return exitBB
    }

    fun resolveExit(ir: FunctionDataBuilder): Label {
        if (exitBB == null) {
            exitBB = ir.createLabel()
        }

        return exitBB as Label
    }
}

class SwitchStmtInfo(val conditionType: IntegerType, val condBlock: Label, val table: MutableList<Label>, val values: MutableList<IntegerConstant>) : StmtInfo() {
    private var default: Label? = null

    fun resolveDefault(ir: FunctionDataBuilder): Label {
        if (default == null) {
            default = ir.createLabel()
        }

        return default as Label
    }

    fun default(): Label? {
        return default
    }
}

sealed class AnyLoopStmtInfo : StmtInfo() {
    private var conditionBB: Label? = null

    fun resolveCondition(ir: FunctionDataBuilder): Label {
        if (conditionBB == null) {
            conditionBB = ir.createLabel()
        }

        return conditionBB as Label
    }
}

class LoopStmtInfo : AnyLoopStmtInfo()

class ForLoopStmtInfo : AnyLoopStmtInfo() {
    private var updateBB: Label? = null

    fun resolveUpdate(ir: FunctionDataBuilder): Label {
        if (updateBB == null) {
            updateBB = ir.createLabel()
        }

        return updateBB as Label
    }

    fun update(): Label? {
        return updateBB
    }
}

class FunctionStmtInfo() : StmtInfo() {
    private var returnValueAdr: Value? = null
    var vaInit: Alloc? = null

    fun resolveReturnValueAdr(retValue: () -> Value): Value {
        if (returnValueAdr == null) {
            returnValueAdr = retValue()
        }

        return returnValueAdr as Value
    }

    fun returnValueAdr(): Value {
        return returnValueAdr as Value
    }
}