package gen

import types.*
import ir.types.*
import ir.module.builder.impl.FunctionDataBuilder
import ir.module.builder.impl.ModuleBuilder
import ir.value.Constant
import ir.value.Value


object TypeConverter {
    inline fun<reified T: Type> ModuleBuilder.toIRType(typeHolder: TypeHolder, type: CType): T {
        val converted = toIRTypeUnchecked(typeHolder, type)
        if (converted !is T) {
            throw IRCodeGenError("Cannot convert '$type' to ${T::class}")
        }

        return converted
    }

    fun ModuleBuilder.toIRTypeUnchecked(typeHolder: TypeHolder, type: CType): Type {
        for (p in type.qualifiers()) {
            if (p is PointerQualifier) {
                return Type.Ptr
            }
        }
        if (type is CPointerType) {
            return Type.Ptr
        }

        if (type is CArrayType) {
            val elementType = toIRType<NonTrivialType>(typeHolder, type.element())
            return ArrayType(elementType, type.dimension().toInt())
        }
        
        val ret = when (type) {
            CType.CHAR   -> Type.I8
            CType.UCHAR  -> Type.U8
            CType.SHORT  -> Type.I16
            CType.USHORT -> Type.U16
            CType.INT    -> Type.I32
            CType.UINT   -> Type.U32
            CType.LONG   -> Type.I64
            CType.ULONG  -> Type.U64
            CType.FLOAT  -> Type.F32
            CType.DOUBLE -> Type.F64
            CType.VOID   -> Type.Void
            is CStructType -> {
                convertStructType(typeHolder, type)
            }
            is CUncompletedStructType -> {
                val structType = typeHolder.getTypedef(type.name()) as CStructType //TODO
                convertStructType(typeHolder, structType)
            }
            is CUncompletedUnionType -> {
                val unionType = typeHolder.getTypedef(type.name()) as CUnionType //TODO
                convertStructType(typeHolder, unionType)
            }
            is CUnionType -> {
                convertUnionType(typeHolder, type)
            }
            else -> throw IRCodeGenError("Unknown type, type=$type")
        }
        return ret
    }

    private fun ModuleBuilder.convertStructType(typeHolder: TypeHolder, type: CBaseStructType): Type {
        val fields = type.fields().map { toIRType<NonTrivialType>(typeHolder, it.second) }
        val structType = findStructTypeOrNull(type.name())
        if (structType != null) {
            return structType
        }

        return structType(type.name(), fields)
    }

    private fun ModuleBuilder.convertUnionType(typeHolder: TypeHolder, type: CUnionType): Type {
        val field = type.fields().maxByOrNull { it.second.size() }.let {
            if (it == null) {
                null
            } else {
                toIRType<NonTrivialType>(typeHolder, it.second)
            }
        }
        val structType = findStructTypeOrNull(type.name())
        if (structType != null) {
            return structType
        }

        return if (field == null) {
            structType(type.name(), listOf())
        } else {
            structType(type.name(), listOf(field))
        }
    }

