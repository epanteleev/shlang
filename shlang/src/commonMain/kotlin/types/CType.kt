package types


import ir.Definitions.POINTER_SIZE

sealed class CType: TypeProperty {
    abstract fun typename(): String
    abstract fun size(): Int
    override fun toString(): String = typename()
}

sealed class CPrimitive: CType() {
    fun interfere(type2: CType): CType {
        if (this == type2) return this
        when (this) {
            CHAR -> {
                return when (type2) {
                    INT -> INT
                    LONG -> LONG
                    ULONG -> ULONG
                    SHORT -> SHORT
                    UINT -> UINT
                    DOUBLE -> DOUBLE
                    FLOAT -> FLOAT
                    else -> throw TypeInferenceException("Can't interfere types '$this' and '$type2'")
                }
            }

            UCHAR -> {
                return when (type2) {
                    INT -> INT
                    LONG -> LONG
                    CHAR -> UCHAR
                    UINT -> UINT
                    DOUBLE -> DOUBLE
                    FLOAT -> FLOAT
                    else -> throw TypeInferenceException("Can't interfere types '$this' and '$type2'")
                }
            }
            SHORT -> {
                return when (type2) {
                    INT -> INT
                    LONG -> LONG
                    CHAR -> SHORT
                    UINT -> UINT
                    DOUBLE -> DOUBLE
                    FLOAT -> FLOAT
                    else -> throw TypeInferenceException("Can't interfere types '$this' and '$type2'")
                }
            }

            INT -> {
                return when (type2) {
                    CHAR -> INT
                    UCHAR -> INT
                    LONG -> LONG
                    ULONG -> ULONG
                    SHORT -> INT
                    USHORT -> INT
                    UINT -> UINT
                    DOUBLE -> DOUBLE
                    FLOAT -> FLOAT
                    else -> throw TypeInferenceException("Can't interfere types '$this' and '$type2'")
                }
            }

            LONG -> {
                return when (type2) {
                    CHAR -> LONG
                    INT -> LONG
                    SHORT -> LONG
                    UINT -> LONG
                    ULONG -> ULONG
                    DOUBLE -> DOUBLE
                    FLOAT -> FLOAT
                    else -> throw TypeInferenceException("Can't interfere types '$this' and '$type2'")
                }
            }

            FLOAT -> {
                return when (type2) {
                    CHAR -> FLOAT
                    INT -> FLOAT
                    SHORT -> FLOAT
                    UINT -> FLOAT
                    DOUBLE -> DOUBLE
                    LONG -> DOUBLE
                    else -> throw TypeInferenceException("Can't interfere types '$this' and '$type2'")
                }
            }

            DOUBLE -> {
                return when (type2) {
                    CHAR -> DOUBLE
                    INT -> DOUBLE
                    SHORT -> DOUBLE
                    UINT -> DOUBLE
                    FLOAT -> DOUBLE
                    LONG -> DOUBLE
                    else -> throw TypeInferenceException("Can't interfere types '$this' and '$type2'")
                }
            }

            USHORT -> {
                return when (type2) {
                    INT -> INT
                    LONG -> LONG
                    CHAR -> USHORT
                    UINT -> UINT
                    DOUBLE -> DOUBLE
                    FLOAT -> FLOAT
                    else -> throw TypeInferenceException("Can't interfere types '$this' and '$type2'")
                }
            }

            UINT -> {
                return when (type2) {
                    CHAR -> UINT
                    UCHAR -> UINT
                    LONG -> LONG
                    ULONG -> ULONG
                    SHORT -> UINT
                    USHORT -> UINT
                    INT -> UINT
                    DOUBLE -> DOUBLE
                    FLOAT -> FLOAT
                    else -> throw TypeInferenceException("Can't interfere types '$this' and '$type2'")
                }
            }

            ULONG -> {
                return when (type2) {
                    CHAR -> ULONG
                    UCHAR -> ULONG
                    INT -> ULONG
                    LONG -> ULONG
                    SHORT -> ULONG
                    USHORT -> ULONG
                    UINT -> ULONG
                    DOUBLE -> DOUBLE
                    FLOAT -> FLOAT
                    else -> throw TypeInferenceException("Can't interfere types '$this' and '$type2'")
                }
            }

            is AnyCPointer -> {
                when (type2) {
                    CHAR -> return this
                    INT -> return this
                    SHORT -> return this
                    UINT -> return this
                    FLOAT -> return this
                    LONG -> return this
                    is AnyCPointer -> {
                        if (dereference() == type2.dereference()) return this
                        if (dereference() == VOID) return this
                        if (type2.dereference() == VOID) return type2
                    }
                    ULONG -> return this
                    else -> throw TypeInferenceException("Can't interfere types '$this' and '$type2'")
                }
            }
            is CEnumType -> {
                return when (type2) {
                    CHAR -> INT
                    UCHAR -> INT
                    SHORT -> INT
                    USHORT -> INT
                    INT -> INT
                    UINT -> UINT
                    LONG -> LONG
                    ULONG -> ULONG
                    FLOAT -> FLOAT
                    DOUBLE -> DOUBLE
                    else -> throw TypeInferenceException("Can't interfere types '$this' and '$type2'")
                }
            }

            else -> throw RuntimeException("Unknown type $this, $type2")
        }
        throw TypeInferenceException("Can't interfere types '$this' and '$type2'")
    }
}

