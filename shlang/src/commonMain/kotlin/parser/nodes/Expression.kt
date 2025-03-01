package parser.nodes

import types.*
import typedesc.*
import tokenizer.tokens.*
import common.assertion
import parser.LineAgnosticAstPrinter
import parser.nodes.BinaryOpType.AND
import parser.nodes.BinaryOpType.OR
import parser.nodes.BinaryOpType.COMMA
import parser.nodes.visitors.*
import tokenizer.Position


sealed class Expression : Node() {
    protected var type: CType? = null

    abstract fun<T> accept(visitor: ExpressionVisitor<T>): T
    abstract fun resolveType(typeHolder: TypeHolder): CType

    protected fun convertToPrimitive(type: CType): CPrimitive? = when (type) {
        is CPrimitive    -> type
        is AnyCArrayType -> type.asPointer()
        is AnyCFunctionType -> type.asPointer()
        else -> null
    }

    protected fun convertToPointer(type: CType): CPointer? = when (type) {
        is CPointer      -> type
        is AnyCArrayType -> type.asPointer()
        else -> null
    }

    protected inline fun<reified T: CType> memoize(closure: () -> T): T {
        if (type != null) {
            return type as T
        }
        type = closure()
        return type as T
    }
}

// https://port70.net/~nsz/c/c11/n1570.html#6.5.2.5
// 6.5.2.5 Compound literals
class CompoundLiteral(val typeName: TypeName, val initializerList: InitializerList) : Expression() {
    override fun<T> accept(visitor: ExpressionVisitor<T>) = visitor.visit(this)
    override fun begin(): Position = typeName.begin()

    fun typeDesc(typeHolder: TypeHolder): TypeDesc {
        val type = typeName.specifyType(typeHolder, listOf()).typeDesc
        val ctype = type.cType()
        if (ctype is CUncompletedArrayType) {
            return TypeDesc.from(CArrayType(ctype.elementType, initializerList.length().toLong()))
        }

        return type
    }

    override fun resolveType(typeHolder: TypeHolder): CType = memoize {
        return@memoize typeDesc(typeHolder).cType()
    }
}

data class BinaryOp(val left: Expression, val right: Expression, val opType: BinaryOpType) : Expression() {
    override fun begin(): Position = left.begin()
    override fun<T> accept(visitor: ExpressionVisitor<T>) = visitor.visit(this)

    override fun resolveType(typeHolder: TypeHolder): CType = memoize {
        when (opType) {
            OR, AND -> return@memoize BOOL
            COMMA -> return@memoize right.resolveType(typeHolder)
            else -> {}
        }
        val l = left.resolveType(typeHolder)
        val r = right.resolveType(typeHolder)
        if (l == r) {
            return@memoize l
        }

        val leftType = convertToPrimitive(l)
            ?: throw TypeResolutionException("Binary operation on non-primitive type '$l': '${LineAgnosticAstPrinter.print(left)}'", begin())

        val rightType = convertToPrimitive(r)
            ?: throw TypeResolutionException("Binary operation on non-primitive type '$r': '${LineAgnosticAstPrinter.print(right)}'", begin())

        val resultType = leftType.interfere(typeHolder, rightType) ?:
            throw TypeResolutionException("Binary operation on incompatible types: $leftType and $rightType in ${left.begin()}'", begin())
        return@memoize resultType
    }
}

data object EmptyExpression : Expression() {
    override fun begin(): Position = Position.UNKNOWN
    override fun<T> accept(visitor: ExpressionVisitor<T>) = visitor.visit(this)

    override fun resolveType(typeHolder: TypeHolder): CType {
        throw IllegalStateException("Empty expression type is not resolved")
    }
}

class Conditional(val cond: Expression, val eTrue: Expression, val eFalse: Expression) : Expression() {
    override fun begin(): Position = cond.begin()
    override fun<T> accept(visitor: ExpressionVisitor<T>) = visitor.visit(this)

    override fun resolveType(typeHolder: TypeHolder): CType = memoize {
        val typeTrue  = eTrue.resolveType(typeHolder)
        val typeFalse = eFalse.resolveType(typeHolder)

        if (typeTrue is VOID || typeFalse is VOID) {
            return@memoize VOID
        }

        if (typeTrue is AnyCStructType && typeFalse is AnyCStructType && typeTrue == typeFalse) {
            return@memoize typeTrue
        }

        val cvtTypeTrue  = convertToPrimitive(typeTrue)
            ?: throw TypeResolutionException("Conditional with non-primitive types: $typeTrue and $typeFalse", begin())
        val cvtTypeFalse = convertToPrimitive(typeFalse)
            ?: throw TypeResolutionException("Conditional with non-primitive types: $typeTrue and $typeFalse", begin())

        if (cvtTypeTrue == cvtTypeFalse) {
            return@memoize cvtTypeTrue
        }

        return@memoize cvtTypeTrue.interfere(typeHolder, cvtTypeFalse) ?:
            throw TypeResolutionException("Conditional with incompatible types: $cvtTypeTrue and $cvtTypeFalse: '${LineAgnosticAstPrinter.print(this)}'", begin())
    }
}

