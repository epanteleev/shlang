package gen

import ir.Constant
import ir.Value
import ir.module.builder.impl.FunctionDataBuilder
import ir.types.SignedIntType
import ir.types.Type
import ir.types.UnsignedIntType

object TypeConverter {

   fun FunctionDataBuilder.convertToType(value: Value, type: Type): Value {
        if (value.type() == type) {
            return value
        }
        if (value is Constant) {
            return convertConstant(value, type)
        }

        return when (type) {
            Type.I8 -> {
                type as SignedIntType
                when (value.type()) {
                    Type.I16 -> trunc(value, type)
                    Type.I32 -> trunc(value, type)
                    Type.I64 -> trunc(value, type)
                    Type.U8  -> trunc(value, type)
                    Type.U16 -> trunc(value, type)
                    Type.U32 -> trunc(value, type)
                    Type.U64 -> trunc(value, type)
                    Type.F32 -> fptosi(value, type)
                    Type.F64 -> fptosi(value, type)
                    else -> throw IRCodeGenError("Cannot convert $value to $type")
                }
            }

            Type.I16 -> {
                type as SignedIntType
                when (value.type()) {
                    Type.I8  -> sext(value, type)
                    Type.I32 -> trunc(value, type)
                    Type.I64 -> trunc(value, type)
                    Type.U8  -> sext(value, type)
                    Type.U16 -> bitcast(value, type)
                    Type.U32 -> trunc(value, type)
                    Type.U64 -> trunc(value, type)
                    Type.F32 -> fptosi(value, type)
                    Type.F64 -> fptosi(value, type)
                    else -> throw IRCodeGenError("Cannot convert $value to $type")
                }
            }

            Type.I32 -> {
                type as SignedIntType
                when (value.type()) {
                    Type.I8 -> sext(value, type)
                    Type.I16 -> sext(value, type)
                    Type.I64 -> trunc(value, type)
                    Type.U8  -> {
                        val bitcast = bitcast(value, Type.I8)
                        sext(bitcast, Type.I32)
                    }
                    Type.U16 -> {
                        val tmp = zext(value, Type.U32)
                        trunc(tmp, type)
                    }
                    Type.U32 -> bitcast(value, type)
                    Type.U64 -> trunc(value, type)
                    Type.F32 -> fptosi(value, type)
                    Type.F64 -> fptosi(value, type)
                    else -> throw IRCodeGenError("Cannot convert $value:${value.type()} to $type")
                }
            }

            Type.I64 -> {
                type as SignedIntType
                when (value.type()) {
                    Type.I8 -> sext(value, type)
                    Type.I16 -> sext(value, type)
                    Type.I32 -> sext(value, type)
                    Type.U8  -> {
                        val tmp = sext(value, Type.I64)
                        trunc(tmp, type)
                    }
                    Type.U16 -> {
                        val tmp = zext(value, Type.U64)
                        trunc(tmp, type)
                    }
                    Type.U32 -> {
                        val tmp = zext(value, Type.U64)
                        trunc(tmp, type)
                    }
                    Type.U64 -> bitcast(value, type)
                    Type.F32 -> fptosi(value, type)
                    Type.F64 -> fptosi(value, type)
                    else -> throw IRCodeGenError("Cannot convert $value to $type")
                }
            }

            Type.U8 -> {
                type as UnsignedIntType
                when (value.type()) {
                    Type.I8  -> bitcast(value, type)
                    Type.I16 -> trunc(value, type)
                    Type.I32 -> trunc(value, type)
                    Type.I64 -> trunc(value, type)
                    Type.U16 -> trunc(value, type)
                    Type.U32 -> trunc(value, type)
                    Type.U64 -> trunc(value, type)
                    Type.F32 -> {
                        val tmp = fptosi(value, Type.I32)
                        trunc(tmp, type)
                    }
                    Type.F64 -> {
                        val tmp = fptosi(value, Type.I64)
                        trunc(tmp, type)
                    }
                    else -> throw IRCodeGenError("Cannot convert $value to $type")
                }
            }

            Type.U16 -> {
                type as UnsignedIntType
                when (value.type()) {
                    Type.I8  -> trunc(value, type)
                    Type.I16 -> bitcast(value, type)
                    Type.I32 -> trunc(value, type)
                    Type.I64 -> trunc(value, type)
                    Type.U8  -> trunc(value, type)
                    Type.U32 -> trunc(value, type)
                    Type.U64 -> trunc(value, type)
                    Type.F32 -> {
                        val tmp = fptosi(value, Type.I32)
                        trunc(tmp, type)
                    }
                    Type.F64 -> {
                        val tmp = fptosi(value, Type.I64)
                        trunc(tmp, type)
                    }
                    else -> throw IRCodeGenError("Cannot convert $value to $type")
                }
            }

            Type.U32 -> {
                type as UnsignedIntType
                when (value.type()) {
                    Type.I8  -> trunc(value, type)
                    Type.I16 -> trunc(value, type)
                    Type.I32 -> bitcast(value, type)
                    Type.I64 -> trunc(value, type)
                    Type.U8  -> trunc(value, type)
                    Type.U16 -> trunc(value, type)
                    Type.U64 -> trunc(value, type)
                    Type.F32 -> {
                        val tmp = fptosi(value, Type.I32)
                        trunc(tmp, type)
                    }
                    Type.F64 -> {
                        val tmp = fptosi(value, Type.I64)
                        trunc(tmp, type)
                    }
                    else -> throw IRCodeGenError("Cannot convert $value to $type")
                }
            }
            else -> throw IRCodeGenError("Cannot convert $value to $type")
        }
    }

    private fun convertConstant(value: Constant, type: Type): Value {
        return Constant.from(type, value)
    }
}