object VOID: CPrimitive() {
    override fun typename(): String = "void"
    override fun size(): Int = -1
}

object CHAR: CPrimitive() {
    override fun typename(): String = "char"
    override fun size(): Int = 1
}

object SHORT: CPrimitive() {
    override fun typename(): String = "short"
    override fun size(): Int = 2
}

object INT: CPrimitive() {
    override fun typename(): String = "int"
    override fun size(): Int = 4
}

object LONG: CPrimitive() {
    override fun typename(): String = "long"
    override fun size(): Int = 8
}

object FLOAT: CPrimitive() {
    override fun typename(): String = "float"
    override fun size(): Int = 4
}

object DOUBLE: CPrimitive() {
    override fun typename(): String = "double"
    override fun size(): Int = 8
}

object UCHAR: CPrimitive() {
    override fun typename(): String = "unsigned char"
    override fun size(): Int = 1
}

object USHORT: CPrimitive() {
    override fun typename(): String = "unsigned short"
    override fun size(): Int = 2
}

object UINT: CPrimitive() {
    override fun typename(): String = "unsigned int"
    override fun size(): Int = 4
}

object ULONG: CPrimitive() {
    override fun typename(): String = "unsigned long"
    override fun size(): Int = 8
}

object BOOL: CPrimitive() {
    override fun typename(): String = "_Bool"
    override fun size(): Int = 1
}

sealed class AnyCPointer: CPrimitive() {
    override fun size(): Int = POINTER_SIZE

    abstract fun qualifiers(): Set<TypeQualifier>
    abstract fun dereference(): CType
}

class SliceType(val elementType: CType, val dimension: Long): AnyCPointer() {
    override fun qualifiers(): Set<TypeQualifier> = setOf()
    override fun dereference(): CType = elementType
    override fun typename(): String = buildString {
        append(elementType)
        append("[$dimension]")
    }
}

class CPointer(val type: CType, private val properties: Set<TypeQualifier> = setOf()) : AnyCPointer() {
    override fun qualifiers(): Set<TypeQualifier> = properties

    override fun dereference(): CType = type

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CPointer) return false

        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        return type.hashCode()
    }

    override fun typename(): String = buildString {
        properties.forEach { append(it) }
        append(type)
        append("*")
    }
}

data class AbstractCFunction(val retType: TypeDesc, val argsTypes: List<TypeDesc>, var variadic: Boolean): CType() {
    override fun size(): Int = throw RuntimeException("Function type has no size")

    override fun typename(): String = buildString {
        append(retType)
        append("(")
        argsTypes.forEachIndexed { index, type ->
            append(type)
            if (index < argsTypes.size - 1) append(", ")
        }
        if (variadic) append(", ...")
        append(")")
    }
}

class CBaseFunctionType(val name: String, val functionType: AbstractCFunction): CType() {
    override fun size(): Int = throw RuntimeException("Function type has no size")

    fun retType(): TypeDesc = functionType.retType
    fun args(): List<TypeDesc> = functionType.argsTypes

    override fun typename(): String = buildString {
        append(functionType.retType)
        append(" $name(")
        functionType.argsTypes.forEachIndexed { index, type ->
            append(type)
            if (index < functionType.argsTypes.size - 1) append(", ")
        }
        if (functionType.variadic) append(", ...")
        append(")")
    }
}