class FunctionCall(val primary: Expression, val args: List<Expression>) : Expression() {
    override fun begin(): Position = primary.begin()
    override fun<T> accept(visitor: ExpressionVisitor<T>) = visitor.visit(this)

    private fun resolveParams(typeHolder: TypeHolder){
        val params = args.map { it.resolveType(typeHolder) }
        if (params.size != args.size) {
            throw TypeResolutionException("Function call of '${LineAgnosticAstPrinter.print(primary)}' with unresolved types", begin())
        }

        for (i in args.indices) {
            val argType = args[i].resolveType(typeHolder)
            if (argType == params[i]) {
                continue
            }
            throw TypeResolutionException("Function call of '${LineAgnosticAstPrinter.print(primary)}' with wrong argument types", begin())
        }
    }

    private fun resolveFunctionType0(typeHolder: TypeHolder): CPointer {
        val functionType = primary.resolveType(typeHolder)
        if (functionType is AbstractCFunction) {
            return CPointer(functionType, setOf())
        }
        if (functionType !is CPointer) {
            throw TypeResolutionException("Function call with non-function type: $functionType", begin())
        }
        return functionType
    }

    fun functionType(typeHolder: TypeHolder): AnyCFunctionType {
        resolveParams(typeHolder)
        val functionType = if (primary !is VarNode) {
            resolveFunctionType0(typeHolder)
        } else {
            typeHolder.getFunctionType(primary.name()).typeDesc.cType()
        }
        if (functionType is CPointer) {
            return functionType.dereference(typeHolder) as AbstractCFunction
        }
        if (functionType !is CFunctionType) {
            throw TypeResolutionException("Function call of '' with non-function type", begin())
        }

        return functionType
    }

    override fun resolveType(typeHolder: TypeHolder): CType {
        return functionType(typeHolder).retType().cType()
    }
}

sealed class Initializer : Expression()

class SingleInitializer(val expr: Expression) : Initializer() {
    override fun begin(): Position = expr.begin()
    override fun<T> accept(visitor: ExpressionVisitor<T>) = visitor.visit(this)

    override fun resolveType(typeHolder: TypeHolder): CType = memoize {
        return@memoize expr.resolveType(typeHolder)
    }
}

class DesignationInitializer(val designation: Designation, val initializer: Expression) : Initializer() {
    override fun begin(): Position = designation.begin()
    override fun<T> accept(visitor: ExpressionVisitor<T>) = visitor.visit(this)

    override fun resolveType(typeHolder: TypeHolder): CType = memoize {
        return@memoize initializer.resolveType(typeHolder)
    }
}

class InitializerList(private val begin: Position, val initializers: List<Initializer>) : Expression() {
    override fun begin(): Position = begin
    override fun<T> accept(visitor: ExpressionVisitor<T>) = visitor.visit(this)

    override fun resolveType(typeHolder: TypeHolder): CType = memoize {
        val types = initializers.map { it.resolveType(typeHolder) }

        val baseTypes = arrayListOf<CType>()
        for (i in initializers.indices) {
            baseTypes.add(types[i])
        }
        if (baseTypes.size == 1 && baseTypes[0] is CStringLiteral) {
            return@memoize baseTypes[0] //TODO is it needed?
        } else {
            return@memoize InitializerType(baseTypes)
        }
    }

    fun length(): Int = initializers.size
}

class MemberAccess(val primary: Expression, val fieldName: Identifier) : Expression() {
    override fun begin(): Position = primary.begin()
    override fun<T> accept(visitor: ExpressionVisitor<T>) = visitor.visit(this)

    fun memberName(): String = fieldName.str()

    override fun resolveType(typeHolder: TypeHolder): CType = memoize {
        val structType = primary.resolveType(typeHolder)
        if (structType !is AnyCStructType) {
            throw TypeResolutionException("Member access on non-struct type, but got $structType", begin())
        }

        val fieldDesc = structType.fieldByNameOrNull(memberName()) ?: throw TypeResolutionException("Field $fieldName not found in struct $structType", begin())
        return@memoize fieldDesc.cType()
    }
}

class ArrowMemberAccess(val primary: Expression, private val ident: Identifier) : Expression() {
    override fun begin(): Position = primary.begin()
    fun fieldName(): String = ident.str()

