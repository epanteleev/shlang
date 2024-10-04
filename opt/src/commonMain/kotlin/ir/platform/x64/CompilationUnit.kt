package ir.platform.x64

import asm.x64.*
import ir.value.*
import ir.global.*
import ir.platform.common.CompiledModule
import ir.platform.x64.auxiliary.LinearizeInitializerList
import ir.types.*

// Using as
// The GNU Assembler
//
// https://ftp.gnu.org/old-gnu/Manuals/gas-2.9.1/html_node/as_toc.html
class CompilationUnit: CompiledModule, ObjModule(NameAssistant()) {

    private fun makeAggregateConstant(name: String, aggregateType: AggregateType, initializer: InitializerListValue): ObjLabel = label(name) {
        val linear = LinearizeInitializerList.linearize(initializer, aggregateType)
        for (e in linear) {
            primitive(this, e)
        }
    }

    fun mkConstant(globalValue: GlobalConstant): ObjLabel = when (globalValue) {
        is StringLiteralGlobalConstant -> {
            // name:
            //    .string "string"
            label(globalValue.name()) {
                string(globalValue.data())
            }
        }
        is AggregateGlobalConstant -> makeAggregateConstant(globalValue.name(), globalValue.contentType().asType(), globalValue.elements())
        is PrimitiveGlobalConstant -> makePrimitiveConstant(globalValue)
    }

    fun makeGlobal(globalValue: AnyGlobalValue) {
        if (globalValue is ExternValue) {
            return
        }
        convertGlobalValueToSymbolType(globalValue as GlobalValue)
    }

    private fun makeStringLiteralConstant(globalValue: GlobalValue, type: ArrayType, constant: StringLiteralConstant): ObjLabel {
        val values = LinearizeInitializerList.linearize(constant, type)
        if (values.size == type.length) {
            return label(globalValue.name()) {
                string(constant.data())
            }
        }
        if (values.isNotEmpty()) {
            throw IllegalArgumentException("string too long: $values")
        }
        val init = StringBuilder()

        for (j in 0 until type.length) {
            init.append("\\000")
        }

        return label(globalValue.name()) {
            ascii(init.toString())
        }
    }

    private fun makePrimitiveConstant(globalValue: GlobalValue): ObjLabel = when (val initializer = globalValue.initializer()) {
        is InitializerListValue -> anonConstant {
            for (e in LinearizeInitializerList.linearize(initializer, globalValue.contentType().asType())) {
                primitive(this, e)
            }
        }
        is StringLiteralConstant -> {
            val initConstant = anonConstant {
                string(initializer.data())
            }
            label(globalValue.name()) {
                quad(initConstant)
            }
        }
        is PrimitiveConstant -> label(globalValue.name()) {
            val cvt = PrimitiveConstant.from(globalValue.contentType().asType(), initializer)
            primitive(this, cvt)
        }
        else -> throw IllegalArgumentException("unsupported constant type: $initializer")
    }

    private fun convertGlobalValueToSymbolType(globalValue: GlobalValue) = when (val type = globalValue.contentType()) {
        is StructType -> {
            val constant = globalValue.initializer() as InitializerListValue
            makeAggregateConstant(globalValue.name(), globalValue.contentType().asType(), constant)
        }
        is ArrayType -> when (val constant = globalValue.initializer()) {
            is InitializerListValue  -> makeAggregateConstant(globalValue.name(), globalValue.contentType().asType(), constant)
            is StringLiteralConstant -> makeStringLiteralConstant(globalValue, type, constant)
            else -> throw IllegalArgumentException("unsupported constant type: $constant")
        }
        is FlagType -> makePrimitiveConstant(globalValue)
        is PrimitiveType -> makePrimitiveConstant(globalValue)
    }

    private fun primitive(builder: ObjBuilder, data: Constant) = when (data) {
        is I64Value -> builder.quad(data.i64)
        is U64Value -> builder.quad(data.u64)
        is I32Value -> builder.long(data.i32)
        is U32Value -> builder.long(data.u32)
        is I16Value -> builder.short(data.i16)
        is U16Value -> builder.short(data.u16)
        is I8Value  -> builder.byte(data.i8)
        is U8Value  -> builder.byte(data.u8)
        is F32Value -> builder.long(data.bits())
        is F64Value -> builder.quad(data.bits())
        is PointerLiteral -> {
            val gConstant = data.gConstant
            builder.quad(findLabel(gConstant.name()))
        }
        is NullValue -> builder.quad(0)
        is StringLiteralConstant -> builder.string(data.data())
        else -> throw IllegalArgumentException("unsupported constant type: $data")
    }

    private fun makePrimitiveConstant(globalValue: PrimitiveGlobalConstant): ObjLabel {
        return label(globalValue.name()) {
            primitive(this, globalValue.constant())
        }
    }
}