class CFunPointerT(val functionType: AbstractCFunction, private val properties: Set<TypeQualifier>) : AnyCPointer() {
    override fun qualifiers(): Set<TypeQualifier> = properties

    override fun dereference(): CType = functionType.retType.baseType()
    override fun typename(): String = buildString {
        append(functionType.retType)
        append("(*)(")
        functionType.argsTypes.forEachIndexed { index, type ->
            append(type)
            if (index < functionType.argsTypes.size - 1) append(", ")
        }
        if (functionType.variadic) append(", ...")
        append(")")
    }
}

class TypeDef(val name: String, val baseType: TypeDesc): CType() {
    fun baseType(): TypeDesc = baseType
    override fun typename(): String = name
    override fun size(): Int = baseType.size()
    override fun toString(): String = baseType.toString()
}

sealed class AggregateBaseType: CType()

sealed class AnyStructType(open val name: String): AggregateBaseType() {
    protected val fields = arrayListOf<Pair<String, TypeDesc>>()
    override fun typename(): String = name

    fun fieldIndex(name: String): Int {
        return fields.indexOfFirst { it.first == name }
    }

    fun fieldIndex(index: Int): TypeDesc {
        return fields[index].second
    }

    fun fields(): List<Pair<String, TypeDesc>> {
        return fields
    }

    //TODO avoid???
    internal fun addField(name: String, type: TypeDesc) {
        fields.add(name to type)
    }
}

sealed interface UncompletedType

data class CStructType(override val name: String): AnyStructType(name) { //TODO
    override fun size(): Int {
        return fields.sumOf { it.second.size() }
    }

    override fun toString(): String = buildString {
        append("struct $name")
        append(" {")
        fields.forEach { (name, type) ->
            append("$type $name;")
        }
        append("}")
    }
}

data class CUnionType(override val name: String): AnyStructType(name) {
    override fun size(): Int {
        if (fields.isEmpty()) {
            return 0
        }
        return fields.maxOf { it.second.size() }
    }

    override fun toString(): String = buildString {
        append("union $name")
        append(" {")
        fields.forEach { (name, type) ->
            append("$type $name;")
        }
        append("}")
    }
}

data class CEnumType(val name: String, private val enumerators: Map<String, Int>): CPrimitive() {
    override fun typename(): String = name

    override fun size(): Int {
        return INT.size()
    }

    fun hasEnumerator(name: String): Boolean {
        return enumerators.contains(name)
    }

    fun enumerator(name: String): Int? {
        return enumerators[name] //TODO temporal
    }
}

sealed class AnyCArrayType(val type: TypeDesc): AggregateBaseType() {
    fun element(): TypeDesc = type
}

class CArrayType(type: TypeDesc, val dimension: Long) : AnyCArrayType(type) {
    override fun typename(): String {
        return toString()
    }

    override fun size(): Int {
        return type.size() * dimension.toInt() //TODO
    }

    override fun toString(): String = buildString {
        append("[$dimension]")
        append(type)
    }
}

data class CUncompletedArrayType(val elementType: TypeDesc) : AnyCArrayType(elementType){
    override fun typename(): String {
        return toString()
    }

    override fun size(): Int {
        return -1
    }

    override fun toString(): String = buildString {
        append("[]")
        append(elementType)
    }
}

data class CUncompletedStructType(override val name: String): UncompletedType, AnyStructType(name) {
    override fun typename(): String = name

    override fun size(): Int = throw Exception("Uncompleted type '$name'")

    override fun toString(): String {
        return "struct $name"
    }
}

data class CUncompletedUnionType(override val name: String): UncompletedType, AnyStructType(name) {
    override fun typename(): String = name

    override fun size(): Int = throw Exception("Uncompleted type")

    override fun toString(): String {
        return "union $name"
    }
}

data class CUncompletedEnumType(val name: String): UncompletedType, CPrimitive() {
    override fun typename(): String = "enum $name"

    override fun size(): Int = throw Exception("Uncompleted type")
}

class InitializerType(val initializer: List<CType>): CType() {
    override fun typename(): String = buildString {
        append("{")
        initializer.forEachIndexed { index, type ->
            append(type)
            if (index < initializer.size - 1) append(", ")
        }
        append("}")
    }

    override fun size(): Int = initializer.fold(0) { acc, type -> acc + type.size() }
}