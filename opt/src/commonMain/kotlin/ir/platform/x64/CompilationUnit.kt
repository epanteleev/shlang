package ir.platform.x64

import asm.x64.*
import ir.value.*
import ir.global.*
import ir.platform.common.CompiledModule
import ir.platform.x64.codegen.MacroAssembler


class CompilationUnit: CompiledModule() {
    private val functions = arrayListOf<MacroAssembler>()
    private val symbols = mutableSetOf<ObjSymbol>()

    fun mkFunction(name: String): MacroAssembler {
        val fn = MacroAssembler(name)
        functions.add(fn)
        return fn
    }

    fun mkConstant(globalValue: GlobalConstant) {
        when (globalValue) {
            is StringLiteralConstant -> {
                symbols.add(ObjSymbol(globalValue.name(), listOf(globalValue.data()), listOf(SymbolType.StringLiteral)))
            }
            is AggregateConstant -> {
                val types = globalValue.elements().map { convertToSymbolType(it) }
                val data  = globalValue.elements().map {
                    if (it is NullValue) {
                        return@map "0"
                    } else {
                        return@map it.toString()
                    }
                }
                symbols.add(ObjSymbol(globalValue.name(), data, types))
            }
            else -> symbols.add(ObjSymbol(globalValue.name(), listOf(globalValue.data()), convertToSymbolType(globalValue)))
        }
    }

    fun makeGlobal(globalValue: GlobalValue) {
        symbols.add(convertGlobalValueToSymbolType(globalValue))
    }

    private fun convertGlobalValueToSymbolType(globalValue: GlobalValue): ObjSymbol {
        if (globalValue.data is StringLiteralConstant) {
            return ObjSymbol(globalValue.name(), listOf(globalValue.data.name()), listOf(SymbolType.Quad))
        }

        val symbolType = convertToSymbolType(globalValue.data)
        val constant = globalValue.data
        if (constant is StructGlobalConstant || constant is ArrayGlobalConstant) {
            constant as AggregateConstant
            val data = constant.elements().map { it.toString() }
            return ObjSymbol(globalValue.name(), data, symbolType)
        } else {
            return ObjSymbol(globalValue.name(), listOf(globalValue.data()), symbolType)
        }
    }

    private fun convertToSymbolType(globalValue: GlobalConstant): List<SymbolType> {
        val symType = when (globalValue) {
            is StringLiteralConstant -> SymbolType.StringLiteral
            is I64ConstantValue -> SymbolType.Quad
            is U64ConstantValue -> SymbolType.Quad
            is I32ConstantValue -> SymbolType.Long
            is U32ConstantValue -> SymbolType.Long
            is I16ConstantValue -> SymbolType.Short
            is U16ConstantValue -> SymbolType.Short
            is I8ConstantValue  -> SymbolType.Byte
            is U8ConstantValue  -> SymbolType.Byte
            is F32ConstantValue -> SymbolType.Long
            is F64ConstantValue -> SymbolType.Quad
            is PointerConstant  -> SymbolType.Quad
            else -> null
        }
        if (symType != null) {
            return listOf(symType)
        }
        globalValue as AggregateConstant
        return globalValue.elements().map { convertToSymbolType(it) }
    }

    private fun convertToSymbolType(globalValue: Constant): SymbolType {
        return when (globalValue) {
            is I64Value -> SymbolType.Quad
            is U64Value -> SymbolType.Quad
            is I32Value -> SymbolType.Long
            is U32Value -> SymbolType.Long
            is I16Value -> SymbolType.Short
            is U16Value -> SymbolType.Short
            is I8Value  -> SymbolType.Byte
            is U8Value  -> SymbolType.Byte
            is F32Value -> SymbolType.Long
            is F64Value -> SymbolType.Quad
            is NullValue -> SymbolType.Quad
            else -> throw RuntimeException("unknown globals value: globalvalue=$globalValue:${globalValue.type()}")
        }
    }

    override fun toString(): String {
        val builder = StringBuilder()
        functions.forEach {
            builder.append(".global ${it.name()}\n")
        }
        builder.append('\n')

        if (symbols.isNotEmpty()) {
            builder.append(".data\n")
        }
        symbols.forEach {
            builder.append(it)
            builder.append('\n')
        }

        builder.append(".text\n")
        functions.joinTo(builder, separator = "\n")
        return builder.toString()
    }
}