    override fun<T> accept(visitor: ExpressionVisitor<T>) = visitor.visit(this)

    override fun resolveType(typeHolder: TypeHolder): CType = memoize {
        val ty = primary.resolveType(typeHolder)
        val structType = convertToPointer(ty)
            ?: throw TypeResolutionException("Arrow member access on non-pointer type, but got $ty", begin())

        val baseType = structType.dereference(typeHolder)
        if (baseType !is AnyCStructType) {
            throw TypeResolutionException("Arrow member access on non-struct type, but got $baseType", begin())
        }

        val fieldDesc = baseType.fieldByNameOrNull(fieldName()) ?: throw TypeResolutionException("Field $ident not found in struct $baseType", begin())
        return@memoize fieldDesc.cType()
    }
}

data class VarNode(private val str: Identifier) : Expression() {
    override fun begin(): Position = str.position()
    fun name(): String = str.str()
    fun nameIdent(): Identifier = str

    fun position(): Position = str.position()

    override fun<T> accept(visitor: ExpressionVisitor<T>) = visitor.visit(this)

    override fun resolveType(typeHolder: TypeHolder): CType = memoize {
        val varType = typeHolder.getVarTypeOrNull(str.str())
        if (varType != null) {
            return@memoize varType.typeDesc.cType()
        }

        return@memoize typeHolder.findEnum(str.str()) ?: throw TypeResolutionException("Variable '$str' not found", begin())
    }
}

data class StringNode(val literals: List<StringLiteral>) : Expression() {
    init {
        assertion(literals.isNotEmpty()) { "Empty string node" }
    }

    private val data by lazy {
        if (literals.all { it.isEmpty() }) {
            ""
        } else {
            literals.joinToString("", postfix = "") { it.data() }
        }
    }

    override fun begin(): Position = literals.first().position()

    override fun<T> accept(visitor: ExpressionVisitor<T>) = visitor.visit(this)

    override fun resolveType(typeHolder: TypeHolder): CStringLiteral = memoize {
        if (data.isEmpty()) {
            return@memoize CStringLiteral(TypeDesc.from(CHAR), 1)
        }

        return@memoize CStringLiteral(TypeDesc.from(CHAR), length().toLong())
    }

    fun length(): Int = data.length + 1

    fun isNotEmpty(): Boolean = data.isNotEmpty()

    fun data(): String {
        val sb = StringBuilder()
        for (ch in data) {
            if (ch == '\"') {
                sb.append("\\\"")
            } else {
                sb.append(ch)
            }
        }

        return sb.toString()
    }
}

data class CharNode(val char: CharLiteral) : Expression() {
    override fun begin(): Position = char.position()
    override fun<T> accept(visitor: ExpressionVisitor<T>) = visitor.visit(this)

    override fun resolveType(typeHolder: TypeHolder): CType = memoize {
        return@memoize CHAR
    }

    fun toByte(): Byte {
        return char.code()
    }
}

data class NumNode(val number: PPNumber) : Expression() {
    override fun begin(): Position = number.position()
    override fun<T> accept(visitor: ExpressionVisitor<T>) = visitor.visit(this)

    override fun resolveType(typeHolder: TypeHolder): CType = memoize {
        when (number.toNumberOrNull()) {
            is Int, is Byte -> INT
            is Long   -> LONG
            is Float  -> FLOAT
            is Double -> DOUBLE
            else      -> throw TypeResolutionException("Unknown number type, but got ${number.str()}", begin())
        }
    }
}

data class UnaryOp(val primary: Expression, val opType: UnaryOpType) : Expression() {
    override fun begin(): Position = primary.begin()
    override fun<T> accept(visitor: ExpressionVisitor<T>) = visitor.visit(this)

    override fun resolveType(typeHolder: TypeHolder): CType = memoize {
        val primaryType = primary.resolveType(typeHolder)
        if (opType !is PrefixUnaryOpType) {
            return@memoize primaryType
        }

        return@memoize when (opType) {
            PrefixUnaryOpType.DEREF -> when (primaryType) {
                is CPointer              -> primaryType.dereference(typeHolder)
                is CArrayType            -> primaryType.type.cType()
                is CUncompletedArrayType -> primaryType.elementType.cType()
                else -> throw TypeResolutionException("Dereference on non-pointer type: $primaryType", begin())
            }
            PrefixUnaryOpType.ADDRESS -> CPointer(primaryType)
            PrefixUnaryOpType.NOT -> {
                if (primaryType is CPointer) {
                    LONG //TODO UNSIGNED???
                } else {
                    primaryType
                }
            }
            PrefixUnaryOpType.NEG -> {
                primaryType as? CPrimitive ?: throw TypeResolutionException("Negation on non-primitive type: $primaryType", begin())
            }
            PrefixUnaryOpType.INC,
            PrefixUnaryOpType.DEC,
            PrefixUnaryOpType.PLUS -> primaryType
            PrefixUnaryOpType.BIT_NOT -> {
                primaryType as? CPrimitive ?: throw TypeResolutionException("Bitwise not on non-primitive type: $primaryType", begin())
            }
        }
    }
}

