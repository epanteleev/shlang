package parser.nodes

import common.assertion
import types.*
import gen.consteval.*
import tokenizer.Identifier
import parser.nodes.visitors.DirectDeclaratorParamVisitor


abstract class DirectDeclaratorParam: Node() {
    abstract fun resolveType(baseType: CType, storageClass: StorageClass?, typeHolder: TypeHolder): CType
    abstract fun<T> accept(visitor: DirectDeclaratorParamVisitor<T>): T
}

data class ArrayDeclarator(val constexpr: Expression) : DirectDeclaratorParam() {
    override fun<T> accept(visitor: DirectDeclaratorParamVisitor<T>) = visitor.visit(this)

    override fun resolveType(baseType: CType, storageClass: StorageClass?, typeHolder: TypeHolder): CType {
        if (constexpr is EmptyExpression) {
            return if (storageClass == null) {
                UncompletedArrayType(baseType)
            } else {
                UncompletedArrayType(baseType, arrayListOf(storageClass))
            }
        }

        val ctx = CommonConstEvalContext<Long>(typeHolder)
        val size = ConstEvalExpression.eval<Long>(constexpr, ConstEvalExpressionLong(ctx))
        return if (storageClass == null) {
            CArrayType(CArrayBaseType(baseType, size))
        } else {
            CArrayType(CArrayBaseType(baseType, size), arrayListOf(storageClass))
        }
    }
}

data class IndentifierList(val list: List<IdentNode>): DirectDeclaratorParam() {
    override fun <T> accept(visitor: DirectDeclaratorParamVisitor<T>): T = visitor.visit(this)

    override fun resolveType(baseType: CType, storageClass: StorageClass?, typeHolder: TypeHolder): CType {
        TODO("Not yet implemented")
    }
}

data class ParameterTypeList(val params: List<AnyParameter>): DirectDeclaratorParam() {
    override fun<T> accept(visitor: DirectDeclaratorParamVisitor<T>) = visitor.visit(this)

    override fun resolveType(baseType: CType, storageClass: StorageClass?, typeHolder: TypeHolder): AbstractCFunctionType {
        assertion(storageClass == null) { "Unimpl" }
        val params = resolveParams(typeHolder)
        return AbstractCFunctionType(baseType, params, isVarArg())
    }

    fun params(): List<String> {
        if (params.isEmpty()) {
            return emptyList()
        }
        if (params.size == 1 && params[0] is Parameter) {
            val param = params[0] as Parameter
            if (param.declarator is EmptyDeclarator) {
                return emptyList()
            }
        }
        return params.map {
            when (it) {
                is Parameter -> it.name()
                is ParameterVarArg -> "..."
                else -> throw IllegalStateException("Unknown parameter $it")
            }
        }
    }

    private fun isVarArg(): Boolean {
        return params.any { it is ParameterVarArg }
    }

    private fun resolveParams(typeHolder: TypeHolder): List<CType> {
        val paramTypes = mutableListOf<CType>()
        if (params.size == 1) {
            val type = params[0].resolveType(typeHolder)
            // Special case for void
            // Pattern: 'void f(void)' can be found in the C program.
            return if (type == CType.CVOID) {
                emptyList()
            } else {
                listOf(type)
            }
        }
        for (param in params) {
            when (param) {
                is Parameter -> {
                    val type = param.resolveType(typeHolder)
                    paramTypes.add(type)
                }
                is ParameterVarArg -> {}
                else -> throw IllegalStateException("Unknown parameter $param")
            }
        }

        return paramTypes
    }
}

// Special case for first parameter of DirectDeclarator
sealed class DirectDeclaratorFirstParam : DirectDeclaratorParam() {
    abstract fun name(): String
}

data class FunctionPointerDeclarator(val declarator: Declarator): DirectDeclaratorFirstParam() {
    override fun<T> accept(visitor: DirectDeclaratorParamVisitor<T>) = visitor.visit(this)

    override fun name(): String = declarator.name()

    fun declarator(): Declarator = declarator

    override fun resolveType(baseType: CType, storageClass: StorageClass?, typeHolder: TypeHolder): CType {
        TODO("Not yet implemented")
    }
}

data class DirectVarDeclarator(val ident: Identifier): DirectDeclaratorFirstParam() {
    override fun<T> accept(visitor: DirectDeclaratorParamVisitor<T>) = visitor.visit(this)
    override fun name(): String = ident.str()

    override fun resolveType(baseType: CType, storageClass: StorageClass?, typeHolder: TypeHolder): CType {
        TODO("Not yet implemented")
    }
}