   fun FunctionDataBuilder.convertToType(value: Value, toType: Type): Value {
        if (value.type() == toType) {
            return value
        }
        if (value is Constant) {
            return convertConstant(value, toType)
        }

        return when (toType) {
            Type.I8 -> {
                toType as SignedIntType
                when (value.type()) {
                    Type.U1  -> flag2int(value, toType)
                    Type.I16 -> trunc(value, toType)
                    Type.I32 -> trunc(value, toType)
                    Type.I64 -> trunc(value, toType)
                    Type.U8  -> trunc(value, toType)
                    Type.U16 -> trunc(value, toType)
                    Type.U32 -> trunc(value, toType)
                    Type.U64 -> trunc(value, toType)
                    Type.F32 -> fp2Int(value, toType)
                    Type.F64 -> fp2Int(value, toType)
                    Type.Ptr -> ptr2int(value, toType)
                    else -> throw IRCodeGenError("Cannot convert $value to $toType")
                }
            }

            Type.I16 -> {
                toType as SignedIntType
                when (value.type()) {
                    Type.U1 -> flag2int(value, toType)
                    Type.I8  -> sext(value, toType)
                    Type.I32 -> trunc(value, toType)
                    Type.I64 -> trunc(value, toType)
                    Type.U8  -> sext(value, toType)
                    Type.U16 -> bitcast(value, toType)
                    Type.U32 -> trunc(value, toType)
                    Type.U64 -> trunc(value, toType)
                    Type.F32 -> fp2Int(value, toType)
                    Type.F64 -> fp2Int(value, toType)
                    Type.Ptr -> ptr2int(value, toType)
                    else -> throw IRCodeGenError("Cannot convert $value to $toType")
                }
            }

            Type.I32 -> {
                toType as SignedIntType
                when (value.type()) {
                    Type.U1 -> flag2int(value, toType)
                    Type.I8 -> sext(value, toType)
                    Type.I16 -> sext(value, toType)
                    Type.I64 -> trunc(value, toType)
                    Type.U8  -> {
                        val bitcast = bitcast(value, Type.I8)
                        sext(bitcast, Type.I32)
                    }
                    Type.U16 -> {
                        val tmp = zext(value, Type.U32)
                        trunc(tmp, toType)
                    }
                    Type.U32 -> bitcast(value, toType)
                    Type.U64 -> trunc(value, toType)
                    Type.F32 -> fp2Int(value, toType)
                    Type.F64 -> fp2Int(value, toType)
                    Type.Ptr -> ptr2int(value, toType)
                    else -> throw IRCodeGenError("Cannot convert $value:${value.type()} to $toType")
                }
            }

            Type.I64 -> {
                toType as SignedIntType
                when (value.type()) {
                    Type.U1 -> flag2int(value, toType)
                    Type.I8 -> sext(value, toType)
                    Type.I16 -> sext(value, toType)
                    Type.I32 -> sext(value, toType)
                    Type.U8  -> {
                        val tmp = sext(value, Type.I64)
                        trunc(tmp, toType)
                    }
                    Type.U16 -> {
                        val tmp = zext(value, Type.U64)
                        trunc(tmp, toType)
                    }
                    Type.U32 -> {
                        val tmp = zext(value, Type.U64)
                        trunc(tmp, toType)
                    }
                    Type.U64 -> bitcast(value, toType)
                    Type.F32 -> fp2Int(value, toType)
                    Type.F64 -> fp2Int(value, toType)
                    Type.Ptr -> ptr2int(value, toType)
                    else -> throw IRCodeGenError("Cannot convert $value to $toType")
                }
            }

            Type.U8 -> {
                toType as UnsignedIntType
                when (value.type()) {
                    Type.U1 -> flag2int(value, toType)
                    Type.I8  -> bitcast(value, toType)
                    Type.I16 -> trunc(value, toType)
                    Type.I32 -> trunc(value, toType)
                    Type.I64 -> trunc(value, toType)
                    Type.U16 -> trunc(value, toType)
                    Type.U32 -> trunc(value, toType)
                    Type.U64 -> trunc(value, toType)
                    Type.F32 -> {
                        val tmp = fp2Int(value, Type.I32)
                        trunc(tmp, toType)
                    }
                    Type.F64 -> {
                        val tmp = fp2Int(value, Type.I64)
                        trunc(tmp, toType)
                    }
                    Type.Ptr -> ptr2int(value, toType)
                    else -> throw IRCodeGenError("Cannot convert $value to $toType")
                }
            }

            Type.U16 -> {
                toType as UnsignedIntType
                when (value.type()) {
                    Type.U1 -> flag2int(value, toType)
                    Type.I8  -> trunc(value, toType)
                    Type.I16 -> bitcast(value, toType)
                    Type.I32 -> trunc(value, toType)
                    Type.I64 -> trunc(value, toType)
                    Type.U8  -> trunc(value, toType)
                    Type.U32 -> trunc(value, toType)
                    Type.U64 -> trunc(value, toType)
                    Type.F32 -> {
                        val tmp = fp2Int(value, Type.I32)
                        trunc(tmp, toType)
                    }
                    Type.F64 -> {
                        val tmp = fp2Int(value, Type.I64)
                        trunc(tmp, toType)
                    }
                    Type.Ptr -> ptr2int(value, toType)
                    else -> throw IRCodeGenError("Cannot convert $value to $toType")
                }
            }

            Type.U32 -> {
                toType as UnsignedIntType
                when (value.type()) {
                    Type.U1  -> flag2int(value, toType)
                    Type.I8  -> trunc(value, toType)
                    Type.I16 -> trunc(value, toType)
                    Type.I32 -> bitcast(value, toType)
                    Type.I64 -> trunc(value, toType)
                    Type.U8  -> trunc(value, toType)
                    Type.U16 -> trunc(value, toType)
                    Type.U64 -> trunc(value, toType)
                    Type.F32 -> {
                        val tmp = fp2Int(value, Type.I32)
                        trunc(tmp, toType)
                    }
                    Type.F64 -> {
                        val tmp = fp2Int(value, Type.I64)
                        trunc(tmp, toType)
                    }
                    Type.Ptr -> ptr2int(value, toType)
                    else -> throw IRCodeGenError("Cannot convert $value to $toType")
                }
            }
            Type.U64 -> {
                toType as UnsignedIntType
                when (value.type()) {
                    Type.U1  -> flag2int(value, toType)
                    Type.I8  -> trunc(value, toType)
                    Type.I16 -> trunc(value, toType)
                    Type.I32 -> {
                        val tmp = sext(value, Type.I64)
                        bitcast(tmp, toType)
                    }
                    Type.I64 -> bitcast(value, toType)
                    Type.U8  -> trunc(value, toType)
                    Type.U16 -> trunc(value, toType)
                    Type.U32 -> trunc(value, toType)
                    Type.F32 -> {
                        val tmp = fp2Int(value, Type.I32)
                        trunc(tmp, toType)
                    }
                    Type.F64 -> {
                        val tmp = fp2Int(value, Type.I64)
                        trunc(tmp, toType)
                    }
                    Type.Ptr -> ptr2int(value, toType)
                    else -> throw IRCodeGenError("Cannot convert $value to $toType")
                }
            }
            Type.F32 -> {
                toType as FloatingPointType
                when (value.type()) {
                    Type.U1  -> int2fp(value, toType)
                    Type.I8  -> int2fp(value, toType)
                    Type.I16 -> int2fp(value, toType)
                    Type.I32 -> int2fp(value, toType)
                    Type.I64 -> int2fp(value, toType)
                    Type.U8  -> int2fp(value, toType)
                    Type.U16 -> int2fp(value, toType)
                    Type.U32 -> int2fp(value, toType)
                    Type.U64 -> int2fp(value, toType)
                    Type.F64 -> fptrunc(value, toType)
                    else -> throw IRCodeGenError("Cannot convert $value to $toType")
                }
            }
            Type.F64 -> {
                toType as FloatingPointType
                when (value.type()) {
                    Type.U1  -> int2fp(value, toType)
                    Type.I8  -> int2fp(value, toType)
                    Type.I16 -> int2fp(value, toType)
                    Type.I32 -> int2fp(value, toType)
                    Type.I64 -> int2fp(value, toType)
                    Type.U8  -> int2fp(value, toType)
                    Type.U16 -> int2fp(value, toType)
                    Type.U32 -> int2fp(value, toType)
                    Type.U64 -> int2fp(value, toType)
                    Type.F32 -> fpext(value, toType)
                    else -> throw IRCodeGenError("Cannot convert $value to $toType")
                }
            }
            Type.Ptr -> {
                toType as PointerType
                val valueType = value.type()
                if (valueType is IntegerType) {
                    return int2ptr(value)
                } else {
                    throw IRCodeGenError("Cannot convert $value to $toType")
                }
            }
            else -> throw IRCodeGenError("Cannot convert $value:${value.type()} to $toType")
        }
    }

    private fun convertConstant(value: Constant, type: Type): Value {
        return Constant.from(type, value)
    }
}