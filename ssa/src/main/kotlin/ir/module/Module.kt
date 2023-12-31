package ir.module

import ir.*
import ir.module.auxiliary.Copy
import ir.module.auxiliary.DumpModule

data class ModuleException(override val message: String): Exception(message)

abstract class Module(internal open val functions: List<FunctionData>,
                      internal open val externFunctions: Set<ExternFunction>,
                      internal open val globals: Set<GlobalValue>) {
    val prototypes: List<AnyFunctionPrototype> by lazy {
        externFunctions.toList() + functions.map { it.prototype }
    }

    fun findFunction(prototype: FunctionPrototype): FunctionData {
        return functions.find { it.prototype == prototype }
            ?: throw ModuleException("Cannot find function: $prototype")
    }

    fun findGlobal(name: String): GlobalValue {
        return globals.find { it.name() == name } //TODO O(n)
            ?: throw ModuleException("Cannot find function: $name")
    }

    abstract fun copy(): Module

    fun functions(): Iterator<FunctionData> {
        return functions.iterator()
    }
}

data class SSAModule(override val functions: List<FunctionData>, override val externFunctions: Set<ExternFunction>, override val globals: Set<GlobalValue>):
    Module(functions, externFunctions, globals) {
    override fun copy(): Module {
        return SSAModule(functions.map { Copy.copy(it) }, externFunctions, globals)
    }

    override fun toString(): String {
        return DumpModule.dump(this)
    }
}