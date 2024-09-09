package types

import gen.VarStack


class TypeHolder(private val valueMap: VarStack<VarDescriptor>): Scope {
    private val typeMap = VarStack<BaseType>()//TODO separate holder for struct, enum, union.
    private val functions = hashMapOf<String, VarDescriptor>()
    private val typedefs = VarStack<TypeDesc>()

    operator fun get(varName: String): VarDescriptor {
        return valueMap[varName] ?: functions[varName] ?: throw Exception("Type for variable '$varName' not found")
    }

    fun getTypeOrNull(name: String): BaseType? {
        return typeMap[name]
    }

    private fun getTypedefOrNull(name: String): TypeDesc? {
        return typedefs[name]
    }

    fun getTypedef(name: String): TypeDesc {
        return getTypedefOrNull(name) ?: throw Exception("Type for 'typedef $name' not found")
    }

    fun addTypedef(name: String, type: TypeDesc): TypeDesc {
        typedefs[name] = type
        return type
    }

    fun addVar(name: String, type: VarDescriptor): VarDescriptor {
        valueMap[name] = type
        return type
    }

    fun containsVar(varName: String): Boolean {
        return valueMap.containsKey(varName)
    }

    fun getStructType(name: String): BaseType {
        return getTypeOrNull(name) ?: throw Exception("Type for struct $name not found")
    }

    fun <T : BaseType> addNewType(name: String, type: T): T {
        typeMap[name] = type
        return type
    }

    fun getFunctionType(name: String): VarDescriptor {
        return functions[name] ?: valueMap[name] ?: throw Exception("Type for function '$name' not found")
    }

    fun addFunctionType(name: String, type: VarDescriptor): VarDescriptor {
        functions[name] = type
        return type
    }

    override fun enter() {
        typeMap.enter()
        valueMap.enter()
    }

    override fun leave() {
        typeMap.leave()
        valueMap.leave()
    }

    companion object {
        fun default(): TypeHolder {
            return TypeHolder(VarStack<VarDescriptor>())
        }
    }
}