data class ArrayAccess(val primary: Expression, val expr: Expression) : Expression() {
    override fun begin(): Position = primary.begin()
    override fun<T> accept(visitor: ExpressionVisitor<T>) = visitor.visit(this)

    override fun resolveType(typeHolder: TypeHolder): CType = memoize {
        return@memoize when (val primaryType = primary.resolveType(typeHolder)) {
            is CArrayType            -> primaryType.type.cType()
            is CStringLiteral        -> CHAR
            is CUncompletedArrayType -> primaryType.elementType.cType()
            is CPointer              -> primaryType.dereference(typeHolder)
            is CPrimitive -> {
                val e = expr.resolveType(typeHolder)
                val exprType = convertToPointer(e)
                    ?: throw TypeResolutionException("Array access with non-pointer type: $e", begin())
                primaryType.interfere(typeHolder, exprType) ?: throw TypeResolutionException("Array access with incompatible types: $primaryType and $exprType", begin())
            }
            else -> throw TypeResolutionException("Array access on non-array type: $primaryType", begin())
        }
    }
}

data class SizeOf(val expr: Node) : Expression() {
    override fun begin(): Position = expr.begin()
    override fun<T> accept(visitor: ExpressionVisitor<T>) = visitor.visit(this)

    override fun resolveType(typeHolder: TypeHolder): CType = memoize {
        return@memoize INT
    }

    fun constEval(typeHolder: TypeHolder): Int = when (expr) {
        is TypeName -> {
            val resolved = expr.specifyType(typeHolder, listOf()).typeDesc.cType()
            if (resolved !is CompletedType) {
                throw TypeResolutionException("sizeof on uncompleted type: $resolved", expr.begin())
            }
            resolved.size()
        }
        is Expression -> {
            val resolved = expr.resolveType(typeHolder)
            if (resolved !is CompletedType) {
                throw TypeResolutionException("sizeof on uncompleted type: $resolved", expr.begin())
            }
            resolved.size()
        }
        else -> throw TypeResolutionException("Unknown sizeOf expression, expr=${expr}", expr.begin())
    }
}

data class Cast(val typeName: TypeName, val cast: Expression) : Expression() {
    override fun begin(): Position = typeName.begin()
    override fun<T> accept(visitor: ExpressionVisitor<T>) = visitor.visit(this)

    override fun resolveType(typeHolder: TypeHolder): CType = memoize {
        return@memoize typeName.specifyType(typeHolder, listOf()).typeDesc.cType()
    }
}

data class IdentNode(private val str: Identifier) : Expression() {
    override fun begin(): Position = str.position()
    fun str(): String = str.str()
    override fun <T> accept(visitor: ExpressionVisitor<T>): T = visitor.visit(this)

    override fun resolveType(typeHolder: TypeHolder): CType {
        TODO("Not yet implemented")
    }
}

class BuiltinVaArg(val assign: Expression, val typeName: TypeName) : Expression() {
    override fun begin(): Position = assign.begin()
    override fun<T> accept(visitor: ExpressionVisitor<T>) = visitor.visit(this)

    override fun resolveType(typeHolder: TypeHolder): CType = memoize {
        return@memoize typeName.specifyType(typeHolder, listOf()).typeDesc.cType()
    }
}

class BuiltinVaStart(val vaList: Expression, val param: Node) : Expression() {
    override fun begin(): Position = vaList.begin()
    override fun<T> accept(visitor: ExpressionVisitor<T>) = visitor.visit(this)

    override fun resolveType(typeHolder: TypeHolder): CType = memoize {
        return@memoize VOID
    }
}

class BuiltinVaEnd(val vaList: Expression) : Expression() {
    override fun begin(): Position = vaList.begin()
    override fun<T> accept(visitor: ExpressionVisitor<T>) = visitor.visit(this)

    override fun resolveType(typeHolder: TypeHolder): CType = memoize {
        return@memoize VOID
    }
}

class BuiltinVaCopy(val dest: Expression, val src: Expression) : Expression() {
    override fun begin(): Position = dest.begin()
    override fun<T> accept(visitor: ExpressionVisitor<T>) = visitor.visit(this)

    override fun resolveType(typeHolder: TypeHolder): CType = memoize {
        return@memoize VOID
    }
}